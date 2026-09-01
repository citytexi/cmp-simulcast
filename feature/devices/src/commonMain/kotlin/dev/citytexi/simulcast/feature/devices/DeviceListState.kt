package dev.citytexi.simulcast.feature.devices

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError

/** null 은 아직 조회하지 않았다는 뜻이다. 조회 결과가 0건인 것과 구분해야 빈 화면 문구가 어긋나지 않는다. */
data class DeviceListState(
    val loading: Boolean = false,
    val android: Outcome<List<Device>, DeviceError>? = null,
    val ios: Outcome<List<Device>, DeviceError>? = null,
)
