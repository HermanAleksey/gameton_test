package com.gameton.app.network.mapper

import com.gameton.app.domain.model.ArenaState
import com.gameton.app.domain.model.Beaver
import com.gameton.app.domain.model.Cell
import com.gameton.app.domain.model.Construction
import com.gameton.app.domain.model.Coordinate
import com.gameton.app.domain.model.EnemyPlantation
import com.gameton.app.domain.model.MapSize
import com.gameton.app.domain.model.MeteoForecast
import com.gameton.app.domain.model.Plantation
import com.gameton.app.domain.model.PlantationUpgrades
import com.gameton.app.domain.model.UpgradeTier
import com.gameton.app.network.dto.ArenaResponseDto
import com.gameton.app.network.dto.BeaverDto
import com.gameton.app.network.dto.CellDto
import com.gameton.app.network.dto.ConstructionDto
import com.gameton.app.network.dto.EnemyPlantationDto
import com.gameton.app.network.dto.MeteoForecastDto
import com.gameton.app.network.dto.PlantationDto
import com.gameton.app.network.dto.PlantationUpgradesDto
import com.gameton.app.network.dto.UpgradeTierDto

fun ArenaResponseDto.toDomainModel(): ArenaState = ArenaState(
    turnNo = turnNo,
    nextTurnIn = nextTurnIn,
    size = size.toDomainMapSize("ArenaResponseDto.size"),
    actionRange = actionRange,
    plantations = plantations.map { it.toDomainModel() },
    enemy = enemy.map { it.toDomainModel() },
    mountains = mountains.mapIndexed { index, coordinate ->
        coordinate.toDomainCoordinate("ArenaResponseDto.mountains[$index]")
    },
    cells = cells.map { it.toDomainModel() },
    construction = construction.map { it.toDomainModel() },
    beavers = beavers.map { it.toDomainModel() },
    plantationUpgrades = plantationUpgrades?.toDomainModel(),
    meteoForecasts = meteoForecasts.map { it.toDomainModel() }
)

private fun PlantationDto.toDomainModel(): Plantation = Plantation(
    id = id,
    position = position.toDomainCoordinate("PlantationDto.position"),
    isMain = isMain,
    isIsolated = isIsolated,
    immunityUntilTurn = immunityUntilTurn,
    hp = hp
)

private fun EnemyPlantationDto.toDomainModel(): EnemyPlantation = EnemyPlantation(
    id = id,
    position = position.toDomainCoordinate("EnemyPlantationDto.position"),
    hp = hp
)

private fun CellDto.toDomainModel(): Cell = Cell(
    position = position.toDomainCoordinate("CellDto.position"),
    terraformationProgress = terraformationProgress,
    turnsUntilDegradation = turnsUntilDegradation
)

private fun ConstructionDto.toDomainModel(): Construction = Construction(
    position = position.toDomainCoordinate("ConstructionDto.position"),
    progress = progress
)

private fun BeaverDto.toDomainModel(): Beaver = Beaver(
    id = id,
    position = position.toDomainCoordinate("BeaverDto.position"),
    hp = hp
)

private fun PlantationUpgradesDto.toDomainModel(): PlantationUpgrades = PlantationUpgrades(
    points = points,
    intervalTurns = intervalTurns,
    turnsUntilPoints = turnsUntilPoints,
    maxPoints = maxPoints,
    tiers = tiers.map { it.toDomainModel() }
)

private fun UpgradeTierDto.toDomainModel(): UpgradeTier = UpgradeTier(
    name = name,
    current = current,
    max = max
)

private fun MeteoForecastDto.toDomainModel(): MeteoForecast = MeteoForecast(
    kind = kind,
    turnsUntil = turnsUntil,
    id = id,
    forming = forming,
    position = position?.toDomainCoordinate("MeteoForecastDto.position"),
    nextPosition = nextPosition?.toDomainCoordinate("MeteoForecastDto.nextPosition"),
    radius = radius
)

private fun List<Int>.toDomainCoordinate(fieldName: String): Coordinate {
    require(size == 2) {
        "$fieldName must contain exactly 2 elements (x, y), but got size=$size, value=$this"
    }
    return Coordinate(
        x = this[0],
        y = this[1]
    )
}

private fun List<Int>.toDomainMapSize(fieldName: String): MapSize {
    require(size == 2) {
        "$fieldName must contain exactly 2 elements (width, height), but got size=$size, value=$this"
    }
    return MapSize(
        width = this[0],
        height = this[1]
    )
}
