package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArenaResponseDto(
    val turnNo: Int,
    val nextTurnIn: Int,
    val size: List<Int>,
    val actionRange: Int,
    val plantations: List<PlantationDto> = emptyList(),
    val enemy: List<EnemyPlantationDto> = emptyList(),
    val mountains: List<List<Int>> = emptyList(),
    val cells: List<CellDto> = emptyList(),
    val construction: List<ConstructionDto> = emptyList(),
    val beavers: List<BeaverDto> = emptyList(),
    val plantationUpgrades: PlantationUpgradesDto? = null,
    val meteoForecasts: List<MeteoForecastDto> = emptyList()
)
