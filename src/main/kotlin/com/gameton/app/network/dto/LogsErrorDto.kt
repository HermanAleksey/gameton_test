package com.gameton.app.network.dto

data class LogsErrorDto(
    val error: ApiErrorDto
) : LogsResponseDto
