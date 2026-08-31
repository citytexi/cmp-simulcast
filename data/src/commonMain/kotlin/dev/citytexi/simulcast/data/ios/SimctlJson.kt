package dev.citytexi.simulcast.data.ios

import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
private data class SimctlDevice(
    val udid: String,
    val name: String,
    val state: String,
    val isAvailable: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * 런타임 키가 iOS 인 것, 그리고 실제로 띄울 수 있는 것만 남긴다.
 * 항목 단위로 디코드해서, 스키마에 안 맞는 한 줄이 나머지 줄까지 끌고 내려가지 않게 한다.
 */
fun parseSimctlDevices(raw: String): List<Device> {
    val devices = json.parseToJsonElement(raw).jsonObject["devices"]?.jsonObject
        ?: error("simctl output has no \"devices\" object")

    return devices
        .filterKeys { it.contains("SimRuntime.iOS") }
        .values
        .flatMap { it.jsonArray }
        .mapNotNull(::decodeDeviceOrNull)
        .filter { it.isAvailable }
        .map { Device(it.udid, it.name, DevicePlatform.IOS, it.state.toDeviceState()) }
}

private fun decodeDeviceOrNull(element: JsonElement): SimctlDevice? =
    try {
        json.decodeFromJsonElement(element)
    } catch (e: SerializationException) {
        null
    }

private fun String.toDeviceState(): DeviceState = when (this) {
    "Booted" -> DeviceState.RUNNING
    "Booting", "Shutting Down" -> DeviceState.STARTING
    "Shutdown" -> DeviceState.STOPPED
    else -> DeviceState.UNAVAILABLE
}
