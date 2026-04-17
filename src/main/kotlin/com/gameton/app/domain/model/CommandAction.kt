package com.gameton.app.domain.model

/**
 * Single plantation action sent in POST /api/command.
 *
 * path format:
 * - first point: command author plantation
 * - second point: exit point (can be same as author)
 * - third point: action target
 */
data class CommandAction(
    val path: List<Coordinate>
)

