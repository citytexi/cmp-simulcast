package dev.citytexi.simulcast.process

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class ProcessCommandRunnerReapTest {

    @Test
    fun kills_grandchildren_when_collection_is_cancelled() = runBlocking {
        val runner = ProcessCommandRunner()
        val script = "sleep 120 & echo \$!; wait"

        val grandchildPid = withTimeout(10.seconds) {
            runner.stream(Command("/bin/sh", listOf("-c", script)))
                .filterIsInstance<CommandEvent.Stdout>()
                .first()
                .line
                .trim()
                .toLong()
        }

        // first() 가 수집을 끊었으므로 awaitClose 의 회수가 이미 돌았다.
        Thread.sleep(1_000)

        val alive = ProcessHandle.of(grandchildPid).map { it.isAlive }.orElse(false)
        assertFalse(alive, "손자 프로세스 $grandchildPid 가 살아남았다")
    }
}
