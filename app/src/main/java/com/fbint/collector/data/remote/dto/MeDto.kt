package com.fbint.collector.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MeEnvelope(val data: MeDto)

@JsonClass(generateAdapter = true)
data class MeDto(
    val id: String,
    val product: ProductDto?,
    val type: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProductDto(
    val id: String,
    val name: String,
)
