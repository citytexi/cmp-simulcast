package dev.citytexi.simulcast

import dev.citytexi.simulcast.feature.devices.DeviceListViewModel
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinGraphTest {

    @Test
    fun composed_graph_resolves_the_device_list_entry_point() {
        val koin = koinApplication { modules(appModules) }.koin

        assertNotNull(koin.get<DeviceListViewModel>())

        koin.close()
    }
}
