package dev.citytexi.simulcast

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.citytexi.simulcast.designsystem.AppTheme
import dev.citytexi.simulcast.feature.devices.DeviceListScreen
import org.koin.compose.KoinApplication

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "cmp-simulcast") {
        KoinApplication(application = { modules(appModules) }) {
            AppTheme {
                DeviceListScreen()
            }
        }
    }
}
