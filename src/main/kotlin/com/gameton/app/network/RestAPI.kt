package com.gameton.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object RestAPI {
    private const val BASE_URL = "https://api.example.com/"

    val client: HttpClient = HttpClient(CIO) {
        defaultRequest {
            url.takeFrom(BASE_URL)
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    fun close() {
        client.close()
    }
}
