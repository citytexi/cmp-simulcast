package dev.citytexi.simulcast.process

import java.util.concurrent.TimeUnit

/**
 * `destroy()` 는 직계 자식에게만 신호를 보낸다. `sh -c 'x & wait'` 류가 만드는 손자는
 * 그대로 남으므로 자손 목록을 먼저 붙잡아 역순으로 죽인다 — 루트를 먼저 죽이면 자손이
 * 재부모화되어 목록에서 사라진다.
 */
internal fun Process.reapTree(graceMillis: Long = 500) {
    val descendants = descendants().toList()
    descendants.asReversed().forEach { it.destroy() }
    destroy()
    if (!waitFor(graceMillis, TimeUnit.MILLISECONDS)) {
        descendants.asReversed().forEach { it.destroyForcibly() }
        destroyForcibly()
        // SIGKILL 조차 통하지 않는 상태(D-state, 예: 끊긴 네트워크 마운트나 장치 I/O에 걸린 경우)가
        // 있을 수 있다. 이 대기가 무한정이면 회수 자체가 상위 타임아웃과 같은 방식으로 매달린다.
        waitFor(graceMillis, TimeUnit.MILLISECONDS)
    }
    descendants.forEach { it.destroyForcibly() }
}
