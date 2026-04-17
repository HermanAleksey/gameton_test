package com.gameton.app.ui.model

sealed interface LogsResponseUi

data class UiCoordinate(
    val x: Int,
    val y: Int
)

data class UiMapSize(
    val width: Int,
    val height: Int
)

data class ApiErrorUi(
    val code: Int,
    val errors: List<String> = emptyList()
)

data class LogEntryUi(
    val time: String,
    val message: String
)

data class LogsSuccessUi(
    val logs: List<LogEntryUi>
) : LogsResponseUi

data class LogsErrorUi(
    val error: ApiErrorUi
) : LogsResponseUi

data class UpgradeTierUi(
    val name: String,
    val current: Int,
    val max: Int
)

data class PlantationUpgradesUi(
    val points: Int,
    val intervalTurns: Int,
    val turnsUntilPoints: Int,
    val maxPoints: Int,
    val tiers: List<UpgradeTierUi> = emptyList()
)

data class PlantationUi(
    val id: Int,
    val position: UiCoordinate,
    val isMain: Boolean,
    val isIsolated: Boolean,
    val immunityUntilTurn: Int,
    val hp: Int
)

data class EnemyPlantationUi(
    val id: Int,
    val position: UiCoordinate,
    val hp: Int
)

data class CellUi(
    val position: UiCoordinate,
    val terraformationProgress: Int,
    val turnsUntilDegradation: Int
)

data class ConstructionUi(
    val position: UiCoordinate,
    val progress: Int
)

data class BeaverUi(
    val id: Int,
    val position: UiCoordinate,
    val hp: Int
)

data class MeteoForecastUi(
    val kind: String,
    val turnsUntil: Int? = null,
    val id: String? = null,
    val forming: Boolean? = null,
    val position: UiCoordinate? = null,
    val nextPosition: UiCoordinate? = null,
    val radius: Int? = null
)

data class CommandActionUi(
    val path: List<UiCoordinate>
)

data class CommandRequestUi(
    val command: List<CommandActionUi> = emptyList(),
    val plantationUpgrade: String? = null,
    val relocateMain: List<UiCoordinate>? = null
)

data class CommandResponseUi(
    val code: Int,
    val errors: List<String> = emptyList()
)

data class ArenaResponseUi(
    val turnNo: Int,
    val nextTurnIn: Double,
    val size: UiMapSize,
    val actionRange: Int,
    val plantations: List<PlantationUi> = emptyList(),
    val enemy: List<EnemyPlantationUi> = emptyList(),
    val mountains: List<UiCoordinate> = emptyList(),
    val cells: List<CellUi> = emptyList(),
    val construction: List<ConstructionUi> = emptyList(),
    val beavers: List<BeaverUi> = emptyList(),
    val plantationUpgrades: PlantationUpgradesUi? = null,
    val meteoForecasts: List<MeteoForecastUi> = emptyList()
)
