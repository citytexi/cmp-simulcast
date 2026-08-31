package dev.citytexi.simulcast.process

import java.util.concurrent.atomic.AtomicInteger

/**
 * @param send 채널이 받아들였으면 true. 실패한 이벤트는 버려진 것으로 센다.
 */
internal class DropCountingSink(private val send: (CommandEvent) -> Boolean) {

    // stdout/stderr 두 리더 코루틴이 서로 다른 스레드에서 동시에 offer를 호출한다.
    private val dropped = AtomicInteger(0)

    fun offer(event: CommandEvent) {
        val pending = pendingCount()
        if (pending > 0 && !send(CommandEvent.Dropped(pending))) {
            dropped.addAndGet(pending)
        }
        if (!send(event)) {
            dropped.incrementAndGet()
        }
    }

    /** 누적된 드롭 수를 원자적으로 비우면서 돌려준다. `offer`의 클레임과 같은 연산이다. */
    internal fun pendingCount(): Int = dropped.getAndSet(0)
}
