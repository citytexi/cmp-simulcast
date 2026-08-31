package dev.citytexi.simulcast.data.ios

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
import kotlin.test.assertIs

class IosDeviceSourceTest {

    private val locator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { it == "/usr/bin/xcrun" })

    private val json = """
        {
          "devices": {
            "com.apple.CoreSimulator.SimRuntime.iOS-18-2": [
              { "udid": "AAA", "name": "iPhone 16", "state": "Booted", "isAvailable": true },
              { "udid": "BBB", "name": "iPhone 16 Pro", "state": "Shutdown", "isAvailable": true },
              { "udid": "CCC", "name": "iPhone SE", "state": "Shutdown", "isAvailable": false }
            ],
            "com.apple.CoreSimulator.SimRuntime.watchOS-11-2": [
              { "udid": "DDD", "name": "Apple Watch", "state": "Shutdown", "isAvailable": true }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun keeps_only_available_ios_runtimes() = runTest {
        val runner = FakeCommandRunner(
            mapOf(listOf("simctl", "list", "devices", "--json") to CommandResult.Completed(0, json, "")),
        )

        val result = IosDeviceSource(runner, locator).list()

        assertEquals(
            Outcome.Ok(
                listOf(
                    Device("AAA", "iPhone 16", DevicePlatform.IOS, DeviceState.RUNNING),
                    Device("BBB", "iPhone 16 Pro", DevicePlatform.IOS, DeviceState.STOPPED),
                )
            ),
            result,
        )
    }

    @Test
    fun skips_only_the_entry_missing_a_required_field() = runTest {
        val partiallyMalformedJson = """
            {
              "devices": {
                "com.apple.CoreSimulator.SimRuntime.iOS-18-2": [
                  { "udid": "AAA", "name": "iPhone 16", "state": "Booted", "isAvailable": true },
                  { "name": "iPhone 16 Pro", "state": "Shutdown", "isAvailable": true },
                  { "udid": "CCC", "name": "iPhone SE", "state": "Shutdown", "isAvailable": true }
                ]
              }
            }
        """.trimIndent()
        val runner = FakeCommandRunner(
            mapOf(listOf("simctl", "list", "devices", "--json") to CommandResult.Completed(0, partiallyMalformedJson, "")),
        )

        val result = IosDeviceSource(runner, locator).list()

        assertEquals(
            Outcome.Ok(
                listOf(
                    Device("AAA", "iPhone 16", DevicePlatform.IOS, DeviceState.RUNNING),
                    Device("CCC", "iPhone SE", DevicePlatform.IOS, DeviceState.STOPPED),
                )
            ),
            result,
        )
    }

    @Test
    fun maps_every_simctl_state_to_the_common_vocabulary() = runTest {
        val stateJson = """
            {
              "devices": {
                "com.apple.CoreSimulator.SimRuntime.iOS-18-2": [
                  { "udid": "AAA", "name": "iPhone 16", "state": "Booted", "isAvailable": true },
                  { "udid": "BBB", "name": "iPhone 16 Pro", "state": "Shutdown", "isAvailable": true },
                  { "udid": "CCC", "name": "iPhone 15", "state": "Booting", "isAvailable": true },
                  { "udid": "DDD", "name": "iPhone 14", "state": "Shutting Down", "isAvailable": true },
                  { "udid": "EEE", "name": "iPhone 13", "state": "Creating", "isAvailable": true }
                ]
              }
            }
        """.trimIndent()
        val runner = FakeCommandRunner(
            mapOf(listOf("simctl", "list", "devices", "--json") to CommandResult.Completed(0, stateJson, "")),
        )

        val result = IosDeviceSource(runner, locator).list()

        assertEquals(
            Outcome.Ok(
                listOf(
                    Device("AAA", "iPhone 16", DevicePlatform.IOS, DeviceState.RUNNING),
                    Device("BBB", "iPhone 16 Pro", DevicePlatform.IOS, DeviceState.STOPPED),
                    Device("CCC", "iPhone 15", DevicePlatform.IOS, DeviceState.STARTING),
                    Device("DDD", "iPhone 14", DevicePlatform.IOS, DeviceState.STARTING),
                    Device("EEE", "iPhone 13", DevicePlatform.IOS, DeviceState.UNAVAILABLE),
                )
            ),
            result,
        )
    }

    @Test
    fun reports_parse_failure_on_unreadable_output() = runTest {
        val runner = FakeCommandRunner(
            mapOf(listOf("simctl", "list", "devices", "--json") to CommandResult.Completed(0, "not json", "")),
        )

        val result = IosDeviceSource(runner, locator).list()

        assertIs<Outcome.Err<DeviceError.ParseFailed>>(result)
    }

    @Test
    fun reports_parse_failure_when_devices_is_not_an_object() = runTest {
        val runner = FakeCommandRunner(
            mapOf(
                listOf("simctl", "list", "devices", "--json") to
                    CommandResult.Completed(0, """{"devices": []}""", ""),
            ),
        )

        val result = IosDeviceSource(runner, locator).list()

        assertIs<Outcome.Err<DeviceError.ParseFailed>>(result)
    }

    @Test
    fun reports_tool_not_found_when_xcrun_is_missing() = runTest {
        val emptyLocator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { false })

        val result = IosDeviceSource(FakeCommandRunner(emptyMap()), emptyLocator).list()

        assertEquals(Outcome.Err(DeviceError.ToolNotFound("xcrun")), result)
    }
}
