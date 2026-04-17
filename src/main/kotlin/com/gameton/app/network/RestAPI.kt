package com.gameton.app.network

import com.gameton.app.network.dto.ApiErrorDto
import com.gameton.app.network.dto.ArenaResponseDto
import com.gameton.app.network.dto.CommandRequestDto
import com.gameton.app.network.dto.CommandResponseDto
import com.gameton.app.network.dto.LogEntryDto
import com.gameton.app.network.dto.LogsErrorDto
import com.gameton.app.network.dto.LogsResponseDto
import com.gameton.app.network.dto.LogsSuccessDto
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

object RestAPI {
    private const val BASE_URL = "https://games-test.datsteam.dev/"
    private const val AUTH_TOKEN = "5b9c9054-08c4-43ba-a588-0fb445278ca4"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val client: HttpClient = HttpClient(CIO) {
        defaultRequest {
            url.takeFrom(BASE_URL)
            header("X-Auth-Token", AUTH_TOKEN)
        }

        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun getArena(): Result<ArenaResponseDto> = safeCall {
        client.get("api/arena").body()
    }

    suspend fun sendCommand(request: CommandRequestDto): Result<CommandResponseDto> = safeCall {
        client.post("api/command") {
            header("Content-Type", "application/json")
            setBody(request)
        }.body()
    }

    suspend fun getLogs(): Result<LogsResponseDto> = safeCall {
        when (val rawResponse = client.get("api/logs").body<JsonElement>()) {
            is JsonArray -> {
                val logs = rawResponse.map { json.decodeFromJsonElement<LogEntryDto>(it) }
                LogsSuccessDto(logs)
            }

            is JsonObject -> {
                val apiError = json.decodeFromJsonElement<ApiErrorDto>(rawResponse)
                LogsErrorDto(apiError)
            }

            else -> error("Unexpected /api/logs response: $rawResponse")
        }
    }

    private suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> {
        return runCatching { block() }
    }

    fun close() {
        client.close()
    }
}
