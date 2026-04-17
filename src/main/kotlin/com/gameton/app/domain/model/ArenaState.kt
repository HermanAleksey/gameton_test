package com.gameton.app.domain.model

/**
 * Snapshot of the world state returned by GET /api/arena for the current turn.
 */
data class ArenaState(
    val turnNo: Int,
    val nextTurnIn: Double,
    val size: MapSize,
    val actionRange: Int,
    val plantations: List<Plantation> = emptyList(),
    val enemy: List<EnemyPlantation> = emptyList(),
    val mountains: List<Coordinate> = emptyList(),
    val cells: List<Cell> = emptyList(),
    val construction: List<Construction> = emptyList(),
    val beavers: List<Beaver> = emptyList(),
    val plantationUpgrades: PlantationUpgrades? = null,
    val meteoForecasts: List<MeteoForecast> = emptyList()
)
