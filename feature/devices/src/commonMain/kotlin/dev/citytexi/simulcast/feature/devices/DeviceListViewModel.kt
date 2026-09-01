package dev.citytexi.simulcast.feature.devices

import androidx.lifecycle.ViewModel
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class DeviceListViewModel(
    private val getDevices: GetDevicesUseCase,
) : ViewModel(), OrbitContainerHost<DeviceListState, DeviceListState, Nothing> {

    override val container = orbitContainer<DeviceListState, Nothing>(DeviceListState())

    fun refresh() = intent {
        if (state.loading) return@intent
        reduce { state.copy(loading = true) }
        val listing = getDevices()
        reduce { state.copy(loading = false, android = listing.android, ios = listing.ios) }
    }
}
