package com.autodoctor.aipro.core.performance

data class AccelerationTestConfig(
    val startRpm: Double = 1_500.0,
    val endRpm: Double = 5_000.0,
    val requestedGear: Int = 3,
    val requireWideOpenThrottlePercent: Double = 85.0,
)

data class PowerEstimate(
    val method: PowerMethod,
    val powerKw: Double,
    val confidence: Double,
    val explanation: String,
)

enum class PowerMethod {
    AirMass,
    VehicleAcceleration,
    CalculatedLoad,
}

data class CombinedPowerEstimate(
    val meanKw: Double,
    val lowKw: Double,
    val highKw: Double,
    val confidence: Double,
    val estimates: List<PowerEstimate>,
    val warnings: List<String>,
)
