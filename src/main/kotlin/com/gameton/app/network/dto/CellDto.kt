package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CellDto(
    val position: List<Int>,
    val terraformationProgress: Int,
    val turnsUntilDegradation: Int
)
