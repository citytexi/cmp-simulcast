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
        waitFor()
    }
    descendants.forEach { it.destroyForcibly() }
}
