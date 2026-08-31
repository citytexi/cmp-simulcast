package dev.citytexi.simulcast.process

/**
 * @param send 채널이 받아들였으면 true. 실패한 이벤트는 버려진 것으로 센다.
 */
internal class DropCountingSink(private val send: (CommandEvent) -> Boolean) {

    private var dropped = 0

    fun offer(event: CommandEvent) {
        if (dropped > 0 && send(CommandEvent.Dropped(dropped))) {
            dropped = 0
        }
        if (!send(event)) {
            dropped++
        }
    }
}
