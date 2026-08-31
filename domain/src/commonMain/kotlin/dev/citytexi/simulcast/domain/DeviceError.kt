package dev.citytexi.simulcast.domain

sealed interface DeviceError {
    data class ToolNotFound(val tool: String) : DeviceError
    data class ToolFailed(val tool: String, val exitCode: Int, val stderr: String) : DeviceError
    data class Timeout(val tool: String) : DeviceError
    data class ParseFailed(val tool: String, val detail: String) : DeviceError
}
