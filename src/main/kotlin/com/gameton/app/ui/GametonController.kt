package com.gameton.app.ui

import com.gameton.app.network.RestApi
import com.gameton.app.network.mapper.toArenaViewState
import com.gameton.app.network.mapper.toDto
import com.gameton.app.network.mapper.toUiModel
import com.gameton.app.ui.model.ArenaViewState
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.CommandResponseUi
import com.gameton.app.ui.sample.SampleArenaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GametonController(
    private val restApi: RestApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _arenaState = MutableStateFlow<ArenaViewState>(SampleArenaState.create())

    val arenaState: StateFlow<ArenaViewState> = _arenaState.asStateFlow()

    init {
        scope.launch {
            refreshArena()
            while (isActive) {
                delay(1_000)
                refreshArena()
            }
        }
    }

    suspend fun sendCommand(request: CommandRequestUi = CommandRequestUi()): Result<CommandResponseUi> {
        return restApi.sendCommand(request.toDto()).map { it.toUiModel() }
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun refreshArena() {
        val result = restApi.getArena()
        result.onSuccess { arenaResponse ->
            _arenaState.value = arenaResponse.toArenaViewState()
        }.onFailure {
            // TODO SHOW ERROR
        }
    }
}
