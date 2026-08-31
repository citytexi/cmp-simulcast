package dev.citytexi.simulcast.process

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ProcessCommandRunner(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CommandRunner {

    override suspend fun run(command: Command, timeout: Duration): CommandResult = withContext(io) {
        val process = try {
            command.toProcessBuilder().start()
        } catch (e: IOException) {
            return@withContext CommandResult.StartFailed(e.message ?: e.toString())
        }
        coroutineScope {
            val stdout = async { readAllCatching(process.inputStream) }
            val stderr = async { readAllCatching(process.errorStream) }
            try {
                val exited = runInterruptible(io) {
                    process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                }
                if (!exited) {
                    process.reapTree()
                    return@coroutineScope CommandResult.TimedOut(stdout.await(), stderr.await())
                }
                CommandResult.Completed(process.exitValue(), stdout.await(), stderr.await())
            } finally {
                // coroutineScope 는 stdout/stderr 자식이 끝나야 리턴한다. 취소로 이 지점이
                // 풀리면 그 자식들은 여전히 블로킹 read 에 걸려 있으므로, join 을 기다리기 전에
                // 여기서 회수해 파이프에 EOF 를 만들어야 자식들이 풀려난다.
                if (process.isAlive) process.reapTree()
            }
        }
    }

    override fun stream(command: Command, capacity: Int): Flow<CommandEvent> = callbackFlow {
        val sink = DropCountingSink { trySend(it).isSuccess }

        val process = try {
            command.toProcessBuilder().start()
        } catch (e: IOException) {
            send(CommandEvent.StartFailed(e.message ?: e.toString()))
            close()
            return@callbackFlow
        }

        val readers = listOf(
            launch(io) {
                try {
                    process.inputStream.bufferedReader().forEachLine { sink.offer(CommandEvent.Stdout(it)) }
                } catch (e: IOException) {
                }
            },
            launch(io) {
                try {
                    process.errorStream.bufferedReader().forEachLine { sink.offer(CommandEvent.Stderr(it)) }
                } catch (e: IOException) {
                }
            },
        )

        launch(io) {
            val exitCode = process.waitFor()
            readers.joinAll()
            val pending = sink.pendingCount()
            if (pending > 0) send(CommandEvent.Dropped(pending))
            send(CommandEvent.Exited(exitCode))
            close()
        }

        awaitClose { process.reapTree() }
    }.buffer(capacity, BufferOverflow.SUSPEND).flowOn(io)
}

/** [IOException]로 중단된 경우 그때까지 읽은 내용을 그대로 돌려준다 — 실패도 값으로 다루는 계약의 일부. */
private fun readAllCatching(input: InputStream): String {
    val builder = StringBuilder()
    val reader = input.bufferedReader()
    val buf = CharArray(8192)
    try {
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            builder.append(buf, 0, n)
        }
    } catch (e: IOException) {
    }
    return builder.toString()
}

internal fun Command.toProcessBuilder(): ProcessBuilder =
    ProcessBuilder(listOf(executable) + args).apply {
        workingDir?.let { directory(File(it)) }
        environment().putAll(env)
    }
