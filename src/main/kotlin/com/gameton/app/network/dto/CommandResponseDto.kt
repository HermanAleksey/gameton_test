package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CommandResponseDto(
    val code: Int,
    val errors: List<String> = emptyList()
)
