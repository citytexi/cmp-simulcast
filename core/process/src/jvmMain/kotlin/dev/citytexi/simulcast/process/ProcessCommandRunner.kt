package dev.citytexi.simulcast.process

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
            val exitCode = process.waitFor()
            CommandResult.Completed(exitCode, stdout.await(), stderr.await())
        }
    }

    override fun stream(command: Command, capacity: Int): Flow<CommandEvent> = flow { }
}

internal fun Command.toProcessBuilder(): ProcessBuilder =
    ProcessBuilder(listOf(executable) + args).apply {
        workingDir?.let { directory(File(it)) }
        environment().putAll(env)
    }
