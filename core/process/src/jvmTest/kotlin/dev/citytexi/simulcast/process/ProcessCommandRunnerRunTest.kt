package dev.citytexi.simulcast.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
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

    @Test
    fun cancelling_run_reaps_the_child_instead_of_waiting_out_the_timeout() = runBlocking {
        val pidFile = File.createTempFile("run-cancel-pid", ".txt")
        // 이 잡을 runBlocking 의 자식으로 두면(예: 그냥 async { }), cancelAndJoin 을 우리가
        // 포기해도 runBlocking 자체는 여전히 구조적으로 이 잡이 끝나길 기다린다 — 깨진 구현에서는
        // 그게 자식 프로세스가 자연 종료할 때까지(스크립트의 sleep 길이만큼)이므로, 테스트에
        // 명시한 타임아웃이 사실상 안 지켜진다. 독립된 스코프에 띄워야 진짜로 손을 뗄 수 있다.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val script = "echo \$\$ > '${pidFile.absolutePath}'; sleep 30"
            val deferred = scope.async {
                runner.run(Command("/bin/sh", listOf("-c", script)), 60.seconds)
            }

            val pid = withTimeout(10.seconds) {
                var text = pidFile.readText()
                while (text.isBlank()) {
                    delay(20)
                    text = pidFile.readText()
                }
                text.trim().toLong()
            }

            // 5초는 회수라면 넉넉하고, 60초 타임아웃이 우연히 터진 걸로는 절대 설명 안 되는 길이다.
            val cancelled = withTimeoutOrNull(5.seconds) { deferred.cancelAndJoin() } != null
            val alive = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

            assertTrue(
                cancelled && !alive,
                "취소 후 회수되지 않았다: pid=$pid, cancelAndJoin 5초 안에 완료=$cancelled, 그 뒤에도 alive=$alive",
            )
        } finally {
            scope.cancel()
            pidFile.delete()
        }
    }
}
