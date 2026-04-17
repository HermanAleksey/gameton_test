package com.gameton.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gameton Desktop"
    ) {
        MaterialTheme {
            val scope = rememberCoroutineScope()
            var clicks by remember { mutableIntStateOf(0) }
            var status by remember { mutableStateOf("Idle") }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Compose Desktop minimal app")
                Text("Status: $status")
                Button(onClick = {
                    clicks += 1
                    scope.launch {
                        status = "Running coroutine..."
                        delay(400)
                        status = "Done #$clicks"
                    }
                }) {
                    Text("Run coroutine")
                }
            }
        }
    }
}
