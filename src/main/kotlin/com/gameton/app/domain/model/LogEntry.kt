package com.gameton.app.domain.model

/**
 * One player log record from GET /api/logs.
 */
data class LogEntry(
    val time: String,
    val message: String
)

