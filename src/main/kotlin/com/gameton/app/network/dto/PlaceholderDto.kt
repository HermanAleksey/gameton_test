package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlaceholderDto(
    val placeholder: String = ""
)
