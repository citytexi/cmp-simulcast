package dev.citytexi.simulcast.process

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

sealed interface CommandResult {
    data class Completed(val exitCode: Int, val stdout: String, val stderr: String) : CommandResult

    /**
     * 부분 출력은 자식 파이프가 EOF에 닿아야 채워진다. 스냅샷 시점 이전에 데몬화해 자손 목록을
     * 벗어난 프로세스가 그 파이프를 물려받고 있었다면, 회수 후에도 출력 수집이 계속 블록될 수 있다.
     */
    data class TimedOut(val partialStdout: String, val partialStderr: String) : CommandResult
    data class StartFailed(val reason: String) : CommandResult
}

sealed interface CommandEvent {
    data class Stdout(val line: String) : CommandEvent
    data class Stderr(val line: String) : CommandEvent
    data class Dropped(val count: Int) : CommandEvent
    data class Exited(val exitCode: Int) : CommandEvent
    data class StartFailed(val reason: String) : CommandEvent
}

interface CommandRunner {
    /** 실패를 던지지 않는다. 기동 실패와 타임아웃도 [CommandResult]로 돌아온다. */
    suspend fun run(command: Command, timeout: Duration): CommandResult

    /** 수집이 취소되면 프로세스와 그 자손을 회수한다. */
    fun stream(command: Command, capacity: Int = DEFAULT_STREAM_CAPACITY): Flow<CommandEvent>

    companion object {
        const val DEFAULT_STREAM_CAPACITY: Int = 4096
    }
}
