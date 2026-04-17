package com.gameton.app.network.mapper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.gameton.app.network.dto.ApiErrorDto
import com.gameton.app.network.dto.ArenaResponseDto
import com.gameton.app.network.dto.BeaverDto
import com.gameton.app.network.dto.CellDto
import com.gameton.app.network.dto.CommandActionDto
import com.gameton.app.network.dto.CommandRequestDto
import com.gameton.app.network.dto.CommandResponseDto
import com.gameton.app.network.dto.ConstructionDto
import com.gameton.app.network.dto.EnemyPlantationDto
import com.gameton.app.network.dto.LogEntryDto
import com.gameton.app.network.dto.LogsErrorDto
import com.gameton.app.network.dto.LogsResponseDto
import com.gameton.app.network.dto.LogsSuccessDto
import com.gameton.app.network.dto.MeteoForecastDto
import com.gameton.app.network.dto.PlantationDto
import com.gameton.app.network.dto.PlantationUpgradesDto
import com.gameton.app.network.dto.UpgradeTierDto
import com.gameton.app.ui.model.AlertViewModel
import com.gameton.app.ui.model.ApiErrorUi
import com.gameton.app.ui.model.ArenaResponseUi
import com.gameton.app.ui.model.ArenaViewState
import com.gameton.app.ui.model.BeaverUi
import com.gameton.app.ui.model.CellUi
import com.gameton.app.ui.model.CommandActionUi
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.CommandResponseUi
import com.gameton.app.ui.model.ConstructionUi
import com.gameton.app.ui.model.EnemyPlantationUi
import com.gameton.app.ui.model.EntityKind
import com.gameton.app.ui.model.EntityViewModel
import com.gameton.app.ui.model.HighlightQuery
import com.gameton.app.ui.model.IconStyle
import com.gameton.app.ui.model.LayerToggle
import com.gameton.app.ui.model.LegendGroup
import com.gameton.app.ui.model.LegendItemViewModel
import com.gameton.app.ui.model.LegendKind
import com.gameton.app.ui.model.LogEntryUi
import com.gameton.app.ui.model.LogsErrorUi
import com.gameton.app.ui.model.LogsResponseUi
import com.gameton.app.ui.model.LogsSuccessUi
import com.gameton.app.ui.model.MapCellViewModel
import com.gameton.app.ui.model.MapLayerToggleState
import com.gameton.app.ui.model.MeteoForecastUi
import com.gameton.app.ui.model.PlantationUi
import com.gameton.app.ui.model.PlantationUpgradesUi
import com.gameton.app.ui.model.RiskSeverity
import com.gameton.app.ui.model.TerrainType
import com.gameton.app.ui.model.UiCoordinate
import com.gameton.app.ui.model.UiMapSize
import com.gameton.app.ui.model.UpgradeTierUi

fun ApiErrorDto.toUiModel(): ApiErrorUi = ApiErrorUi(
    code = code,
    errors = errors
)

fun ArenaResponseDto.toUiModel(): ArenaResponseUi = ArenaResponseUi(
    turnNo = turnNo,
    nextTurnIn = nextTurnIn,
    size = size.toUiMapSize("ArenaResponseDto.size"),
    actionRange = actionRange,
    plantations = plantations.map { it.toUiModel() },
    enemy = enemy.map { it.toUiModel() },
    mountains = mountains.mapIndexed { index, coordinates ->
        coordinates.toUiCoordinate("ArenaResponseDto.mountains[$index]")
    },
    cells = cells.map { it.toUiModel() },
    construction = construction.map { it.toUiModel() },
    beavers = beavers.map { it.toUiModel() },
    plantationUpgrades = plantationUpgrades?.toUiModel(),
    meteoForecasts = meteoForecasts.map { it.toUiModel() }
)

fun BeaverDto.toUiModel(): BeaverUi = BeaverUi(
    id = id,
    position = position.toUiCoordinate("BeaverDto.position"),
    hp = hp
)

fun CellDto.toUiModel(): CellUi = CellUi(
    position = position.toUiCoordinate("CellDto.position"),
    terraformationProgress = terraformationProgress,
    turnsUntilDegradation = turnsUntilDegradation
)

fun CommandActionDto.toUiModel(): CommandActionUi = CommandActionUi(
    path = path.mapIndexed { index, coordinates ->
        coordinates.toUiCoordinate("CommandActionDto.path[$index]")
    }
)

fun CommandRequestDto.toUiModel(): CommandRequestUi = CommandRequestUi(
    command = command.map { it.toUiModel() },
    plantationUpgrade = plantationUpgrade,
    relocateMain = relocateMain?.mapIndexed { index, coordinates ->
        coordinates.toUiCoordinate("CommandRequestDto.relocateMain[$index]")
    }
)

fun CommandResponseDto.toUiModel(): CommandResponseUi = CommandResponseUi(
    code = code,
    errors = errors
)

fun ConstructionDto.toUiModel(): ConstructionUi = ConstructionUi(
    position = position.toUiCoordinate("ConstructionDto.position"),
    progress = progress
)

fun EnemyPlantationDto.toUiModel(): EnemyPlantationUi = EnemyPlantationUi(
    id = id,
    position = position.toUiCoordinate("EnemyPlantationDto.position"),
    hp = hp
)

fun LogEntryDto.toUiModel(): LogEntryUi = LogEntryUi(
    time = time,
    message = message
)

fun LogsErrorDto.toUiModel(): LogsErrorUi = LogsErrorUi(
    error = error.toUiModel()
)

fun LogsSuccessDto.toUiModel(): LogsSuccessUi = LogsSuccessUi(
    logs = logs.map { it.toUiModel() }
)

fun LogsResponseDto.toUiModel(): LogsResponseUi = when (this) {
    is LogsSuccessDto -> toUiModel()
    is LogsErrorDto -> toUiModel()
}

fun MeteoForecastDto.toUiModel(): MeteoForecastUi = MeteoForecastUi(
    kind = kind,
    turnsUntil = turnsUntil,
    id = id,
    forming = forming,
    position = position?.toUiCoordinate("MeteoForecastDto.position"),
    nextPosition = nextPosition?.toUiCoordinate("MeteoForecastDto.nextPosition"),
    radius = radius
)

fun PlantationDto.toUiModel(): PlantationUi = PlantationUi(
    id = id,
    position = position.toUiCoordinate("PlantationDto.position"),
    isMain = isMain,
    isIsolated = isIsolated,
    immunityUntilTurn = immunityUntilTurn,
    hp = hp
)

fun PlantationUpgradesDto.toUiModel(): PlantationUpgradesUi = PlantationUpgradesUi(
    points = points,
    intervalTurns = intervalTurns,
    turnsUntilPoints = turnsUntilPoints,
    maxPoints = maxPoints,
    tiers = tiers.map { it.toUiModel() }
)

fun UpgradeTierDto.toUiModel(): UpgradeTierUi = UpgradeTierUi(
    name = name,
    current = current,
    max = max
)

fun CommandActionUi.toDto(): CommandActionDto = CommandActionDto(
    path = path.map { it.toApiVector() }
)

fun CommandRequestUi.toDto(): CommandRequestDto = CommandRequestDto(
    command = command.map { it.toDto() },
    plantationUpgrade = plantationUpgrade,
    relocateMain = relocateMain?.map { it.toApiVector() }
)

fun ArenaResponseDto.toArenaViewState(): ArenaViewState {
    val width = size.toUiMapSize("ArenaResponseDto.size").width
    val height = size.toUiMapSize("ArenaResponseDto.size").height
    val mountains = mountains.mapIndexed { index, coordinates ->
        coordinates.toUiCoordinate("ArenaResponseDto.mountains[$index]")
    }.map { it.x to it.y }.toSet()

    val defaultCells = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) {
                add(
                    MapCellViewModel(
                        x = x,
                        y = y,
                        terrainType = if ((x to y) in mountains) TerrainType.Mountain else TerrainType.Desert
                    )
                )
            }
        }
    }

    val overriddenCells = cells.associateBy(
        keySelector = {
            val coord = it.position.toUiCoordinate("CellDto.position")
            coord.x to coord.y
        },
        valueTransform = {
            val coord = it.position.toUiCoordinate("CellDto.position")
            val risk = when {
                it.turnsUntilDegradation <= 3 -> RiskSeverity.Critical
                it.turnsUntilDegradation <= 8 -> RiskSeverity.High
                else -> RiskSeverity.None
            }
            MapCellViewModel(
                x = coord.x,
                y = coord.y,
                terrainType = if ((coord.x to coord.y) in mountains) TerrainType.Mountain else TerrainType.Oasis,
                terraformationProgress = it.terraformationProgress,
                turnsUntilDegradation = it.turnsUntilDegradation,
                riskLevel = risk
            )
        }
    )

    val mergedCells = defaultCells.map { cell ->
        overriddenCells[cell.x to cell.y] ?: cell
    }

    val plantationEntities = plantations.map { plantation ->
        val position = plantation.position.toUiCoordinate("PlantationDto.position")
        val kind = when {
            plantation.isMain -> EntityKind.MainPlantation
            plantation.isIsolated -> EntityKind.IsolatedPlantation
            else -> EntityKind.OwnPlantation
        }
        val risk = when {
            plantation.isIsolated -> RiskSeverity.Critical
            plantation.hp <= 20 -> RiskSeverity.High
            plantation.hp <= 35 -> RiskSeverity.Warning
            else -> RiskSeverity.None
        }
        EntityViewModel(
            id = "own-${plantation.id}",
            kind = kind,
            label = if (plantation.isMain) "HQ" else "P-${plantation.id}",
            position = Offset(position.x.toFloat(), position.y.toFloat()),
            hp = plantation.hp,
            maxHp = 50,
            isImmune = plantation.immunityUntilTurn > turnNo,
            turnsLeft = (plantation.immunityUntilTurn - turnNo).takeIf { it > 0 },
            riskLevel = risk,
            details = listOf("Immunity until turn ${plantation.immunityUntilTurn}")
        )
    }

    val enemyEntities = enemy.map { enemyPlantation ->
        val position = enemyPlantation.position.toUiCoordinate("EnemyPlantationDto.position")
        EntityViewModel(
            id = "enemy-${enemyPlantation.id}",
            kind = EntityKind.EnemyPlantation,
            label = "EN-${enemyPlantation.id}",
            position = Offset(position.x.toFloat(), position.y.toFloat()),
            hp = enemyPlantation.hp,
            maxHp = 50,
            riskLevel = if (enemyPlantation.hp <= 20) RiskSeverity.Warning else RiskSeverity.None
        )
    }

    val constructionEntities = construction.mapIndexed { index, constructionDto ->
        val position = constructionDto.position.toUiCoordinate("ConstructionDto.position")
        EntityViewModel(
            id = "construction-$index",
            kind = EntityKind.Construction,
            label = "Build",
            position = Offset(position.x.toFloat(), position.y.toFloat()),
            progress = constructionDto.progress,
            riskLevel = if (constructionDto.progress < 50) RiskSeverity.Warning else RiskSeverity.None
        )
    }

    val beaverEntities = beavers.map { beaver ->
        val position = beaver.position.toUiCoordinate("BeaverDto.position")
        EntityViewModel(
            id = "beaver-${beaver.id}",
            kind = EntityKind.BeaverLair,
            label = "Beaver ${beaver.id}",
            position = Offset(position.x.toFloat(), position.y.toFloat()),
            hp = beaver.hp,
            maxHp = 100,
            riskLevel = if (beaver.hp > 50) RiskSeverity.High else RiskSeverity.Warning
        )
    }

    val meteoEntities = meteoForecasts.mapIndexedNotNull { index, forecast ->
        val position = forecast.position ?: return@mapIndexedNotNull null
        val uiPosition = position.toUiCoordinate("MeteoForecastDto.position")
        val kindLower = forecast.kind.lowercase()
        val kind = when {
            "sand" in kindLower -> EntityKind.Sandstorm
            "earthquake" in kindLower -> EntityKind.Earthquake
            else -> null
        } ?: return@mapIndexedNotNull null
        EntityViewModel(
            id = forecast.id ?: "meteo-$index",
            kind = kind,
            label = forecast.kind,
            position = Offset(uiPosition.x.toFloat(), uiPosition.y.toFloat()),
            turnsLeft = forecast.turnsUntil,
            riskLevel = if (forecast.forming == true) RiskSeverity.Critical else RiskSeverity.Warning
        )
    }

    val entities = plantationEntities + enemyEntities + constructionEntities + beaverEntities + meteoEntities

    val alerts = entities
        .filter { it.riskLevel == RiskSeverity.High || it.riskLevel == RiskSeverity.Critical }
        .map { entity ->
            AlertViewModel(
                id = "alert-${entity.id}",
                severity = entity.riskLevel,
                title = "${entity.label} requires attention",
                description = entity.details.firstOrNull() ?: "Check current state on map",
                target = entity.position,
                sourceKind = entity.kind
            )
        }

    val warnings = entities.count { it.riskLevel == RiskSeverity.Warning }
    val errors = entities.count { it.riskLevel == RiskSeverity.High || it.riskLevel == RiskSeverity.Critical }

    return ArenaViewState(
        turnNo = turnNo,
        nextTurnInSeconds = nextTurnIn.toFloat(),
        mapSize = width to height,
        connectionState = "Sync OK",
        errors = errors,
        warnings = warnings,
        cells = mergedCells,
        entities = entities,
        alerts = alerts,
        layerToggles = defaultLayerToggles(),
        legendItems = defaultLegendItems()
    )
}

private fun defaultLayerToggles(): List<MapLayerToggleState> = listOf(
    MapLayerToggleState("Grid", true, LayerToggle.Grid),
    MapLayerToggleState("Terraforming", true, LayerToggle.Terraforming),
    MapLayerToggleState("Risks", true, LayerToggle.Risks),
    MapLayerToggleState("Boosted", true, LayerToggle.BoostedCells),
    MapLayerToggleState("AR", false, LayerToggle.RangeAr),
    MapLayerToggleState("SR", false, LayerToggle.RangeSr),
    MapLayerToggleState("VR", false, LayerToggle.RangeVr),
    MapLayerToggleState("Only own", false, LayerToggle.OnlyOwn),
    MapLayerToggleState("Only enemy", false, LayerToggle.OnlyEnemy),
    MapLayerToggleState("Only threats", false, LayerToggle.OnlyThreats),
    MapLayerToggleState("Only meteo", false, LayerToggle.OnlyMeteo),
    MapLayerToggleState("Isolated", false, LayerToggle.Isolated)
)

private fun defaultLegendItems(): List<LegendItemViewModel> = listOf(
    LegendItemViewModel(
        kind = LegendKind.Desert,
        group = LegendGroup.Terrain,
        label = "Desert",
        description = "Default non-terraformed cells",
        iconStyle = IconStyle(fill = Color(0xFFC9A46A), stroke = Color(0xFF9A713A)),
        highlightQuery = HighlightQuery(terrainTypes = setOf(TerrainType.Desert))
    ),
    LegendItemViewModel(
        kind = LegendKind.Mountains,
        group = LegendGroup.Terrain,
        label = "Mountains",
        description = "Blocked cells",
        iconStyle = IconStyle(fill = Color(0xFF7A7C80), stroke = Color(0xFF4F5155)),
        highlightQuery = HighlightQuery(terrainTypes = setOf(TerrainType.Mountain))
    ),
    LegendItemViewModel(
        kind = LegendKind.OwnPlantation,
        group = LegendGroup.Entities,
        label = "Own plantation",
        description = "Controlled plantation",
        iconStyle = IconStyle(fill = Color(0xFF00BFA5), stroke = Color(0xFF0B6F61)),
        highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.OwnPlantation, EntityKind.MainPlantation))
    ),
    LegendItemViewModel(
        kind = LegendKind.EnemyPlantation,
        group = LegendGroup.Entities,
        label = "Enemy plantation",
        description = "Visible enemy cell",
        iconStyle = IconStyle(fill = Color(0xFFE53935), stroke = Color(0xFF8E1F1D)),
        highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.EnemyPlantation))
    ),
    LegendItemViewModel(
        kind = LegendKind.Earthquake,
        group = LegendGroup.Meteo,
        label = "Earthquake",
        description = "Upcoming earthquake event",
        iconStyle = IconStyle(fill = Color(0xFFEF6C00), stroke = Color(0xFFAD4B00)),
        severity = RiskSeverity.Warning,
        highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.Earthquake))
    )
)

private fun List<Int>.toUiCoordinate(fieldName: String): UiCoordinate {
    require(size == 2) {
        "$fieldName must contain exactly 2 elements (x, y), but got size=$size, value=$this"
    }
    return UiCoordinate(
        x = this[0],
        y = this[1]
    )
}

private fun List<Int>.toUiMapSize(fieldName: String): UiMapSize {
    require(size == 2) {
        "$fieldName must contain exactly 2 elements (width, height), but got size=$size, value=$this"
    }
    return UiMapSize(
        width = this[0],
        height = this[1]
    )
}

private fun UiCoordinate.toApiVector(): List<Int> = listOf(x, y)
