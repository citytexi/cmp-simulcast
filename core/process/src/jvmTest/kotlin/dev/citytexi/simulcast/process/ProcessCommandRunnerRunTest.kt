package dev.citytexi.simulcast.process

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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

    @Test
    fun times_out_and_kills_the_process() = runTest {
        val started = System.currentTimeMillis()
        val result = runner.run(Command("/bin/sleep", listOf("30")), 300.milliseconds)
        val elapsed = System.currentTimeMillis() - started

        assertIs<CommandResult.TimedOut>(result)
        assertTrue(elapsed < 10_000, "타임아웃이 걸리지 않고 매달렸다: ${elapsed}ms")
    }

    @Test
    fun passes_env_to_the_child_process() = runTest {
        val result = runner.run(
            Command("/bin/sh", listOf("-c", "echo \$FOO"), env = mapOf("FOO" to "bar")),
            5.seconds,
        )

        val completed = assertIs<CommandResult.Completed>(result)
        assertEquals("bar", completed.stdout.trim())
    }
}
