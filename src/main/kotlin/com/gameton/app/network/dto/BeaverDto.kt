package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BeaverDto(
    val id: Int,
    val position: List<Int>,
    val hp: Int
)
