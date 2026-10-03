package com.healthify.app.food

import com.healthify.app.data.db.FoodItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/** Outcome of an Open Food Facts call, phrased for what the UI shows. */
sealed interface OffResult<out T> {
    data class Ok<T>(val value: T) : OffResult<T>
    data object NotFound : OffResult<Nothing>
    /** Our own limiter or the server said slow down; try again later. */
    data class Busy(val retryInSec: Int) : OffResult<Nothing>
    data object Offline : OffResult<Nothing>
    data class Failed(val message: String) : OffResult<Nothing>
}

/**
 * Open Food Facts client (https://openfoodfacts.github.io/openfoodfacts-server/api/).
 *
 * The API's limits are per IP address — 15 product reads and 10 searches a
 * minute — and many phones share one carrier IP, so the app stays well
 * under them: searches run only when the user submits (never per
 * keystroke), a local limiter caps both kinds of call, and responses go
 * through a small HTTP cache. Every request carries the
 * `AppName/Version (contact)` User-Agent the API asks for.
 *
 * Text search uses Search-a-licious, the full-text search OFF provides now
 * that the v2 API has none; products by barcode use the v2 product API.
 */
class OpenFoodFactsClient(
    cacheDir: File,
    private val userAgent: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val http = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "off_http"), 10L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    private val productLimiter = RateLimiter(maxEvents = 12, windowMs = 60_000)
    private val searchLimiter  = RateLimiter(maxEvents = 8, windowMs = 60_000)

    /** Product by barcode. A 12-digit UPC that isn't found is retried as EAN-13 ("0" + code). */
    suspend fun product(barcode: String): OffResult<FoodItemEntity> = withContext(Dispatchers.IO) {
        val first = fetchProduct(barcode)
        if (first is OffResult.NotFound && barcode.length == 12) fetchProduct("0$barcode") else first
    }

    suspend fun search(query: String): OffResult<List<FoodItemEntity>> = withContext(Dispatchers.IO) {
        val now = clock()
        if (!searchLimiter.tryAcquire(now)) return@withContext OffResult.Busy(searchLimiter.secondsUntilNext(now))
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page_size", "24")
            .addQueryParameter("fields", FIELDS)
            .build()
        get(url.toString()) { body -> OffResult.Ok(OffParser.parseSearch(body)) }
    }

    private fun fetchProduct(code: String): OffResult<FoodItemEntity> {
        val now = clock()
        if (!productLimiter.tryAcquire(now)) return OffResult.Busy(productLimiter.secondsUntilNext(now))
        return get("$PRODUCT_URL/$code?fields=$FIELDS") { body ->
            OffParser.parseProductResponse(body)?.let { OffResult.Ok(it) } ?: OffResult.NotFound
        }
    }

    private inline fun <T> get(url: String, parse: (String) -> OffResult<T>): OffResult<T> = try {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            when {
                resp.code == 404                     -> OffResult.NotFound
                resp.code == 429 || resp.code == 503 -> OffResult.Busy(60)
                !resp.isSuccessful                   -> OffResult.Failed("HTTP ${resp.code}")
                else                                 -> parse(resp.body?.string().orEmpty())
            }
        }
    } catch (e: IOException) {
        OffResult.Offline
    } catch (e: JSONException) {
        OffResult.Failed("Unexpected response")
    }

    companion object {
        private const val PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product"
        private const val SEARCH_URL  = "https://search.openfoodfacts.org/search"
        private const val FIELDS =
            "code,product_name,product_name_en,brands,nutriments,nutriscore_grade,nova_group,serving_size,serving_quantity"
    }
}

/** Sliding-window limiter: at most [maxEvents] in any [windowMs]. Thread-safe. */
class RateLimiter(private val maxEvents: Int, private val windowMs: Long) {
    private val events = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(now: Long): Boolean {
        drop(now)
        if (events.size >= maxEvents) return false
        events.addLast(now)
        return true
    }

    @Synchronized
    fun secondsUntilNext(now: Long): Int {
        drop(now)
        if (events.size < maxEvents) return 0
        return ((events.first() + windowMs - now + 999) / 1000).toInt()
    }

    private fun drop(now: Long) {
        while (events.isNotEmpty() && now - events.first() >= windowMs) events.removeFirst()
    }
}

/** Turns OFF JSON into [FoodItemEntity] rows (not yet stored). Pure; unit-tested. */
object OffParser {

    private const val KJ_PER_KCAL = 4.184f

    /** v2 product response: `{"status":1,"product":{…}}`. Null when not found or unusable. */
    fun parseProductResponse(body: String): FoodItemEntity? {
        val root = JSONObject(body)
        if (root.optInt("status", 0) != 1) return null
        val product = root.optJSONObject("product") ?: return null
        return parseProduct(product, fallbackCode = root.optString("code"))
    }

    /** Search-a-licious (`hits`) or legacy search (`products`). Drops unnamed products. */
    fun parseSearch(body: String): List<FoodItemEntity> {
        val root = JSONObject(body)
        val list = root.optJSONArray("hits") ?: root.optJSONArray("products") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i -> list.optJSONObject(i)?.let { parseProduct(it) } }
    }

    fun parseProduct(p: JSONObject, fallbackCode: String = ""): FoodItemEntity? {
        val code = p.optString("code").ifBlank { fallbackCode }.trim()
        // The UI is English, so the English name wins when the product has one.
        val name = text(p, "product_name_en") ?: text(p, "product_name") ?: return null
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        val kcal = num(n, "energy-kcal_100g")
            ?: num(n, "energy-kj_100g")?.div(KJ_PER_KCAL)
            ?: num(n, "energy_100g")?.div(KJ_PER_KCAL)          // "energy" is kJ
        return FoodItemEntity(
            barcode      = code.takeIf { it.isNotEmpty() && it.all(Char::isDigit) },
            source       = FoodItemEntity.SOURCE_OFF,
            name         = name,
            brand        = brand(p),
            servingSizeG = num(p, "serving_quantity")?.takeIf { it in 1f..2000f },
            servingLabel = text(p, "serving_size"),
            kcal100      = kcal,
            protein100   = num(n, "proteins_100g"),
            carbs100     = num(n, "carbohydrates_100g"),
            fat100       = num(n, "fat_100g"),
            fiber100     = num(n, "fiber_100g"),
            sugar100     = num(n, "sugars_100g"),
            salt100      = num(n, "salt_100g"),
            nutriScore   = p.optString("nutriscore_grade").lowercase().takeIf { it in setOf("a", "b", "c", "d", "e") },
            novaGroup    = p.optInt("nova_group", 0).takeIf { it in 1..4 },
        )
    }

    /** Plain string, or a per-language object (`{"en": …}`) as some search results return. */
    private fun text(o: JSONObject, key: String): String? {
        val v = o.opt(key)
        val s = when (v) {
            is String     -> v
            is JSONObject -> v.optString("en").ifBlank { v.keys().asSequence().firstOrNull()?.let(v::optString).orEmpty() }
            else          -> ""
        }
        return s.trim().takeIf { it.isNotEmpty() }
    }

    private fun brand(o: JSONObject): String? = when (val v = o.opt("brands")) {
        is String    -> v.split(',').firstOrNull()
        is JSONArray -> if (v.length() > 0) v.optString(0) else null
        else         -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** Number or numeric string; null when absent, NaN or negative. */
    private fun num(o: JSONObject, key: String): Float? =
        o.optDouble(key, Double.NaN).takeIf { !it.isNaN() && it >= 0 }?.toFloat()
}
