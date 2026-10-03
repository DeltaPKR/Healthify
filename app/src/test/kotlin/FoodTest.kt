package com.healthify.app.food

import com.healthify.app.data.db.FoodItemEntity
import com.healthify.app.data.db.MealEntryEntity
import com.healthify.app.data.db.UserEntity
import com.healthify.app.logs.MealQuality
import com.healthify.app.nutrition.CalorieGoal
import com.healthify.app.nutrition.NutritionTargets
import com.healthify.app.nutrition.Portion
import com.healthify.app.nutrition.totals
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String): String =
    OffParserTest::class.java.getResource("/off/$name")!!.readText()

/** Fixtures are real responses saved from the Open Food Facts APIs. */
class OffParserTest {

    @Test fun productByBarcode() {
        val p = OffParser.parseProductResponse(fixture("product_nutella.json"))!!
        assertEquals("3017620422003", p.barcode)
        assertEquals("Nutella", p.name)
        assertEquals("Nutella", p.brand)                 // first of "Nutella, Ferrero"
        assertEquals(539f, p.kcal100!!, 0.01f)
        assertEquals(6.3f, p.protein100!!, 0.01f)
        assertEquals("e", p.nutriScore)
        assertEquals(4, p.novaGroup)
        assertEquals(FoodItemEntity.SOURCE_OFF, p.source)
    }

    @Test fun unknownBarcodeIsNull() {
        assertNull(OffParser.parseProductResponse(fixture("product_missing.json")))
    }

    @Test fun searchHits() {
        val hits = OffParser.parseSearch(fixture("search_oat_milk.json"))
        assertEquals(4, hits.size)
        // kJ only → converted to kcal.
        assertEquals(190f / 4.184f, hits[0].kcal100!!, 0.01f)
        assertEquals("Boring Oat Milk", hits[0].brand)       // brands as an array
        assertEquals("c", hits[0].nutriScore)
        assertNull(hits[1].nutriScore)                       // "unknown"
        assertEquals("Oat milk", hits[3].name)               // English name preferred
        assertEquals(45f, hits[3].kcal100!!, 0.01f)          // kcal wins over kJ
    }

    @Test fun edgeCases() {
        val noName = JSONObject("""{"code":"123","nutriments":{}}""")
        assertNull(OffParser.parseProduct(noName))
        val odd = OffParser.parseProduct(JSONObject(
            """{"code":"abc","product_name":{"en":"Rice"},"serving_quantity":"150",
               "nutriments":{"energy_100g":1500,"fat_100g":-1}}"""
        ))!!
        assertEquals("Rice", odd.name)
        assertNull(odd.barcode)                              // non-digit codes dropped
        assertEquals(150f, odd.servingSizeG!!, 0.01f)        // numeric string accepted
        assertEquals(1500f / 4.184f, odd.kcal100!!, 0.01f)   // "energy" is kJ
        assertNull(odd.fat100)                               // negatives ignored
    }
}

class RateLimiterTest {
    @Test fun slidingWindow() {
        val l = RateLimiter(maxEvents = 2, windowMs = 60_000)
        assertTrue(l.tryAcquire(0))
        assertTrue(l.tryAcquire(1_000))
        assertFalse(l.tryAcquire(2_000))
        assertEquals(58, l.secondsUntilNext(2_000))
        assertTrue(l.tryAcquire(60_000))                     // first one aged out
        assertFalse(l.tryAcquire(60_500))
        assertEquals(0, RateLimiter(1, 1000).secondsUntilNext(0))
    }
}

class NutritionTargetsTest {
    private val base = UserEntity(age = 30, gender = "Male", heightCm = 180f, weightKg = 80f, activityLevel = "moderate")

    @Test fun mifflinStJeor() {
        assertEquals(1780.0, NutritionTargets.bmr(80f, 180f, 30, "Male"), 0.001)
        assertEquals(1614.0, NutritionTargets.bmr(80f, 180f, 30, "Female"), 0.001)
        assertEquals(1697.0, NutritionTargets.bmr(80f, 180f, 30, "Non-binary"), 0.001)
        assertEquals(2760, NutritionTargets.estimate(base))            // 1780 × 1.55 = 2759 → 2760
    }

    @Test fun goalsAndFloor() {
        assertEquals(2350, NutritionTargets.estimate(base.copy(calorieGoal = CalorieGoal.LOSE.key)))   // 2345.2
        // A small sedentary user losing weight never goes below resting needs / 1,200.
        val small = UserEntity(age = 70, gender = "Female", heightCm = 150f, weightKg = 45f,
            activityLevel = "sedentary", calorieGoal = "lose")
        assertEquals(1200, NutritionTargets.estimate(small))
    }

    @Test fun missingAndOverride() {
        assertEquals(listOf("activity"), NutritionTargets.missing(base.copy(activityLevel = "")))
        assertNull(NutritionTargets.estimate(base.copy(heightCm = 0f)))
        assertEquals(1800, NutritionTargets.dailyTarget(base.copy(calorieTargetOverride = 1800)))
        val m = NutritionTargets.macros(2000)
        assertEquals(100, m.proteinG); assertEquals(250, m.carbsG); assertEquals(67, m.fatG)
    }
}

class PortionTest {
    private val food = FoodItemEntity(id = 7, barcode = "1", source = "off", name = "Yogurt",
        kcal100 = 60f, protein100 = 10f, carbs100 = null, fat100 = 2f, nutriScore = "a")
    private val base = MealEntryEntity(date = "2026-10-03", mealType = "breakfast", quality = "well")

    @Test fun snapshotForGrams() {
        val m = Portion.mealFrom(food, 150f, base)
        assertEquals("Yogurt", m.name)
        assertEquals(7L, m.foodItemId)
        assertEquals(90f, m.kcal!!, 0.01f)
        assertEquals(15f, m.proteinG!!, 0.01f)
        assertNull(m.carbsG)
        assertEquals("a", m.nutriScore)
    }

    @Test fun rescaleKeepsRatios() {
        val m = Portion.rescale(Portion.mealFrom(food, 150f, base), 300f)
        assertEquals(180f, m.kcal!!, 0.01f)
        assertEquals(6f, m.fatG!!, 0.01f)
        val quick = base.copy(name = "Toast")
        assertEquals(quick, Portion.rescale(quick, 100f))     // no grams → unchanged
    }

    @Test fun qualityFromNutriScore() {
        assertEquals(MealQuality.WELL, Portion.suggestedQuality("b"))
        assertEquals(MealQuality.OK, Portion.suggestedQuality("c"))
        assertEquals(MealQuality.POOR, Portion.suggestedQuality("E"))
        assertNull(Portion.suggestedQuality(null))
    }

    @Test fun dayTotals() {
        val meals = listOf(
            Portion.mealFrom(food, 100f, base),
            base.copy(name = "Coffee"),                          // quick entry, no calories
            base.copy(quality = "skip")
        )
        val t = meals.totals()
        assertEquals(60, t.kcal)
        assertEquals(10, t.proteinG)
        assertTrue(t.hasUnknown)                                 // the coffee
    }
}
