package com.fbint.collector.data.remote

import com.fbint.collector.data.remote.dto.CreateDisplayRequest
import com.fbint.collector.data.remote.dto.CreateResponseEnvelope
import com.fbint.collector.data.remote.dto.CreateResponseRequest
import com.fbint.collector.data.remote.dto.UploadFileEnvelope
import com.fbint.collector.data.remote.dto.UploadFileRequest
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface FormbricksClientApi {

    @POST("api/v1/client/{environmentId}/responses")
    suspend fun createResponse(
        @Path("environmentId") environmentId: String,
        @Body body: CreateResponseRequest,
    ): CreateResponseEnvelope

    @POST("api/v1/client/{environmentId}/displays")
    suspend fun createDisplay(
        @Path("environmentId") environmentId: String,
        @Body body: CreateDisplayRequest,
    )

    @POST("api/v1/client/{environmentId}/storage")
    suspend fun requestUploadUrl(
        @Path("environmentId") environmentId: String,
        @Body body: UploadFileRequest,
    ): UploadFileEnvelope
}
