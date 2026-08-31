package dev.citytexi.simulcast.data.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolLocatorTest {

    @Test
    fun prefers_the_sdk_root_from_the_environment() {
        val locator = ToolLocator(
            env = mapOf("ANDROID_HOME" to "/sdk"),
            homeDir = "/Users/someone",
            exists = { it == "/sdk/platform-tools/adb" },
        )

        assertEquals("/sdk/platform-tools/adb", locator.adb())
    }

    @Test
    fun falls_back_to_the_conventional_path_under_home() {
        val conventional = "/Users/someone/Library/Android/sdk/platform-tools/adb"
        val locator = ToolLocator(
            env = emptyMap(),
            homeDir = "/Users/someone",
            exists = { it == conventional },
        )

        assertEquals(conventional, locator.adb())
    }

    @Test
    fun returns_null_when_nothing_matches() {
        val locator = ToolLocator(env = emptyMap(), homeDir = "/Users/someone", exists = { false })

        assertNull(locator.adb())
        assertNull(locator.emulator())
        assertNull(locator.xcrun())
    }

    @Test
    fun finds_xcrun_at_its_fixed_location() {
        val locator = ToolLocator(env = emptyMap(), homeDir = "/h", exists = { it == "/usr/bin/xcrun" })

        assertEquals("/usr/bin/xcrun", locator.xcrun())
    }
}
