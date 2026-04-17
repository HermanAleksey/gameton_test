package com.gameton.app.di

import com.gameton.app.network.RestApi
import com.gameton.app.network.RestApiConfig
import com.gameton.app.network.createRestApi
import com.gameton.app.ui.GametonController

class AppContainer(
    restApiConfig: RestApiConfig = RestApiConfig()
) {
    val restApi: RestApi = createRestApi(restApiConfig)
    val gametonController: GametonController = GametonController(restApi)

    fun close() {
        gametonController.close()
        restApi.close()
    }
}
