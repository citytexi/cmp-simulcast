package dev.citytexi.simulcast.feature.devices

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError

/**
 * android·ios: null 은 아직 조회하지 않았다는 뜻이다. 조회 결과가 0건인 것과 구분해야 빈 화면
 * 문구가 어긋나지 않는다.
 * refreshFailure: getDevices()가 값 대신 예외로 실패했을 때만 채워진다(ADR-0004). 정상 경로에서는
 * 항상 null이고, 다음 refresh가 시작되면 다시 null로 지워진다.
 */
data class DeviceListState(
    val loading: Boolean = false,
    val android: Outcome<List<Device>, DeviceError>? = null,
    val ios: Outcome<List<Device>, DeviceError>? = null,
    val refreshFailure: String? = null,
)
