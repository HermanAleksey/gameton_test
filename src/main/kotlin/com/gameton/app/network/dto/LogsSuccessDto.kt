package com.gameton.app.network.dto

data class LogsSuccessDto(
    val logs: List<LogEntryDto>
) : LogsResponseDto
