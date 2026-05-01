package com.fbint.collector.data.remote

import com.fbint.collector.data.remote.dto.MeDto
import com.fbint.collector.data.remote.dto.SurveyEnvelope
import com.fbint.collector.data.remote.dto.SurveyListEnvelope
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface FormbricksManagementApi {

    @GET("api/v1/management/me")
    suspend fun me(@Header("x-api-key") apiKey: String): MeDto

    @GET("api/v1/management/surveys")
    suspend fun listSurveys(@Header("x-api-key") apiKey: String): SurveyListEnvelope

    @GET("api/v1/management/surveys/{id}")
    suspend fun getSurvey(
        @Header("x-api-key") apiKey: String,
        @Path("id") id: String,
    ): SurveyEnvelope
}
