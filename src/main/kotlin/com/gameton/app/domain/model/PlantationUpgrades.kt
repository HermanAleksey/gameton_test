package com.gameton.app.domain.model

/**
 * Upgrade system state from GET /api/arena.
 */
data class PlantationUpgrades(
    val points: Int,
    val intervalTurns: Int,
    val turnsUntilPoints: Int,
    val maxPoints: Int,
    val tiers: List<UpgradeTier> = emptyList()
)

