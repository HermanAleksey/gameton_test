package com.gameton.app.domain.model

/**
 * Beaver target visible in the current turn.
 */
data class Beaver(
    val id: String,
    val position: Coordinate,
    val hp: Int
)
