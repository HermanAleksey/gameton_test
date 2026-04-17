package com.gameton.app.domain.model

/**
 * Successful logs response with list of player log entries.
 */
data class LogsSuccess(
    val logs: List<LogEntry>
) : LogsResponse

