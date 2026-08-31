package dev.citytexi.simulcast

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.citytexi.simulcast.designsystem.AppTheme

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "cmp-simulcast") {
        AppTheme {
            Text("scaffold")
        }
    }
}
