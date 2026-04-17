package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeteoForecastDto(
    val kind: String,
    val turnsUntil: Int? = null,
    val id: String? = null,
    val forming: Boolean? = null,
    val position: List<Int>? = null,
    val nextPosition: List<Int>? = null,
    val radius: Int? = null
)
