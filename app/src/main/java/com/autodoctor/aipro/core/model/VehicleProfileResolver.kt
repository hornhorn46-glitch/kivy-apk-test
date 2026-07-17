package com.autodoctor.aipro.core.model

import kotlin.math.abs

class VehicleProfileResolver(
    private val profiles: List<VehicleProfile>,
    private val mappings: List<BrandProfileMapping>,
) {
    fun resolve(request: VehicleProfileRequest): VehicleProfile {
        val exact = profiles.firstOrNull { profile ->
            profile.make.equals(request.make, ignoreCase = true) &&
                profile.model.equals(request.model, ignoreCase = true) &&
                (request.year == null || profile.year == null || profile.year == request.year)
        }
        if (exact != null) return exact

        bestGenericProfile(request)?.let { return it }

        return profiles.firstOrNull { it.id == broadFallbackId(request) }
            ?: profiles.firstOrNull { it.id == brandDefault(request.make) }
            ?: UniversalVehicleProfile
    }

    private fun bestGenericProfile(request: VehicleProfileRequest): VehicleProfile? {
        val displacement = request.displacementLiters
        val candidates = profiles.filter { profile ->
            profile.id.startsWith("generic-gasoline-") &&
                inductionMatches(profile, request) &&
                injectionMatches(profile, request) &&
                airMeteringMatches(profile, request)
        }
        if (candidates.isEmpty()) return null
        if (displacement == null) return candidates.maxByOrNull { specificityScore(it, request) }
        return candidates.minWithOrNull(
            compareBy<VehicleProfile> { abs((it.displacementLiters ?: displacement) - displacement) }
                .thenByDescending { specificityScore(it, request) },
        )
    }

    private fun broadFallbackId(request: VehicleProfileRequest): String {
        return when {
            request.induction == InductionType.Turbocharged -> "universal-gasoline-turbo-maf-map"
            request.induction == InductionType.HybridAssist -> "universal-gasoline-hybrid"
            request.injection == InjectionType.DirectInjection -> "universal-gasoline-gdi-maf-na"
            request.airMetering == AirMeteringType.Map || request.airMetering == AirMeteringType.SpeedDensity -> "universal-gasoline-pfi-map-na"
            else -> brandDefault(request.make)
        }
    }

    private fun inductionMatches(profile: VehicleProfile, request: VehicleProfileRequest): Boolean {
        return request.induction == InductionType.Unknown || profile.induction == request.induction
    }

    private fun injectionMatches(profile: VehicleProfile, request: VehicleProfileRequest): Boolean {
        return request.injection == InjectionType.Unknown || profile.injection == request.injection
    }

    private fun airMeteringMatches(profile: VehicleProfile, request: VehicleProfileRequest): Boolean {
        return when (request.airMetering) {
            AirMeteringType.Unknown -> true
            AirMeteringType.SpeedDensity -> profile.airMetering == AirMeteringType.Map
            AirMeteringType.Maf -> profile.airMetering == AirMeteringType.Maf || profile.airMetering == AirMeteringType.MafAndMap
            AirMeteringType.Map -> profile.airMetering == AirMeteringType.Map || profile.airMetering == AirMeteringType.MafAndMap
            AirMeteringType.MafAndMap -> profile.airMetering == AirMeteringType.MafAndMap
        }
    }

    private fun specificityScore(profile: VehicleProfile, request: VehicleProfileRequest): Int {
        var score = 0
        if (request.induction != InductionType.Unknown && profile.induction == request.induction) score += 3
        if (request.injection != InjectionType.Unknown && profile.injection == request.injection) score += 2
        if (request.airMetering != AirMeteringType.Unknown && airMeteringMatches(profile, request)) score += 1
        return score
    }

    private fun brandDefault(make: String?): String {
        val normalized = make?.trim().orEmpty()
        val mapping = mappings.firstOrNull { item ->
            normalized.isBlank() || item.brands.any { it.equals(normalized, ignoreCase = true) }
        }
        return mapping?.defaultProfileId ?: "universal-gasoline-pfi-maf-na"
    }
}

data class VehicleProfileRequest(
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val displacementLiters: Double? = null,
    val induction: InductionType = InductionType.Unknown,
    val injection: InjectionType = InjectionType.Unknown,
    val airMetering: AirMeteringType = AirMeteringType.Unknown,
)
