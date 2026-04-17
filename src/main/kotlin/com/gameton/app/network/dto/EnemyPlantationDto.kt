package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EnemyPlantationDto(
    val id: Int,
    val position: List<Int>,
    val hp: Int
)
