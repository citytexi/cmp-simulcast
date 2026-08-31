package dev.citytexi.simulcast.feature.devices

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DeviceListing
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceRepository
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test

class DeviceListViewModelTest {

    private val listing = DeviceListing(
        android = Outcome.Ok(listOf(Device("emulator-5554", "Pixel_7", DevicePlatform.ANDROID, DeviceState.RUNNING))),
        ios = Outcome.Err(DeviceError.ToolNotFound("xcrun")),
    )

    @Test
    fun refresh_shows_loading_then_carries_both_sides() = runTest {
        val viewModel = DeviceListViewModel(GetDevicesUseCase(FakeRepository(listing)))

        // orbit-test 12.0.0 checks the initial state automatically (autoCheckInitialState
        // defaults to true) and exposes it through testWithInternalState/expectInternalState —
        // the test/expectState/expectInitialState trio from older Orbit majors is deprecated.
        viewModel.testWithInternalState(this) {
            containerHost.refresh()
            expectInternalState { copy(loading = true) }
            expectInternalState { copy(loading = false, android = listing.android, ios = listing.ios) }
        }
    }
}

private class FakeRepository(private val listing: DeviceListing) : DeviceRepository {
    override suspend fun listDevices(): DeviceListing = listing
}
