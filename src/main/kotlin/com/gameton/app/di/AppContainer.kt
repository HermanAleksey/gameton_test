package com.gameton.app.di

import com.gameton.app.domain.capitan.StrategyRegistry
import com.gameton.app.network.RestApiConfig
import com.gameton.app.ui.GametonController

class AppContainer(
    restApiConfig: RestApiConfig = RestApiConfig()
) {
    val gametonController: GametonController = GametonController(
        initialConfig = restApiConfig,
        initialStrategy = StrategyRegistry.default()
    )

    fun close() {
        gametonController.close()
    }
}
