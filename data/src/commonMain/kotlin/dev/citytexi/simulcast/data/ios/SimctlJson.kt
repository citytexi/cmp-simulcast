package dev.citytexi.simulcast.data.ios

import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SimctlList(val devices: Map<String, List<SimctlDevice>> = emptyMap())

@Serializable
private data class SimctlDevice(
    val udid: String,
    val name: String,
    val state: String,
    val isAvailable: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

/** 런타임 키가 iOS 인 것, 그리고 실제로 띄울 수 있는 것만 남긴다. */
fun parseSimctlDevices(raw: String): List<Device> =
    json.decodeFromString<SimctlList>(raw)
        .devices
        .filterKeys { it.contains("SimRuntime.iOS") }
        .values
        .flatten()
        .filter { it.isAvailable }
        .map { Device(it.udid, it.name, DevicePlatform.IOS, it.state.toDeviceState()) }

private fun String.toDeviceState(): DeviceState = when (this) {
    "Booted" -> DeviceState.RUNNING
    "Booting", "Shutting Down" -> DeviceState.STARTING
    "Shutdown" -> DeviceState.STOPPED
    else -> DeviceState.UNAVAILABLE
}
