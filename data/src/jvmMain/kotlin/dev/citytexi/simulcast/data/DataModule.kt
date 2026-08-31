package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.data.android.AndroidDeviceSource
import dev.citytexi.simulcast.data.ios.IosDeviceSource
import dev.citytexi.simulcast.data.tool.ToolLocator
import dev.citytexi.simulcast.domain.DeviceRepository
import org.koin.dsl.module

val dataModule = module {
    single { ToolLocator(env = System.getenv(), homeDir = System.getProperty("user.home"), exists = { java.io.File(it).canExecute() }) }
    single { AndroidDeviceSource(get(), get()) }
    single { IosDeviceSource(get(), get()) }
    single<DeviceRepository> { DeviceRepositoryImpl(get(), get()) }
}
