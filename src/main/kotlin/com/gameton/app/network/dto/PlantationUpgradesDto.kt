package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlantationUpgradesDto(
    val points: Int,
    val intervalTurns: Int,
    val turnsUntilPoints: Int,
    val maxPoints: Int,
    val tiers: List<UpgradeTierDto> = emptyList()
)
