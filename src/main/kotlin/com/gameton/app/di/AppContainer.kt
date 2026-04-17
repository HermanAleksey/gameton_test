package com.gameton.app.di

import com.gameton.app.domain.capitan.DecisionMaker
import com.gameton.app.domain.capitan.SimpleExpansionDecisionMaker
import com.gameton.app.network.RestApi
import com.gameton.app.network.RestApiConfig
import com.gameton.app.network.createRestApi
import com.gameton.app.ui.GametonController

class AppContainer(
    restApiConfig: RestApiConfig = RestApiConfig()
) {
    val restApi: RestApi = createRestApi(restApiConfig)
    val decisionMaker: DecisionMaker = SimpleExpansionDecisionMaker()
    val gametonController: GametonController = GametonController(restApi, decisionMaker)

    fun close() {
        gametonController.close()
        restApi.close()
    }
}
