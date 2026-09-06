package com.irinteractivestudios.kabadiwalaconnect

import com.irinteractivestudios.kabadiwalaconnect.util.ConditionMultiplier
import com.irinteractivestudios.kabadiwalaconnect.util.RecordingPriceSpeaker
import com.irinteractivestudios.kabadiwalaconnect.util.ValuationCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceValuationTest {
    @Test fun valuation_usesPriceWeightConditionAndQuality() {
        val result = ValuationCalculator.calculate(100.0, 10.0, ConditionMultiplier.DAMAGED, qualityAdjustment = 0.9, minPrice = 80.0, maxPrice = 120.0)
        assertEquals(630.0, result.estimatedValue, 0.001)
        assertEquals(504.0, result.typicalMin, 0.001)
        assertEquals(756.0, result.typicalMax, 0.001)
    }

    @Test fun conditionMultipliers_matchProductRules() {
        assertEquals(1.0, ConditionMultiplier.INTACT.value, 0.0)
        assertEquals(0.7, ConditionMultiplier.DAMAGED.value, 0.0)
        assertEquals(0.4, ConditionMultiplier.PARTIAL.value, 0.0)
    }

    @Test fun recordingSpeaker_supportsHindiAndMarathiRequests() {
        val speaker = RecordingPriceSpeaker()
        speaker.speak("CRT", 42.0, "hi")
        assertTrue(speaker.lastMessage!!.endsWith(":hi"))
        speaker.speak("CRT", 42.0, "mr")
        assertTrue(speaker.lastMessage!!.endsWith(":mr"))
    }
}
