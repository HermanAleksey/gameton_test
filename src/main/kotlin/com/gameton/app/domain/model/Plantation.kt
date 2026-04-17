package com.gameton.app.domain.model

/**
 * Player plantation state from GET /api/arena.
 */
data class Plantation(
    val id: Int,
    val position: Coordinate,
    val isMain: Boolean,
    val isIsolated: Boolean,
    val immunityUntilTurn: Int,
    val hp: Int
)

