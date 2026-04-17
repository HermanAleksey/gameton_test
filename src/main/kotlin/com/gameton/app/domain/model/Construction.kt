package com.gameton.app.domain.model

/**
 * Plantation construction progress from GET /api/arena.
 */
data class Construction(
    val position: Coordinate,
    val progress: Int
)

