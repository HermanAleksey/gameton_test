package com.gameton.app.domain.model

/**
 * Result of POST /api/command.
 *
 * code is a numeric result code, and errors contains validation messages.
 */
data class CommandResponse(
    val code: Int,
    val errors: List<String> = emptyList()
)

