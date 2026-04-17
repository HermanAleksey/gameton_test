package com.gameton.app.domain.model

/**
 * Turn actions payload for POST /api/command.
 *
 * Server expects at least one useful action:
 * command, plantationUpgrade, or relocateMain.
 */
data class CommandRequest(
    val command: List<CommandAction> = emptyList(),
    val plantationUpgrade: String? = null,
    val relocateMain: List<Coordinate>? = null
)

