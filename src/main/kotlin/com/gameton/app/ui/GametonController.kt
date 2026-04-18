package com.gameton.app.ui

import com.gameton.app.domain.capitan.DecisionMaker
import com.gameton.app.network.RestApi
import com.gameton.app.network.mapper.toDomainModel
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

class GametonController(
    private val restApi: RestApi,
    private val decisionMaker: DecisionMaker
) {
    private companion object {
        const val DECISION_TIMEOUT_MS = 1_000L
        const val MIN_POLL_DELAY_MS = 50L
        const val TURN_BUFFER_SECONDS = 0.18
        const val ARENA_RETRY_DELAY_MS = 300L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _arenaState = MutableStateFlow<ArenaViewState>(SampleArenaState.create())
    private var lastProcessedTurnNo: Int? = null

    val arenaState: StateFlow<ArenaViewState> = _arenaState.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                val nextPollDelayMs = refreshArena()
                delay(nextPollDelayMs)
            }
        }
    }

    suspend fun sendCommand(request: CommandRequestUi = CommandRequestUi()): Result<CommandResponseUi> {
        return restApi.sendCommand(request.toDto()).map { it.toUiModel() }
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun refreshArena(): Long {
        val result = restApi.getArena()
        return result.fold(
            onSuccess = { arenaResponse ->
                _arenaState.value = arenaResponse.toArenaViewState()
                val turnNo = arenaResponse.turnNo
                if (turnNo != lastProcessedTurnNo) {
                    val decision = withTimeoutOrNull(DECISION_TIMEOUT_MS.milliseconds) {
                        decisionMaker.makeTurn(arenaResponse.toDomainModel())
                    }
                    if (decision != null && !decision.isEmptyTurn()) {
                        sendCommand(decision)
                    }
                    lastProcessedTurnNo = turnNo
                }

                val sleepMs = ((arenaResponse.nextTurnIn - TURN_BUFFER_SECONDS) * 1_000).toLong()
                max(MIN_POLL_DELAY_MS, sleepMs)
            },
            onFailure = {
                // TODO SHOW ERROR
                ARENA_RETRY_DELAY_MS
            }
        )
    }
}

private fun CommandRequestUi.isEmptyTurn(): Boolean {
    return command.isEmpty() && plantationUpgrade == null && relocateMain == null
}
