package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlantationDto(
    val id: Int,
    val position: List<Int>,
    val isMain: Boolean,
    val isIsolated: Boolean,
    val immunityUntilTurn: Int,
    val hp: Int
)
