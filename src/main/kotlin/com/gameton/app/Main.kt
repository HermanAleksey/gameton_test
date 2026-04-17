package com.gameton.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gameton.app.di.AppContainer
import com.gameton.app.ui.GametonDesktopApp
import androidx.compose.runtime.remember

fun main() = application {
    val appContainer = remember { AppContainer() }
    Window(
        onCloseRequest = {
            appContainer.close()
            exitApplication()
        },
        title = "DatsSol Tactical Dashboard",
        state = rememberWindowState(width = 1600.dp, height = 960.dp)
    ) {
        GametonDesktopApp(appContainer = appContainer)
    }
}
