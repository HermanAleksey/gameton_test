package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConstructionDto(
    val position: List<Int>,
    val progress: Int
)
