package dev.citytexi.simulcast.process

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class ProcessCommandRunnerRunTest {

    private val runner = ProcessCommandRunner()

    @Test
    fun captures_stdout_and_exit_code() = runTest {
        val result = runner.run(Command("/bin/echo", listOf("hello")), 5.seconds)

        val completed = assertIs<CommandResult.Completed>(result)
        assertEquals(0, completed.exitCode)
        assertEquals("hello", completed.stdout.trim())
    }

    @Test
    fun reports_start_failure_without_throwing() = runTest {
        val result = runner.run(Command("/nonexistent/tool"), 5.seconds)

        assertIs<CommandResult.StartFailed>(result)
    }
}
