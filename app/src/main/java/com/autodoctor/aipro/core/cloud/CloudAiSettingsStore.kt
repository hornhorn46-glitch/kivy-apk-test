package com.autodoctor.aipro.core.cloud

import android.content.Context

class CloudAiSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_ai_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = preferences.getString("openai_api_key", "").orEmpty()
        set(value) {
            preferences.edit().putString("openai_api_key", value).apply()
        }

    var model: String
        get() = preferences.getString("openai_model", DefaultModel).orEmpty().ifBlank { DefaultModel }
        set(value) {
            preferences.edit().putString("openai_model", value.ifBlank { DefaultModel }).apply()
        }

    fun save(apiKey: String, model: String) {
        this.apiKey = apiKey.trim()
        this.model = model.trim()
    }

    companion object {
        const val DefaultModel = "gpt-5.6-terra"
    }
}
