package com.healthify.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════════════════════════════
// FOOD CACHE (DB v4)
// Products fetched from Open Food Facts plus foods the user made up. Kept
// locally so recent foods and re-scans work offline. Public data — never
// synced; logged meals carry their own nutrient snapshot.
// ═══════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "food_items",
    indices = [Index(value = ["barcode"], unique = true), Index("lastUsedAt")]
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String? = null,
    val source: String,                   // "off" | "custom"
    val name: String,
    val brand: String? = null,
    val servingSizeG: Float? = null,      // grams (or ml) in one serving
    val servingLabel: String? = null,     // as printed, e.g. "1 cup (240 ml)"
    // Per 100 g (or 100 ml); null when the source doesn't say.
    val kcal100: Float? = null,
    val protein100: Float? = null,
    val carbs100: Float? = null,
    val fat100: Float? = null,
    val fiber100: Float? = null,
    val sugar100: Float? = null,
    val salt100: Float? = null,
    val nutriScore: String? = null,       // "a".."e"
    val novaGroup: Int? = null,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_OFF = "off"
        const val SOURCE_CUSTOM = "custom"
    }
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun byId(id: Long): FoodItemEntity?

    @Query(
        """SELECT * FROM food_items
           WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%'
           ORDER BY useCount DESC, lastUsedAt DESC LIMIT :limit"""
    )
    suspend fun searchLocal(query: String, limit: Int = 20): List<FoodItemEntity>

    @Query("SELECT * FROM food_items WHERE useCount > 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 30): Flow<List<FoodItemEntity>>

    @Insert
    suspend fun insert(item: FoodItemEntity): Long

    @Update
    suspend fun update(item: FoodItemEntity)

    @Query("UPDATE food_items SET useCount = useCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    /** Drops cached products nobody has used lately and no meal points at. */
    @Query(
        """DELETE FROM food_items
           WHERE source = 'off' AND lastUsedAt < :before AND fetchedAt < :before
           AND id NOT IN (SELECT foodItemId FROM meal_entries WHERE foodItemId IS NOT NULL)"""
    )
    suspend fun pruneUnused(before: Long): Int
}
