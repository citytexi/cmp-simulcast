package dev.citytexi.simulcast.domain

import dev.citytexi.simulcast.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDevicesUseCaseTest {

    @Test
    fun passes_the_listing_through() = runTest {
        val listing = DeviceListing(
            android = Outcome.Ok(listOf(Device("emulator-5554", "Pixel", DevicePlatform.ANDROID, DeviceState.RUNNING))),
            ios = Outcome.Err(DeviceError.ToolNotFound("xcrun")),
        )
        val useCase = GetDevicesUseCase(FakeDeviceRepository(listing))

        assertEquals(listing, useCase())
    }
}

private class FakeDeviceRepository(private val listing: DeviceListing) : DeviceRepository {
    override suspend fun listDevices(): DeviceListing = listing
}
