package dev.citytexi.simulcast.domain

enum class DevicePlatform { ANDROID, IOS }

/** adb 와 simctl 의 상태 어휘를 공통 축으로 접은 것. 매핑은 data 레이어가 한다. */
enum class DeviceState { RUNNING, STARTING, STOPPED, UNAVAILABLE }

/**
 * @param id 실행 중이면 adb serial 또는 simctl UDID, 정지 상태면 AVD 이름이다.
 */
data class Device(
    val id: String,
    val name: String,
    val platform: DevicePlatform,
    val state: DeviceState,
)
