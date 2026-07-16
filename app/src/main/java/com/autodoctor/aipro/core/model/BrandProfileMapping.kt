package com.autodoctor.aipro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BrandProfileMapping(
    val id: String,
    val source: String,
    val defaultProfileId: String,
    val supportedProfileIds: List<String>,
    val brands: List<String>,
    val selectionNotes: List<String>,
)
