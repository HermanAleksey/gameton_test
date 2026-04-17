package com.gameton.app.di

import com.gameton.app.domain.capitan.DefaultDecisionMaker
import com.gameton.app.domain.capitan.DecisionMaker
import com.gameton.app.network.RestApi
import com.gameton.app.network.RestApiConfig
import com.gameton.app.network.createRestApi
import com.gameton.app.ui.GametonController

class AppContainer(
    restApiConfig: RestApiConfig = RestApiConfig()
) {
    val restApi: RestApi = createRestApi(restApiConfig)
    val decisionMaker: DecisionMaker = DefaultDecisionMaker()
    val gametonController: GametonController = GametonController(restApi, decisionMaker)

    fun close() {
        gametonController.close()
        restApi.close()
    }
}
