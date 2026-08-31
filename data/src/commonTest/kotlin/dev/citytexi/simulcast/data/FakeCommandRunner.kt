package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.process.Command
import dev.citytexi.simulcast.process.CommandEvent
import dev.citytexi.simulcast.process.CommandResult
import dev.citytexi.simulcast.process.CommandRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

/**
 * @param responses 인자 리스트 전체를 키로 한다. 명령 이름은 탐색 결과에 따라 달라지므로 키에 넣지 않는다.
 */
class FakeCommandRunner(
    private val responses: Map<List<String>, CommandResult>,
) : CommandRunner {

    val invoked = mutableListOf<Command>()

    override suspend fun run(command: Command, timeout: Duration): CommandResult {
        invoked += command
        return responses[command.args] ?: CommandResult.StartFailed("unstubbed: ${command.args}")
    }

    override fun stream(command: Command, capacity: Int): Flow<CommandEvent> = emptyFlow()
}
