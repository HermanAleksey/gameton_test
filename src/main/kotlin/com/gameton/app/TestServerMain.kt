package com.gameton.app

import com.gameton.app.di.AppContainer
import com.gameton.app.network.dto.CommandRequestDto
import kotlinx.coroutines.runBlocking

fun TestServerMain() = runBlocking {
    println("=== TestServerMain started ===")
    val appContainer = AppContainer()
    val restApi = appContainer.restApi

    try {
        val arenaResult = restApi.getArena()
        arenaResult
            .onSuccess { arena ->
                println("[getArena] success: turnNo=${arena.turnNo}, nextTurnIn=${arena.nextTurnIn} ${arena.size}")
            }
            .onFailure { error ->
                println("[getArena] failure: ${error.message}")
                error.printStackTrace()
            }

        val logsResult = restApi.getLogs()
        logsResult
            .onSuccess { logs ->
                println("[getLogs] success: ${logs::class.simpleName}")
            }
            .onFailure { error ->
                println("[getLogs] failure: ${error.message}")
                error.printStackTrace()
            }

        val commandResult = restApi.sendCommand(CommandRequestDto())
        commandResult
            .onSuccess { response ->
                println("[sendCommand] success: code=${response.code}, errors=${response.errors}")
            }
            .onFailure { error ->
                println("[sendCommand] failure: ${error.message}")
                error.printStackTrace()
            }
    } finally {
        appContainer.close()
        println("=== TestServerMain finished ===")
    }
}

fun main(args: Array<String>) {
    TestServerMain()
}
