package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CommandActionDto(
    val path: List<List<Int>>
)
