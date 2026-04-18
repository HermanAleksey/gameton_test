package com.gameton.app.ui

import com.gameton.app.domain.capitan.DecisionMaker
import com.gameton.app.domain.capitan.StrategyDescriptor
import com.gameton.app.domain.capitan.StrategyId
import com.gameton.app.domain.capitan.StrategyRegistry
import com.gameton.app.domain.model.ArenaState
import com.gameton.app.logging.JournalRecord
import com.gameton.app.logging.JournalSeverity
import com.gameton.app.logging.JournalSource
import com.gameton.app.logging.PlantationActionLogger
import com.gameton.app.network.DatsSolServer
import com.gameton.app.network.RestApi
import com.gameton.app.network.RestApiConfig
import com.gameton.app.network.createRestApi
import com.gameton.app.network.dto.LogsSuccessDto
import com.gameton.app.network.mapper.toDomainModel
import com.gameton.app.network.mapper.toArenaViewState
import com.gameton.app.network.mapper.toDto
import com.gameton.app.network.mapper.toUiModel
import com.gameton.app.ui.model.ArenaViewState
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.CommandResponseUi
import com.gameton.app.ui.model.JournalEntryViewModel
import com.gameton.app.ui.model.JournalViewState
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GametonController(
    initialConfig: RestApiConfig,
    initialStrategy: StrategyDescriptor = StrategyRegistry.default()
) {
    private companion object {
        const val DECISION_TIMEOUT_MS = 1_000L
        const val MIN_POLL_DELAY_MS = 50L
        const val TURN_BUFFER_SECONDS = 0.18
        const val ARENA_RETRY_DELAY_MS = 300L
        const val MAX_JOURNAL_ENTRIES = 300
    }

    @Volatile
    private var restApi: RestApi = createRestApi(initialConfig)
    private var restApiConfig: RestApiConfig = initialConfig
    @Volatile
    private var decisionMaker: DecisionMaker = initialStrategy.maker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logger = PlantationActionLogger()
    private val journalMutex = Mutex()
    private val _arenaState = MutableStateFlow<ArenaViewState>(SampleArenaState.create())
    private val _connectionState = MutableStateFlow(initialConfig.toConnectionViewState())
    private val _strategyState = MutableStateFlow(
        StrategySelectionViewState(
            selectedStrategy = initialStrategy.id,
            availableStrategies = StrategyRegistry.all.map { it.id }
        )
    )
    private val _journalState = MutableStateFlow(JournalViewState(filePath = logger.filePath()))
    private var lastProcessedTurnNo: Int? = null
    private var lastArenaSnapshot: ArenaState? = null
    private val seenServerLogs = linkedSetOf<String>()

    val arenaState: StateFlow<ArenaViewState> = _arenaState.asStateFlow()
    val connectionState: StateFlow<ServerConnectionViewState> = _connectionState.asStateFlow()
    val strategyState: StateFlow<StrategySelectionViewState> = _strategyState.asStateFlow()
    val journalState: StateFlow<JournalViewState> = _journalState.asStateFlow()

    init {
        publishJournal(
            JournalRecord(
                timestamp = LocalDateTime.now(),
                source = JournalSource.Local,
                severity = JournalSeverity.Info,
                title = "Logger ready",
                message = "Local journal file: ${logger.filePath()}"
            )
        )
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
        publishJournal(
            JournalRecord(
                timestamp = LocalDateTime.now(),
                source = JournalSource.Local,
                severity = JournalSeverity.Info,
                title = "Server changed",
                message = "Switched to ${server.title} (${server.baseUrl})"
            )
        )
    }

    fun selectStrategy(strategyId: StrategyId) {
        if (strategyId == _strategyState.value.selectedStrategy) return

        decisionMaker = StrategyRegistry.byId(strategyId).maker()
        _strategyState.value = _strategyState.value.copy(selectedStrategy = strategyId)
        lastProcessedTurnNo = null
        publishJournal(
            JournalRecord(
                timestamp = LocalDateTime.now(),
                source = JournalSource.Local,
                severity = JournalSeverity.Info,
                title = "Strategy changed",
                message = "Selected strategy ${strategyId.title}"
            )
        )
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
                val domainState = arenaResponse.toDomainModel()
                analyzeArenaTransition(lastArenaSnapshot, domainState).forEach(::publishJournal)
                val turnNo = arenaResponse.turnNo
                if (turnNo != lastProcessedTurnNo) {
                    val decision = withTimeoutOrNull(DECISION_TIMEOUT_MS.milliseconds) {
                        decisionMaker.makeTurn(domainState)
                    }
                    if (decision != null && !decision.isEmptyTurn()) {
                        publishJournal(describeDecision(turnNo, decision))
                        val response = sendCommand(decision)
                        publishCommandResponse(turnNo, response)
                    } else {
                        publishJournal(
                            JournalRecord(
                                timestamp = LocalDateTime.now(),
                                source = JournalSource.Analysis,
                                severity = JournalSeverity.Info,
                                title = "Idle turn",
                                message = "Turn $turnNo: no command, upgrade or relocate issued."
                            )
                        )
                    }
                    collectServerLogs()
                    lastProcessedTurnNo = turnNo
                }
                lastArenaSnapshot = domainState

                val sleepMs = ((arenaResponse.nextTurnIn - TURN_BUFFER_SECONDS) * 1_000).toLong()
                max(MIN_POLL_DELAY_MS, sleepMs)
            },
            onFailure = {
                publishJournal(
                    JournalRecord(
                        timestamp = LocalDateTime.now(),
                        source = JournalSource.Local,
                        severity = JournalSeverity.Error,
                        title = "Arena poll failed",
                        message = it.message ?: "Unknown error while polling /api/arena"
                    )
                )
                ARENA_RETRY_DELAY_MS
            }
        )
    }

    private suspend fun collectServerLogs() {
        restApi.getLogs().onSuccess { response ->
            if (response is LogsSuccessDto) {
                response.logs.forEach { log ->
                    val key = "${log.time}|${log.message}"
                    if (seenServerLogs.add(key)) {
                        publishJournal(
                            JournalRecord(
                                timestamp = LocalDateTime.now(),
                                source = JournalSource.Server,
                                severity = if (log.message.contains("destroy", ignoreCase = true) || log.message.contains("error", ignoreCase = true)) {
                                    JournalSeverity.Warning
                                } else {
                                    JournalSeverity.Info
                                },
                                title = "Server log",
                                message = "${log.time}: ${log.message}"
                            )
                        )
                    }
                }
            }
        }.onFailure {
            publishJournal(
                JournalRecord(
                    timestamp = LocalDateTime.now(),
                    source = JournalSource.Local,
                    severity = JournalSeverity.Warning,
                    title = "Logs unavailable",
                    message = it.message ?: "Failed to fetch /api/logs"
                )
            )
        }
    }

    private fun publishCommandResponse(turnNo: Int, response: Result<CommandResponseUi>) {
        response.fold(
            onSuccess = { result ->
                val severity = if (result.errors.isEmpty()) JournalSeverity.Info else JournalSeverity.Error
                val summary = if (result.errors.isEmpty()) {
                    "Turn $turnNo command accepted with code ${result.code}."
                } else {
                    "Turn $turnNo command response code ${result.code}: ${result.errors.joinToString()}"
                }
                publishJournal(
                    JournalRecord(
                        timestamp = LocalDateTime.now(),
                        source = JournalSource.Local,
                        severity = severity,
                        title = "Command response",
                        message = summary
                    )
                )
            },
            onFailure = {
                publishJournal(
                    JournalRecord(
                        timestamp = LocalDateTime.now(),
                        source = JournalSource.Local,
                        severity = JournalSeverity.Error,
                        title = "Command failed",
                        message = "Turn $turnNo: ${it.message ?: "Unknown sendCommand failure"}"
                    )
                )
            }
        )
    }

    private fun describeDecision(turnNo: Int, request: CommandRequestUi): JournalRecord {
        val buildCount = request.command.size
        val relocate = request.relocateMain
        val upgrade = request.plantationUpgrade
        val message = buildString {
            append("Turn ")
            append(turnNo)
            append(": ")
            if (buildCount > 0) {
                append("issued ")
                append(buildCount)
                append(" plantation action")
                if (buildCount > 1) append('s')
            }
            if (upgrade != null) {
                if (isNotEmpty()) append("; ")
                append("upgrade=")
                append(upgrade)
            }
            if (relocate != null) {
                if (isNotEmpty()) append("; ")
                append("relocateMain=")
                append(relocate.joinToString(" -> ") { "(${it.x},${it.y})" })
            }
            if (buildCount > 0) {
                append("; paths=")
                append(
                    request.command.take(3).joinToString(" | ") { action ->
                        action.path.joinToString("->") { "(${it.x},${it.y})" }
                    }
                )
                if (request.command.size > 3) append(" ...")
            }
        }
        return JournalRecord(
            timestamp = LocalDateTime.now(),
            source = JournalSource.Local,
            severity = JournalSeverity.Info,
            title = "Decision issued",
            message = message
        )
    }

    private fun analyzeArenaTransition(previous: ArenaState?, current: ArenaState): List<JournalRecord> {
        if (previous == null) return emptyList()
        val records = mutableListOf<JournalRecord>()
        val previousMain = previous.plantations.firstOrNull { it.isMain && !it.isIsolated }
        val currentMain = current.plantations.firstOrNull { it.isMain && !it.isIsolated }

        if (previousMain != null && currentMain == null) {
            val previousCoord = previousMain.position
            val reasons = mutableListOf<String>()
            val priorProgress = previous.cells.firstOrNull { it.position == previousCoord }?.terraformationProgress ?: 0
            if (priorProgress >= 95) {
                reasons += "HQ cell was almost fully terraformed ($priorProgress%), so disappearance by completion is likely."
            }
            val beaverThreat = previous.beavers.any {
                kotlin.math.abs(it.position.x - previousCoord.x) <= 2 && kotlin.math.abs(it.position.y - previousCoord.y) <= 2
            }
            if (beaverThreat) {
                reasons += "HQ was inside beaver attack radius."
            }
            val sandstormThreat = previous.meteoForecasts.any { forecast ->
                forecast.kind.contains("sand", ignoreCase = true) &&
                    forecast.forming != true &&
                    (forecast.nextPosition ?: forecast.position)?.let { pos ->
                        val radius = forecast.radius ?: return@let false
                        kotlin.math.abs(pos.x - previousCoord.x) <= radius &&
                            kotlin.math.abs(pos.y - previousCoord.y) <= radius
                    } == true
            }
            if (sandstormThreat) {
                reasons += "HQ was on projected sandstorm path."
            }
            if (previous.meteoForecasts.any { it.kind == "earthquake" && (it.turnsUntil ?: Int.MAX_VALUE) <= 1 }) {
                reasons += "Earthquake impact was imminent."
            }
            if (previousMain.hp <= 10) {
                reasons += "HQ HP was critically low (${previousMain.hp})."
            }
            if (previous.plantations.size > 1 && current.plantations.isEmpty()) {
                reasons += "Whole plantation network disappeared together with HQ."
            }
            records += JournalRecord(
                timestamp = LocalDateTime.now(),
                source = JournalSource.Analysis,
                severity = JournalSeverity.Critical,
                title = "HQ destroyed / respawn window",
                message = buildString {
                    append("Turn ")
                    append(current.turnNo)
                    append(": main plantation is missing, network likely reset. ")
                    append(
                        if (reasons.isNotEmpty()) reasons.joinToString(" ")
                        else "Cause could not be isolated from local state; inspect server logs and previous threat markers."
                    )
                }
            )
        }

        if (previousMain == null && currentMain != null) {
            records += JournalRecord(
                timestamp = LocalDateTime.now(),
                source = JournalSource.Analysis,
                severity = JournalSeverity.Warning,
                title = "HQ present again",
                message = "Turn ${current.turnNo}: main plantation is visible again after previous absence."
            )
        }

        if (previous.plantations.size > current.plantations.size + 2) {
            records += JournalRecord(
                timestamp = LocalDateTime.now(),
                source = JournalSource.Analysis,
                severity = JournalSeverity.Warning,
                title = "Heavy plantation losses",
                message = "Turn ${current.turnNo}: plantation count dropped from ${previous.plantations.size} to ${current.plantations.size}."
            )
        }

        return records
    }

    private fun publishJournal(record: JournalRecord) {
        logger.append(record)
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val entry = JournalEntryViewModel(
            id = "${record.timestamp.toLocalTime()}-${record.title}-${record.message.hashCode()}",
            timestamp = record.timestamp.format(formatter),
            source = record.source,
            severity = record.severity,
            title = record.title,
            message = record.message
        )
        scope.launch {
            journalMutex.withLock {
                _journalState.value = _journalState.value.copy(
                    entries = (listOf(entry) + _journalState.value.entries).take(MAX_JOURNAL_ENTRIES)
                )
            }
        }
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
