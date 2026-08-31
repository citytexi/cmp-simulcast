package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.domain.DeviceState
import kotlin.test.Test
import kotlin.test.assertEquals

class AdbDevicesParserTest {

    private val output = """
        List of devices attached
        emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 transport_id:1
        emulator-5556          offline transport_id:2
        1A2B3C4D               unauthorized transport_id:3
        R5CT10ABCDE            device product:a53x model:SM_A536N transport_id:4

    """.trimIndent()

    @Test
    fun maps_adb_states_to_the_common_vocabulary() {
        assertEquals(
            listOf(
                AdbEntry("emulator-5554", DeviceState.RUNNING),
                AdbEntry("emulator-5556", DeviceState.STARTING),
                AdbEntry("1A2B3C4D", DeviceState.UNAVAILABLE),
                AdbEntry("R5CT10ABCDE", DeviceState.RUNNING),
            ),
            parseAdbDevices(output),
        )
    }

    @Test
    fun returns_empty_when_nothing_is_attached() {
        assertEquals(emptyList(), parseAdbDevices("List of devices attached\n\n"))
    }
}
