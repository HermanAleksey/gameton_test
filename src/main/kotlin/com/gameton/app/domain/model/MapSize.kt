package com.gameton.app.domain.model

/**
 * Arena dimensions in cells.
 *
 * API transfers size as [width, height].
 */
data class MapSize(
    val width: Int,
    val height: Int
)
