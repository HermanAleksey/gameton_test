package com.gameton.app.di

import com.gameton.app.network.RestApi
import com.gameton.app.network.RestApiConfig
import com.gameton.app.network.createRestApi

class AppContainer(
    restApiConfig: RestApiConfig = RestApiConfig()
) {
    val restApi: RestApi = createRestApi(restApiConfig)

    fun close() {
        restApi.close()
    }
}
