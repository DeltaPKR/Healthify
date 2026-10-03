package com.healthify.app.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {

    @Test fun cmToFtIn_roundsTotalInchesFirst() {
        assertEquals(5 to 10, Units.cmToFtIn(177.8f))
        assertEquals(6 to 0, Units.cmToFtIn(182.5f))   // 71.85 in → 72 in, never 5′12″
        assertEquals(5 to 0, Units.cmToFtIn(152.4f))
    }

    @Test fun ftInRoundTrip() {
        for (ft in 4..7) for (inch in 0..11) {
            assertEquals(ft to inch, Units.cmToFtIn(Units.ftInToCm(ft, inch)))
        }
    }

    @Test fun weightConversions() {
        assertEquals(69.85f, Units.lbToKg(154f), 0.01f)
        assertEquals(154.32f, Units.kgToLb(70f), 0.01f)
        assertEquals(100f, Units.ozToG(Units.gToOz(100f)), 0.001f)
    }

    @Test fun formatting() {
        assertEquals("178 cm", Units.formatHeight(177.8f, UnitSystem.METRIC))
        assertEquals("5′ 10″", Units.formatHeight(177.8f, UnitSystem.IMPERIAL))
        assertEquals("70.5 kg", Units.formatWeight(70.5f, UnitSystem.METRIC))
        assertEquals("70 kg", Units.formatWeight(70f, UnitSystem.METRIC))
        assertEquals("154 lb", Units.formatWeight(69.85f, UnitSystem.IMPERIAL))
        assertEquals("—", Units.formatHeight(0f, UnitSystem.IMPERIAL))
        assertEquals("—", Units.formatWeight(0f, UnitSystem.METRIC))
    }

    @Test fun plainUsesDotAndTrimsZeros() {
        assertEquals("70", Units.plain(70f, 1))
        assertEquals("70.5", Units.plain(70.5f, 1))
        assertEquals("154.3", Units.plain(154.32f, 1))
    }

    @Test fun validRanges() {
        assertEquals(0f, Units.validHeightCm(5.1f))
        assertEquals(175f, Units.validHeightCm(175f))
        assertEquals(0f, Units.validWeightKg(1000f))
        assertEquals(70f, Units.validWeightKg(70f))
    }

    @Test fun unitSystemOf() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.of("imperial"))
        assertEquals(UnitSystem.METRIC, UnitSystem.of("metric"))
        assertEquals(UnitSystem.METRIC, UnitSystem.of(""))
        assertEquals(UnitSystem.METRIC, UnitSystem.of(null))
    }
}

class LegacyUnitsTest {

    private fun cm(ft: Int, inch: Int) = Units.ftInToCm(ft, inch)

    @Test fun metricValuesUntouched() {
        assertNull(LegacyUnits.repair(175f, 70f))
        assertNull(LegacyUnits.repair(0f, 0f))
        assertNull(LegacyUnits.repair(0f, 154f))      // can't tell lb from kg without a height
    }

    @Test fun metresBecomeCentimetres() {
        val r = LegacyUnits.repair(1.75f, 70f)!!
        assertEquals(175f, r.heightCm, 0.01f)
        assertEquals(70f, r.weightKg, 0.001f)
        assertEquals(UnitSystem.METRIC, r.unitSystem)
    }

    @Test fun feetDotInches() {
        val r = LegacyUnits.repair(5.1f, 154f)!!       // typed "5.10"
        assertEquals(cm(5, 10), r.heightCm, 0.01f)
        assertEquals(69.85f, r.weightKg, 0.01f)
        assertEquals(UnitSystem.IMPERIAL, r.unitSystem)

        assertEquals(cm(5, 11), LegacyUnits.legacyFeetToCm(5.11f), 0.01f)
        assertEquals(cm(5, 5),  LegacyUnits.legacyFeetToCm(5.05f), 0.01f)
        assertEquals(cm(6, 0),  LegacyUnits.legacyFeetToCm(6f), 0.01f)
    }

    @Test fun singleDigitInches() {
        assertEquals(cm(5, 6), LegacyUnits.legacyFeetToCm(5.6f), 0.01f)
        assertEquals(cm(5, 2), LegacyUnits.legacyFeetToCm(5.2f), 0.01f)
    }

    @Test fun decimalFeet() {
        assertEquals(5.75f * 30.48f, LegacyUnits.legacyFeetToCm(5.75f), 0.01f)
    }

    @Test fun imperialWithoutWeight() {
        val r = LegacyUnits.repair(5.1f, 0f)!!
        assertEquals(0f, r.weightKg)
    }
}
