package dev.citytexi.simulcast.data.android

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.process.Command
import dev.citytexi.simulcast.process.CommandResult
import dev.citytexi.simulcast.process.CommandRunner
import kotlin.time.Duration.Companion.seconds

class AndroidDeviceSource(
    private val runner: CommandRunner,
    private val locator: ToolLocator,
) {

    suspend fun list(): Outcome<List<Device>, DeviceError> {
        val adb = locator.adb() ?: return Outcome.Err(DeviceError.ToolNotFound("adb"))

        val avdNames = locator.emulator()
            ?.let { emulator -> runner.run(Command(emulator, listOf("-list-avds")), TIMEOUT) }
            ?.let { it as? CommandResult.Completed }
            ?.stdout
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()

        val attached = when (val result = runner.run(Command(adb, listOf("devices", "-l")), TIMEOUT)) {
            is CommandResult.Completed ->
                if (result.exitCode == 0) parseAdbDevices(result.stdout)
                else return Outcome.Err(DeviceError.ToolFailed("adb", result.exitCode, result.stderr.trim()))
            is CommandResult.TimedOut -> return Outcome.Err(DeviceError.Timeout("adb"))
            is CommandResult.StartFailed -> return Outcome.Err(DeviceError.ToolNotFound("adb"))
        }

        val running = attached.map { entry ->
            val avdName = avdNameOf(adb, entry.serial)
            Device(entry.serial, avdName ?: entry.serial, DevicePlatform.ANDROID, entry.state)
        }
        val runningAvdNames = running.mapNotNull { it.name }.toSet()
        val stopped = avdNames
            .filterNot { it in runningAvdNames }
            .map { Device(it, it, DevicePlatform.ANDROID, DeviceState.STOPPED) }

        return Outcome.Ok(running + stopped)
    }

    /** 실물 기기는 AVD 이름이 없다. 그 경우 null 이고 호출부가 serial 로 대신한다. */
    private suspend fun avdNameOf(adb: String, serial: String): String? {
        val result = runner.run(Command(adb, listOf("-s", serial, "emu", "avd", "name")), TIMEOUT)
        val stdout = (result as? CommandResult.Completed)?.takeIf { it.exitCode == 0 }?.stdout ?: return null
        return stdout.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() && it != "OK" }
    }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}
