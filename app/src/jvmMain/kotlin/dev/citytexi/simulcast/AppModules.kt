package dev.citytexi.simulcast

import dev.citytexi.simulcast.data.dataModule
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import dev.citytexi.simulcast.feature.devices.devicesModule
import dev.citytexi.simulcast.process.CommandRunner
import dev.citytexi.simulcast.process.ProcessCommandRunner
import org.koin.core.module.Module
import org.koin.dsl.module

private val platformModule = module {
    single<CommandRunner> { ProcessCommandRunner() }
    factory { GetDevicesUseCase(get()) }
}

val appModules: List<Module> = listOf(platformModule, dataModule, devicesModule)
