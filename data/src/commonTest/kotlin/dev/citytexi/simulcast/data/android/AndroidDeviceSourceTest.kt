package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.FakeCommandRunner
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.process.CommandResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidDeviceSourceTest {

    private val locator = ToolLocator(
        env = mapOf("ANDROID_HOME" to "/sdk"),
        homeDir = "/h",
        exists = { it.startsWith("/sdk") },
    )

    @Test
    fun joins_stopped_avds_with_running_emulators() = runTest {
        val runner = FakeCommandRunner(
            mapOf(
                listOf("-list-avds") to CommandResult.Completed(0, "Pixel_7\nPixel_Tablet\n", ""),
                listOf("devices", "-l") to CommandResult.Completed(
                    0,
                    "List of devices attached\nemulator-5554  device transport_id:1\n",
                    "",
                ),
                listOf("-s", "emulator-5554", "emu", "avd", "name") to
                    CommandResult.Completed(0, "Pixel_7\nOK\n", ""),
            ),
        )

        val result = AndroidDeviceSource(runner, locator).list()

        assertEquals(
            Outcome.Ok(
                listOf(
                    Device("emulator-5554", "Pixel_7", DevicePlatform.ANDROID, DeviceState.RUNNING),
                    Device("Pixel_Tablet", "Pixel_Tablet", DevicePlatform.ANDROID, DeviceState.STOPPED),
                )
            ),
            result,
        )
    }

    @Test
    fun reports_tool_not_found_when_adb_is_missing() = runTest {
        val emptyLocator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { false })

        val result = AndroidDeviceSource(FakeCommandRunner(emptyMap()), emptyLocator).list()

        assertEquals(Outcome.Err(DeviceError.ToolNotFound("adb")), result)
    }

    @Test
    fun reports_tool_failed_when_adb_exits_nonzero() = runTest {
        val runner = FakeCommandRunner(
            mapOf(
                listOf("-list-avds") to CommandResult.Completed(0, "", ""),
                listOf("devices", "-l") to CommandResult.Completed(1, "", "adb: no permissions"),
            ),
        )

        val result = AndroidDeviceSource(runner, locator).list()

        assertEquals(Outcome.Err(DeviceError.ToolFailed("adb", 1, "adb: no permissions")), result)
    }
}
