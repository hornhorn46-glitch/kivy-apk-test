package com.autodoctor.aipro.core.model

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

        val genericId = when {
            request.induction == InductionType.Turbocharged -> "universal-gasoline-turbo-maf-map"
            request.induction == InductionType.HybridAssist -> "universal-gasoline-hybrid"
            request.injection == InjectionType.DirectInjection -> "universal-gasoline-gdi-maf-na"
            request.airMetering == AirMeteringType.Map || request.airMetering == AirMeteringType.SpeedDensity -> "universal-gasoline-pfi-map-na"
            else -> brandDefault(request.make)
        }

        return profiles.firstOrNull { it.id == genericId }
            ?: profiles.firstOrNull { it.id == brandDefault(request.make) }
            ?: UniversalVehicleProfile
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
    val induction: InductionType = InductionType.Unknown,
    val injection: InjectionType = InjectionType.Unknown,
    val airMetering: AirMeteringType = AirMeteringType.Unknown,
)
