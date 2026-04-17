package com.gameton.app.domain.model

/**
 * Known terraformed cell from GET /api/arena.
 */
data class Cell(
    val position: Coordinate,
    val terraformationProgress: Int,
    val turnsUntilDegradation: Int
)

