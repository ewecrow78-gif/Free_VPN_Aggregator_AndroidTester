package com.ghostvpn.tester.data.remote

import com.ghostvpn.tester.data.model.TestResult
import com.ghostvpn.tester.data.model.VpnConfig
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AggregatorApi {
    @GET("/api/configs/pending")
    suspend fun getPendingConfigs(): Response<List<VpnConfig>>

    @POST("/api/results")
    suspend fun submitResults(@Body results: List<TestResult>): Response<Void>
}
