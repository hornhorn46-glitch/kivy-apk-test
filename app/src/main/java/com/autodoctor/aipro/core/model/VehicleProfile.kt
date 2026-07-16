package com.autodoctor.aipro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class VehicleProfile(
    val id: String,
    val make: String,
    val model: String,
    val year: Int?,
    val engineCode: String?,
    val displacementLiters: Double?,
    val powerKw: Double?,
    val torqueNm: Double?,
    val massKg: Double?,
    val induction: InductionType,
    val injection: InjectionType,
    val airMetering: AirMeteringType,
    val redlineRpm: Int?,
    val transmission: TransmissionType,
    val obdType: ObdType,
    val supportedPids: List<String>,
    val references: ReferenceValues,
)

@Serializable
enum class InductionType { NaturallyAspirated, Turbocharged, Supercharged, HybridAssist, Unknown }

@Serializable
enum class InjectionType { PortFuelInjection, DirectInjection, CommonRailDiesel, Carburetor, Unknown }

@Serializable
enum class AirMeteringType { Maf, Map, MafAndMap, SpeedDensity, Unknown }

@Serializable
enum class TransmissionType { Manual, Automatic, Cvt, DualClutch, AutomatedManual, Unknown }

@Serializable
enum class ObdType { Obd2, Eobd, Jobd, Uds, KwP2000, Unknown }

@Serializable
data class ReferenceValues(
    val coolantNormalCelsius: ClosedRangeValue = ClosedRangeValue(82.0, 108.0),
    val intakeAirNormalCelsius: ClosedRangeValue = ClosedRangeValue(-20.0, 70.0),
    val idleRpm: ClosedRangeValue = ClosedRangeValue(600.0, 900.0),
    val fuelTrimNormalPercent: ClosedRangeValue = ClosedRangeValue(-8.0, 8.0),
    val wotThrottlePercent: ClosedRangeValue = ClosedRangeValue(85.0, 100.0),
)

@Serializable
data class ClosedRangeValue(
    val min: Double,
    val max: Double,
) {
    operator fun contains(value: Double): Boolean = value in min..max
}

val UniversalVehicleProfile = VehicleProfile(
    id = "universal-obd2",
    make = "Universal",
    model = "OBD-II",
    year = null,
    engineCode = null,
    displacementLiters = null,
    powerKw = null,
    torqueNm = null,
    massKg = null,
    induction = InductionType.Unknown,
    injection = InjectionType.Unknown,
    airMetering = AirMeteringType.Unknown,
    redlineRpm = null,
    transmission = TransmissionType.Unknown,
    obdType = ObdType.Obd2,
    supportedPids = emptyList(),
    references = ReferenceValues(),
)
