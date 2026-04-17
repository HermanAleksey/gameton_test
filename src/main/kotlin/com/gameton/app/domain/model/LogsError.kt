package com.gameton.app.domain.model

/**
 * Error variant of logs response.
 */
data class LogsError(
    val error: ApiError
) : LogsResponse

