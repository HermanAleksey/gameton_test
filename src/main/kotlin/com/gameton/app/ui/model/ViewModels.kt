package com.gameton.app.ui.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.gameton.app.domain.capitan.StrategyId
import com.gameton.app.network.DatsSolServer

enum class TerrainType {
    Desert,
    Mountain,
    Oasis
}

enum class RiskSeverity {
    None,
    Warning,
    High,
    Critical
}

enum class LayerToggle {
    Grid,
    Terraforming,
    Risks,
    BoostedCells,
    RangeAr,
    RangeSr,
    RangeVr,
    OnlyOwn,
    OnlyEnemy,
    OnlyThreats,
    OnlyMeteo,
    Isolated
}

enum class EntityKind {
    OwnPlantation,
    MainPlantation,
    IsolatedPlantation,
    EnemyPlantation,
    Construction,
    BeaverLair,
    Sandstorm,
    Earthquake
}

enum class LegendGroup {
    Terrain,
    Entities,
    Statuses,
    Risks,
    Meteo,
    Layers
}

enum class LegendKind {
    Desert,
    Mountains,
    Terraforming,
    BoostedCell,
    OwnPlantation,
    MainPlantation,
    IsolatedPlantation,
    EnemyPlantation,
    Construction,
    BeaverLair,
    Immunity,
    LowHp,
    DegradationRisk,
    FormingSandstorm,
    MovingSandstorm,
    Earthquake,
    GridLayer,
    RiskLayer,
    BoostedLayer
}

data class IconStyle(
    val fill: Color,
    val stroke: Color,
    val badge: Color? = null
)

data class HighlightQuery(
    val entityKinds: Set<EntityKind> = emptySet(),
    val boostedCells: Boolean = false,
    val riskAtLeast: RiskSeverity? = null,
    val terrainTypes: Set<TerrainType> = emptySet(),
    val layer: LayerToggle? = null
)

data class LegendItemViewModel(
    val kind: LegendKind,
    val group: LegendGroup,
    val label: String,
    val description: String,
    val iconStyle: IconStyle,
    val severity: RiskSeverity = RiskSeverity.None,
    val highlightQuery: HighlightQuery = HighlightQuery()
)

data class MapLayerToggleState(
    val label: String,
    val enabled: Boolean,
    val kind: LayerToggle
)

data class MapCellViewModel(
    val x: Int,
    val y: Int,
    val terrainType: TerrainType,
    val terraformationProgress: Int = 0,
    val turnsUntilDegradation: Int? = null,
    val isBoosted: Boolean = false,
    val riskLevel: RiskSeverity = RiskSeverity.None
)

data class EntityViewModel(
    val id: String,
    val kind: EntityKind,
    val label: String,
    val position: Offset,
    val hp: Int? = null,
    val maxHp: Int? = null,
    val progress: Int? = null,
    val isImmune: Boolean = false,
    val turnsLeft: Int? = null,
    val riskLevel: RiskSeverity = RiskSeverity.None,
    val details: List<String> = emptyList()
)

data class AlertViewModel(
    val id: String,
    val severity: RiskSeverity,
    val title: String,
    val description: String,
    val target: Offset? = null,
    val sourceKind: EntityKind? = null
)

data class SelectionState(
    val entityId: String? = null,
    val position: Offset? = null,
    val pinned: Boolean = false
)

data class ArenaViewState(
    val turnNo: Int,
    val nextTurnInSeconds: Float,
    val mapSize: Pair<Int, Int>,
    val actionRange: Int,
    val connectionState: String,
    val errors: Int,
    val warnings: Int,
    val ownCount: Int,
    val enemyCount: Int,
    val constructionCount: Int,
    val beaverCount: Int,
    val meteoCount: Int,
    val upgradePoints: Int,
    val turnsUntilUpgrade: Int? = null,
    val cells: List<MapCellViewModel>,
    val entities: List<EntityViewModel>,
    val alerts: List<AlertViewModel>,
    val layerToggles: List<MapLayerToggleState>,
    val legendItems: List<LegendItemViewModel>
)

data class ServerConnectionViewState(
    val selectedServer: DatsSolServer,
    val authTokenPreview: String
)

data class StrategySelectionViewState(
    val selectedStrategy: StrategyId,
    val availableStrategies: List<StrategyId>
)
