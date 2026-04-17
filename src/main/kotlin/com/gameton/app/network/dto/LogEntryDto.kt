package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LogEntryDto(
    val time: String,
    val message: String
)
