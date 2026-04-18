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
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

interface RestApi {
    suspend fun getArena(): Result<ArenaResponseDto>
    suspend fun sendCommand(request: CommandRequestDto): Result<CommandResponseDto>
    suspend fun getLogs(): Result<LogsResponseDto>
    fun close()
}

/**
 * Server list matches the documented OpenAPI `servers` section:
 * https://games-test.datsteam.dev/static/datssol/openapi/openapi.yml
 */
enum class DatsSolServer(
    val baseUrl: String,
    val title: String,
    val description: String
) {
    Test(
        baseUrl = "https://games-test.datsteam.dev/",
        title = "TEST",
        description = "Test server"
    ),
    Production(
        baseUrl = "https://games.datsteam.dev/",
        title = "PROD",
        description = "Production server"
    )
}

class KtorRestApi(
    private val baseUrl: String,
    private val authToken: String
) : RestApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: HttpClient = HttpClient(CIO) {
        defaultRequest {
            url.takeFrom(baseUrl)
            header("X-Auth-Token", authToken)
        }

        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    println(message)
                }
            }
            level = LogLevel.ALL
        }
    }

    override suspend fun getArena(): Result<ArenaResponseDto> = safeCall {
        client.get("api/arena").body()
    }

    override suspend fun sendCommand(request: CommandRequestDto): Result<CommandResponseDto> = safeCall {
        client.post("api/command") {
            header("Content-Type", "application/json")
            setBody(request)
        }.body()
    }

    override suspend fun getLogs(): Result<LogsResponseDto> = safeCall {
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

    override fun close() {
        client.close()
    }

    private suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> {
        return runCatching { block() }
    }
}

data class RestApiConfig(
    val server: DatsSolServer = DatsSolServer.Test,
    val authToken: String = "5b9c9054-08c4-43ba-a588-0fb445278ca4"
)

fun createRestApi(config: RestApiConfig = RestApiConfig()): RestApi {
    return KtorRestApi(
        baseUrl = config.server.baseUrl,
        authToken = config.authToken
    )
}
