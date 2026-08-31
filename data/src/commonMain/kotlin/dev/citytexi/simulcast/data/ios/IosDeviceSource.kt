package dev.citytexi.simulcast.data.ios

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.process.Command
import dev.citytexi.simulcast.process.CommandResult
import dev.citytexi.simulcast.process.CommandRunner
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class IosDeviceSource(
    private val runner: CommandRunner,
    private val locator: ToolLocator,
) {

    suspend fun list(): Outcome<List<Device>, DeviceError> {
        val xcrun = locator.xcrun() ?: return Outcome.Err(DeviceError.ToolNotFound("xcrun"))

        val command = Command(xcrun, listOf("simctl", "list", "devices", "--json"))
        return when (val result = runner.run(command, TIMEOUT)) {
            is CommandResult.Completed ->
                if (result.exitCode != 0) {
                    Outcome.Err(DeviceError.ToolFailed("xcrun", result.exitCode, result.stderr.trim()))
                } else {
                    try {
                        Outcome.Ok(parseSimctlDevices(result.stdout))
                    } catch (e: SerializationException) {
                        Outcome.Err(DeviceError.ParseFailed("simctl", e.message ?: ""))
                    } catch (e: CancellationException) {
                        // JVM 상의 CancellationException은 IllegalStateException의 서브클래스라
                        // 아래 catch가 이 가드 없이는 취소를 삼켜버린다.
                        throw e
                    } catch (e: IllegalStateException) {
                        Outcome.Err(DeviceError.ParseFailed("simctl", e.message ?: ""))
                    }
                }
            is CommandResult.TimedOut -> Outcome.Err(DeviceError.Timeout("xcrun"))
            is CommandResult.StartFailed -> Outcome.Err(DeviceError.ToolNotFound("xcrun"))
        }
    }

    private companion object {
        val TIMEOUT = 20.seconds
    }
}
