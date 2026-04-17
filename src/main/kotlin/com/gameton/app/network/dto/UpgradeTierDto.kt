package com.gameton.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpgradeTierDto(
    val name: String,
    val current: Int,
    val max: Int
)
