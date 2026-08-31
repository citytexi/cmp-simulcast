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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
            val stdout = async { process.inputStream.bufferedReader().readText() }
            val stderr = async { process.errorStream.bufferedReader().readText() }

            val exited = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.reapTree()
                return@coroutineScope CommandResult.TimedOut(stdout.await(), stderr.await())
            }
            CommandResult.Completed(process.exitValue(), stdout.await(), stderr.await())
        }
    }

    override fun stream(command: Command, capacity: Int): Flow<CommandEvent> = callbackFlow {
        val process = try {
            command.toProcessBuilder().start()
        } catch (e: IOException) {
            send(CommandEvent.StartFailed(e.message ?: e.toString()))
            close()
            return@callbackFlow
        }

        val readers = listOf(
            launch(io) {
                process.inputStream.bufferedReader().forEachLine { trySend(CommandEvent.Stdout(it)) }
            },
            launch(io) {
                process.errorStream.bufferedReader().forEachLine { trySend(CommandEvent.Stderr(it)) }
            },
        )

        launch(io) {
            val exitCode = process.waitFor()
            readers.joinAll()
            trySend(CommandEvent.Exited(exitCode))
            close()
        }

        awaitClose { process.reapTree() }
    }.buffer(capacity, BufferOverflow.SUSPEND)
}

internal fun Command.toProcessBuilder(): ProcessBuilder =
    ProcessBuilder(listOf(executable) + args).apply {
        workingDir?.let { directory(File(it)) }
        environment().putAll(env)
    }
