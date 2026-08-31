package dev.citytexi.simulcast.domain

import dev.citytexi.simulcast.common.Outcome

/** 한쪽 플랫폼 조회가 실패해도 다른 쪽 결과는 살아 있어야 하므로 갈래를 따로 든다. */
data class DeviceListing(
    val android: Outcome<List<Device>, DeviceError>,
    val ios: Outcome<List<Device>, DeviceError>,
)

interface DeviceRepository {
    suspend fun listDevices(): DeviceListing
}
