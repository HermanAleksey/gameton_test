package com.gameton.app

import com.gameton.app.network.RestAPI
import com.gameton.app.network.dto.CommandRequestDto
import kotlinx.coroutines.runBlocking

fun TestServerMain() = runBlocking {
    println("=== TestServerMain started ===")

    try {
        val arenaResult = RestAPI.getArena()
        arenaResult
            .onSuccess { arena ->
                println("[getArena] success: turnNo=${arena.turnNo}, nextTurnIn=${arena.nextTurnIn}")
            }
            .onFailure { error ->
                println("[getArena] failure: ${error.message}")
                error.printStackTrace()
            }

        val logsResult = RestAPI.getLogs()
        logsResult
            .onSuccess { logs ->
                println("[getLogs] success: ${logs::class.simpleName}")
            }
            .onFailure { error ->
                println("[getLogs] failure: ${error.message}")
                error.printStackTrace()
            }

        val commandResult = RestAPI.sendCommand(CommandRequestDto())
        commandResult
            .onSuccess { response ->
                println("[sendCommand] success: code=${response.code}, errors=${response.errors}")
            }
            .onFailure { error ->
                println("[sendCommand] failure: ${error.message}")
                error.printStackTrace()
            }
    } finally {
        RestAPI.close()
        println("=== TestServerMain finished ===")
    }
}

fun main(args: Array<String>) {
    TestServerMain()
}
