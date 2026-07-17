package com.autodoctor.aipro.core.knowledge

import android.content.Context
import com.autodoctor.aipro.core.ai.DriveabilityModel
import com.autodoctor.aipro.core.diagnostics.DiagnosticRule
import com.autodoctor.aipro.core.model.BrandProfileMapping
import com.autodoctor.aipro.core.model.PidDefinition
import com.autodoctor.aipro.core.model.VehicleProfile
import com.autodoctor.aipro.core.reference.ReferenceCurveSet
import kotlinx.serialization.json.Json

class KnowledgeRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun loadRules(): List<DiagnosticRule> {
        return context.assets.list("knowledge/rules")
            ?.filter { it.endsWith(".json") }
            ?.flatMap { file ->
                context.assets.open("knowledge/rules/$file").bufferedReader().use { reader ->
                    json.decodeFromString<List<DiagnosticRule>>(reader.readText())
                }
            }
            ?: emptyList()
    }

    fun loadVehicleProfiles(): List<VehicleProfile> {
        return context.assets.list("knowledge/profiles")
            ?.filter { it.endsWith(".json") }
            ?.map { file ->
                context.assets.open("knowledge/profiles/$file").bufferedReader().use { reader ->
                    json.decodeFromString<VehicleProfile>(reader.readText())
                }
            }
            ?: emptyList()
    }

    fun loadPidDefinitions(): List<PidDefinition> {
        return context.assets.list("knowledge/pids")
            ?.filter { it.endsWith(".json") }
            ?.flatMap { file ->
                context.assets.open("knowledge/pids/$file").bufferedReader().use { reader ->
                    json.decodeFromString<List<PidDefinition>>(reader.readText())
                }
            }
            ?: emptyList()
    }

    fun loadReferenceCurves(): List<ReferenceCurveSet> {
        return context.assets.list("knowledge/reference_curves")
            ?.filter { it.endsWith(".json") }
            ?.map { file ->
                context.assets.open("knowledge/reference_curves/$file").bufferedReader().use { reader ->
                    json.decodeFromString<ReferenceCurveSet>(reader.readText())
                }
            }
            ?: emptyList()
    }

    fun loadBrandProfileMappings(): List<BrandProfileMapping> {
        return context.assets.list("knowledge/brand_profiles")
            ?.filter { it.endsWith(".json") }
            ?.map { file ->
                context.assets.open("knowledge/brand_profiles/$file").bufferedReader().use { reader ->
                    json.decodeFromString<BrandProfileMapping>(reader.readText())
                }
            }
            ?: emptyList()
    }

    fun loadDriveabilityModel(): DriveabilityModel {
        return context.assets.open("knowledge/ai/driveability_neurosymbolic_model.json")
            .bufferedReader()
            .use { reader -> json.decodeFromString<DriveabilityModel>(reader.readText()) }
    }
}
