package dev.citytexi.simulcast.process

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProcessCommandRunnerPipeTest {

    private val runner = ProcessCommandRunner()

    @Test
    fun drains_stderr_larger_than_the_os_pipe_buffer() = runTest {
        val script = "i=0; while [ \$i -lt 20000 ]; do echo 'stderr line' 1>&2; i=\$((i+1)); done; echo done"
        val result = runner.run(Command("/bin/sh", listOf("-c", script)), 30.seconds)

        val completed = assertIs<CommandResult.Completed>(result)
        assertEquals(0, completed.exitCode)
        assertEquals("done", completed.stdout.trim())
        assertTrue(completed.stderr.length > 100_000, "stderr 가 잘렸다: ${completed.stderr.length}")
    }
}
