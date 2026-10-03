package com.healthify.app.food

import com.healthify.app.data.db.AppDatabase
import com.healthify.app.data.db.FoodItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Foods to log from: the local cache (recent foods, earlier scans, the
 * user's own foods) first, Open Food Facts second. Only foods the user
 * actually looks up by barcode or picks are stored.
 */
class FoodRepository(db: AppDatabase, private val off: OpenFoodFactsClient) {

    private val dao = db.foodDao()

    fun recentFlow(): Flow<List<FoodItemEntity>> = dao.recentFlow()

    suspend fun searchLocal(query: String): List<FoodItemEntity> =
        if (query.isBlank()) emptyList() else dao.searchLocal(query.trim())

    suspend fun searchRemote(query: String): OffResult<List<FoodItemEntity>> = off.search(query.trim())

    /**
     * A barcode from the cache when it's ours or fresh, otherwise from Open
     * Food Facts (and cached). A stale cached copy still answers when OFF
     * can't be reached.
     */
    suspend fun lookupBarcode(barcode: String): OffResult<FoodItemEntity> {
        val cached = dao.byBarcode(barcode)
        val fresh = cached != null && (cached.source == FoodItemEntity.SOURCE_CUSTOM ||
            System.currentTimeMillis() - cached.fetchedAt < CACHE_FRESH_MS)
        if (fresh) return OffResult.Ok(cached!!)
        return when (val r = off.product(barcode)) {
            is OffResult.Ok -> OffResult.Ok(save(r.value))
            else            -> cached?.let { OffResult.Ok(it) } ?: r
        }
    }

    /** Inserts, or refreshes the row with the same barcode keeping its id and usage. */
    suspend fun save(item: FoodItemEntity): FoodItemEntity {
        val existing = item.barcode?.let { dao.byBarcode(it) } ?: item.id.takeIf { it > 0 }?.let { dao.byId(it) }
        return if (existing != null) {
            item.copy(id = existing.id, useCount = existing.useCount, lastUsedAt = existing.lastUsedAt)
                .also { dao.update(it) }
        } else {
            item.copy(id = dao.insert(item))
        }
    }

    suspend fun createCustom(name: String, kcal100: Float?, barcode: String?): FoodItemEntity =
        save(FoodItemEntity(source = FoodItemEntity.SOURCE_CUSTOM, name = name, kcal100 = kcal100, barcode = barcode))

    suspend fun markUsed(id: Long) = dao.markUsed(id, System.currentTimeMillis())

    /** Drops cached products unused for [UNUSED_FOR_MS] that no meal refers to. */
    suspend fun prune(): Int = dao.pruneUnused(System.currentTimeMillis() - UNUSED_FOR_MS)

    private companion object {
        const val CACHE_FRESH_MS = 30L * 24 * 60 * 60 * 1000
        const val UNUSED_FOR_MS  = 180L * 24 * 60 * 60 * 1000
    }
}
