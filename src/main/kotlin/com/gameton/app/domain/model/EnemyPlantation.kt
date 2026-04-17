package com.gameton.app.domain.model

/**
 * Visible enemy plantation state from GET /api/arena.
 */
data class EnemyPlantation(
    val id: String,
    val position: Coordinate,
    val hp: Int
)
