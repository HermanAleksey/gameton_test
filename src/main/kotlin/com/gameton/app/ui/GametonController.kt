package com.gameton.app.ui

import com.gameton.app.domain.capitan.DecisionMaker
import com.gameton.app.domain.capitan.StrategyDescriptor
import com.gameton.app.domain.capitan.StrategyId
import com.gameton.app.domain.capitan.StrategyRegistry
import com.gameton.app.network.DatsSolServer
import com.gameton.app.network.RestApi
import com.gameton.app.network.RestApiConfig
import com.gameton.app.network.createRestApi
import com.gameton.app.network.mapper.toDomainModel
import com.gameton.app.network.mapper.toArenaViewState
import com.gameton.app.network.mapper.toDto
import com.gameton.app.network.mapper.toUiModel
import com.gameton.app.ui.model.ArenaViewState
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.CommandResponseUi
import com.gameton.app.ui.model.ServerConnectionViewState
import com.gameton.app.ui.model.StrategySelectionViewState
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
    initialConfig: RestApiConfig,
    initialStrategy: StrategyDescriptor = StrategyRegistry.default()
) {
    private companion object {
        const val DECISION_TIMEOUT_MS = 1_000L
        const val MIN_POLL_DELAY_MS = 50L
        const val TURN_BUFFER_SECONDS = 0.18
        const val ARENA_RETRY_DELAY_MS = 300L
    }

    @Volatile
    private var restApi: RestApi = createRestApi(initialConfig)
    private var restApiConfig: RestApiConfig = initialConfig
    @Volatile
    private var decisionMaker: DecisionMaker = initialStrategy.maker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _arenaState = MutableStateFlow<ArenaViewState>(SampleArenaState.create())
    private val _connectionState = MutableStateFlow(initialConfig.toConnectionViewState())
    private val _strategyState = MutableStateFlow(
        StrategySelectionViewState(
            selectedStrategy = initialStrategy.id,
            availableStrategies = StrategyRegistry.all.map { it.id }
        )
    )
    private var lastProcessedTurnNo: Int? = null

    val arenaState: StateFlow<ArenaViewState> = _arenaState.asStateFlow()
    val connectionState: StateFlow<ServerConnectionViewState> = _connectionState.asStateFlow()
    val strategyState: StateFlow<StrategySelectionViewState> = _strategyState.asStateFlow()

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

    fun selectServer(server: DatsSolServer) {
        if (server == restApiConfig.server) return

        val oldApi = restApi
        val newConfig = restApiConfig.copy(server = server)
        restApiConfig = newConfig
        restApi = createRestApi(newConfig)
        _connectionState.value = newConfig.toConnectionViewState()
        lastProcessedTurnNo = null
        oldApi.close()
    }

    fun selectStrategy(strategyId: StrategyId) {
        if (strategyId == _strategyState.value.selectedStrategy) return

        decisionMaker = StrategyRegistry.byId(strategyId).maker()
        _strategyState.value = _strategyState.value.copy(selectedStrategy = strategyId)
        lastProcessedTurnNo = null
    }

    fun close() {
        scope.cancel()
        restApi.close()
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

private fun RestApiConfig.toConnectionViewState(): ServerConnectionViewState {
    val preview = when {
        authToken.length <= 8 -> authToken
        else -> "${authToken.take(4)}...${authToken.takeLast(4)}"
    }
    return ServerConnectionViewState(
        selectedServer = server,
        authTokenPreview = preview
    )
}
