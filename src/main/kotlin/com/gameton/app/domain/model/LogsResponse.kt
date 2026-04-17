package com.gameton.app.domain.model

/**
 * Domain representation of GET /api/logs response.
 *
 * API can return either logs array or an error object (for example, when token
 * is not registered in the game yet).
 */
sealed interface LogsResponse

