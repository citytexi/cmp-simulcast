package dev.citytexi.simulcast.feature.devices

import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import dev.citytexi.simulcast.domain.DeviceListing
import dev.citytexi.simulcast.domain.DevicePlatform
import dev.citytexi.simulcast.domain.DeviceRepository
import dev.citytexi.simulcast.domain.DeviceState
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceListViewModelTest {

    private val listing = DeviceListing(
        android = Outcome.Ok(listOf(Device("emulator-5554", "Pixel_7", DevicePlatform.ANDROID, DeviceState.RUNNING))),
        ios = Outcome.Err(DeviceError.ToolNotFound("xcrun")),
    )

    private val iosListing = DeviceListing(
        android = Outcome.Err(DeviceError.ToolNotFound("adb")),
        ios = Outcome.Ok(listOf(Device("AAA", "iPhone 16", DevicePlatform.IOS, DeviceState.RUNNING))),
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

    @Test
    fun refresh_carries_the_ios_listing_intact_when_android_fails() = runTest {
        val viewModel = DeviceListViewModel(GetDevicesUseCase(FakeRepository(iosListing)))

        viewModel.testWithInternalState(this) {
            containerHost.refresh()
            expectInternalState { copy(loading = true) }
            expectInternalState { copy(loading = false, android = iosListing.android, ios = iosListing.ios) }
        }
    }

    @Test
    fun refresh_is_ignored_while_a_refresh_is_already_in_flight() = runTest {
        val repository = SuspendingRepository(listing)
        val viewModel = DeviceListViewModel(GetDevicesUseCase(repository))
        val scheduler = testScheduler

        viewModel.testWithInternalState(this) {
            containerHost.refresh()
            expectInternalState { copy(loading = true) }

            // The screen's LaunchedEffect(Unit) refresh and a button press can race — orbit's
            // event loop dispatches intents onto their own coroutines rather than running them
            // to completion one at a time, so a second refresh() can start executing while the
            // first is still in flight. Drain the scheduler here so the second intent's guard
            // check runs now, before the first intent's reduce resolves state.loading to false.
            containerHost.refresh()
            scheduler.runCurrent()

            repository.complete()
            expectInternalState { copy(loading = false, android = listing.android, ios = listing.ios) }
        }

        assertEquals(1, repository.callCount)
    }

    @Test
    fun refresh_resets_loading_and_recovers_when_getDevices_throws_unexpectedly() = runTest {
        val repository = ThrowingThenSucceedingRepository(listing)
        val viewModel = DeviceListViewModel(GetDevicesUseCase(repository))

        viewModel.testWithInternalState(this) {
            containerHost.refresh()
            expectInternalState { copy(loading = true) }
            // getDevices() threw here (a contract violation the real DeviceRepository
            // implementations never commit — CommandRunner reports failure as a value). The
            // guard must still come back to false, or every later refresh() — including the
            // screen's own LaunchedEffect(Unit) one — would be silently ignored forever.
            expectInternalState { copy(loading = false) }

            // Prove the guard actually recovers, not just this one field: a subsequent refresh()
            // must still be able to run to completion.
            containerHost.refresh()
            expectInternalState { copy(loading = true) }
            expectInternalState { copy(loading = false, android = listing.android, ios = listing.ios) }
        }

        assertEquals(2, repository.callCount)
    }
}

private class FakeRepository(private val listing: DeviceListing) : DeviceRepository {
    override suspend fun listDevices(): DeviceListing = listing
}

/** Throws on its first call, then behaves like [FakeRepository] — simulates a contract violation. */
private class ThrowingThenSucceedingRepository(private val listing: DeviceListing) : DeviceRepository {
    var callCount = 0
        private set

    override suspend fun listDevices(): DeviceListing {
        callCount++
        if (callCount == 1) error("boom")
        return listing
    }
}

/** Suspends [listDevices] until [complete] is called, so a test can hold an intent in flight. */
private class SuspendingRepository(private val listing: DeviceListing) : DeviceRepository {
    var callCount = 0
        private set
    private val result = CompletableDeferred<DeviceListing>()

    override suspend fun listDevices(): DeviceListing {
        callCount++
        return result.await()
    }

    fun complete() {
        result.complete(listing)
    }
}
