package com.gameton.app.domain.model

/**
 * Generic API error payload containing error code and messages.
 */
data class ApiError(
    val code: Int,
    val errors: List<String> = emptyList()
)

