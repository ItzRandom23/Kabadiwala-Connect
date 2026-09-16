package com.irinteractivestudios.kabadiwalaconnect.util

enum class ConditionMultiplier(val value: Double) { INTACT(1.0), DAMAGED(0.7), PARTIAL(0.4) }

data class Valuation(val estimatedValue: Double, val typicalMin: Double, val typicalMax: Double, val basePrice: Double, val multiplier: Double)

object ValuationCalculator {
    fun calculate(basePrice: Double, weightKg: Double, condition: ConditionMultiplier, qualityAdjustment: Double = 1.0, minPrice: Double = basePrice, maxPrice: Double = basePrice) =
        Valuation(basePrice * weightKg * condition.value * qualityAdjustment, minPrice * weightKg * condition.value * qualityAdjustment, maxPrice * weightKg * condition.value * qualityAdjustment, basePrice, condition.value)
}
