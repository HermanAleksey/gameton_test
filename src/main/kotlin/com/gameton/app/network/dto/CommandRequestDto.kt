package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CommandRequestDto(
    val command: List<CommandActionDto> = emptyList(),
    val plantationUpgrade: String? = null,
    val relocateMain: List<List<Int>>? = null
)
