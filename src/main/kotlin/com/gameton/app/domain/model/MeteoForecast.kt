package com.gameton.app.domain.model

/**
 * Forecast event from meteoForecasts in GET /api/arena.
 *
 * kind can be values like earthquake or sandstorm.
 */
data class MeteoForecast(
    val kind: String,
    val turnsUntil: Int? = null,
    val id: String? = null,
    val forming: Boolean? = null,
    val position: Coordinate? = null,
    val nextPosition: Coordinate? = null,
    val radius: Int? = null
)
