package dev.citytexi.simulcast.feature.devices

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val devicesModule = module {
    viewModelOf(::DeviceListViewModel)
}
