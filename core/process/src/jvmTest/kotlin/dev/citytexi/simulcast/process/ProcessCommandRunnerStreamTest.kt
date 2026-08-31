package dev.citytexi.simulcast.process

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessCommandRunnerStreamTest {

    private val runner = ProcessCommandRunner()

    @Test
    fun emits_lines_then_exit_code() = runTest {
        val script = "echo one; echo two; echo oops 1>&2; exit 3"
        val events = runner.stream(Command("/bin/sh", listOf("-c", script))).toList()

        assertEquals(
            listOf("one", "two"),
            events.filterIsInstance<CommandEvent.Stdout>().map { it.line },
        )
        assertEquals(
            listOf("oops"),
            events.filterIsInstance<CommandEvent.Stderr>().map { it.line },
        )
        assertEquals(CommandEvent.Exited(3), events.last())
    }

    @Test
    fun emits_start_failure_instead_of_throwing() = runTest {
        val events = runner.stream(Command("/nonexistent/tool")).toList()

        assertTrue(events.size == 1)
        assertIs<CommandEvent.StartFailed>(events.single())
    }
}
