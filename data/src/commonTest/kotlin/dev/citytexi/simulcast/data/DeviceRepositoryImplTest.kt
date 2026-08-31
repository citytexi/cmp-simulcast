package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.android.AndroidDeviceSource
import dev.citytexi.simulcast.data.ios.IosDeviceSource
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.process.CommandResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeviceRepositoryImplTest {

    @Test
    fun a_missing_xcrun_does_not_hide_android_devices() = runTest {
        val androidLocator = ToolLocator(
            env = mapOf("ANDROID_HOME" to "/sdk"),
            homeDir = "/h",
            exists = { it.startsWith("/sdk") },
        )
        val noXcrun = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { false })
        val runner = FakeCommandRunner(
            mapOf(
                listOf("-list-avds") to CommandResult.Completed(0, "Pixel_7\n", ""),
                listOf("devices", "-l") to CommandResult.Completed(0, "List of devices attached\n", ""),
            ),
        )

        val listing = DeviceRepositoryImpl(
            android = AndroidDeviceSource(runner, androidLocator),
            ios = IosDeviceSource(runner, noXcrun),
        ).listDevices()

        val android = assertIs<Outcome.Ok<*>>(listing.android)
        assertEquals(1, (android.value as List<*>).size)
        assertEquals(Outcome.Err(DeviceError.ToolNotFound("xcrun")), listing.ios)
    }

    @Test
    fun a_missing_adb_does_not_hide_ios_devices() = runTest {
        val noAdb = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { false })
        val iosLocator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { it == "/usr/bin/xcrun" })
        val simctlJson = """
            {
              "devices": {
                "com.apple.CoreSimulator.SimRuntime.iOS-18-2": [
                  { "udid": "AAA", "name": "iPhone 16", "state": "Booted", "isAvailable": true }
                ]
              }
            }
        """.trimIndent()
        val runner = FakeCommandRunner(
            mapOf(listOf("simctl", "list", "devices", "--json") to CommandResult.Completed(0, simctlJson, "")),
        )

        val listing = DeviceRepositoryImpl(
            android = AndroidDeviceSource(runner, noAdb),
            ios = IosDeviceSource(runner, iosLocator),
        ).listDevices()

        assertEquals(Outcome.Err(DeviceError.ToolNotFound("adb")), listing.android)
        assertEquals(
            Outcome.Ok(listOf(Device("AAA", "iPhone 16", DevicePlatform.IOS, DeviceState.RUNNING))),
            listing.ios,
        )
    }
}
