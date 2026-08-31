package dev.citytexi.simulcast.common

import kotlin.test.Test
import kotlin.test.assertTrue

class ToolchainTest {
    @Test
    fun runs_on_jvm_21() {
        val version = System.getProperty("java.version")
        assertTrue(version.startsWith("21"), "expected JVM 21, got $version")
    }
}
