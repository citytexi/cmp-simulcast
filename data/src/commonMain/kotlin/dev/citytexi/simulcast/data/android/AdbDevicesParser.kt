package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.domain.DeviceState

data class AdbEntry(val serial: String, val state: DeviceState)

/**
 * `adb devices -l` 은 첫 줄이 헤더이고 그 뒤가 `<serial> <state> [key:value ...]` 다.
 * 목록에 없는 AVD 는 부팅되지 않은 것이라 여기서는 알 수 없다 — 그 축은 emulator 쪽이 채운다.
 */
fun parseAdbDevices(output: String): List<AdbEntry> =
    output.lineSequence()
        .drop(1)
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val columns = line.split(Regex("\\s+"))
            if (columns.size < 2) return@mapNotNull null
            AdbEntry(columns[0], columns[1].toDeviceState())
        }
        .toList()

private fun String.toDeviceState(): DeviceState = when (this) {
    "device" -> DeviceState.RUNNING
    "offline" -> DeviceState.STARTING
    else -> DeviceState.UNAVAILABLE
}
