package com.scouty.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

@Serializable
data class RouteEnrichmentCatalog(
    @SerialName("routes_by_local_code")
    val routesByLocalCode: Map<String, RouteEnrichmentEntry> = emptyMap()
)

@Serializable
data class RouteEnrichmentEntry(
    @SerialName("local_code")
    val localCode: String? = null,
    @SerialName("display_title")
    val displayTitle: String? = null,
    val region: String? = null,
    val description: RouteDescription? = null,
    val image: RouteImage? = null,
    @SerialName("mn_data")
    val mnData: RouteMnData? = null
)

@Serializable
data class RouteDescription(
    @SerialName("text_ro")
    val textRo: String? = null,
    @SerialName("detail_level")
    val detailLevel: String? = null
)

@Serializable
data class RouteImage(
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("source_page_url")
    val sourcePageUrl: String? = null,
    val author: String? = null,
    val license: String? = null,
    @SerialName("license_url")
    val licenseUrl: String? = null,
    @SerialName("attribution_text")
    val attributionText: String? = null,
    val scope: String? = null
)

@Serializable
data class RouteMnData(
    @SerialName("difficulty_label")
    val difficultyLabel: String? = null,
    @SerialName("duration_text")
    val durationText: String? = null,
    @SerialName("distance_km")
    val distanceKm: Double? = null,
    @SerialName("ascent_m")
    val ascentM: Int? = null
)

data class RouteSearchSuggestion(
    val localCode: String,
    val entry: RouteEnrichmentEntry,
    val score: Int
)

object RouteEnrichmentRepository {
    private const val AssetName = "local_route_enriched_catalog.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedCatalog: RouteEnrichmentCatalog? = null

    suspend fun load(context: Context): RouteEnrichmentCatalog = withContext(Dispatchers.IO) {
        cachedCatalog?.let { return@withContext it }
        runCatching {
            context.assets.open(AssetName).use { input ->
                json.decodeFromString<RouteEnrichmentCatalog>(input.bufferedReader().readText())
            }
        }.onFailure { error ->
            Log.e("ScoutyRouteCatalog", "Failed to load $AssetName", error)
        }.getOrElse {
            RouteEnrichmentCatalog()
        }.also { cachedCatalog = it }
    }

    fun normalizeLocalCode(rawCode: String?): String? {
        if (rawCode.isNullOrBlank()) {
            return null
        }
        val parts = rawCode
            .split(";")
            .map { it.trim().uppercase().replace(" ", "") }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return null
        }
        return parts.joinToString(";")
    }

    fun findByLocalCode(
        catalog: RouteEnrichmentCatalog,
        rawCode: String?
    ): RouteEnrichmentEntry? {
        val normalized = normalizeLocalCode(rawCode) ?: return null
        return catalog.routesByLocalCode[normalized]
    }

    fun search(
        catalog: RouteEnrichmentCatalog,
        query: String,
        limit: Int = 8
    ): List<RouteSearchSuggestion> {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

        return catalog.routesByLocalCode
            .asSequence()
            .mapNotNull { (code, entry) ->
                val title = entry.displayTitle.orEmpty()
                val region = entry.region.orEmpty()
                val description = entry.description?.textRo.orEmpty()
                val normalizedCode = normalizeSearchText(code)
                val normalizedTitle = normalizeSearchText(title)
                val normalizedRegion = normalizeSearchText(region)
                val normalizedDescription = normalizeSearchText(description)
                val titleHaystack = normalizeSearchText("$code $title $region")
                val titleMatches = matchesQuery(titleHaystack, normalizedQuery, queryTokens)
                val descriptionMatches = matchesQuery(normalizedDescription, normalizedQuery, queryTokens)
                if (!titleMatches && !descriptionMatches) {
                    return@mapNotNull null
                }
                if (!hasUsableImage(entry)) {
                    return@mapNotNull null
                }

                val score = when {
                    normalizedCode == normalizedQuery -> 260
                    normalizedTitle == normalizedQuery -> 240
                    normalizedCode.startsWith(normalizedQuery) -> 210
                    normalizedTitle.startsWith(normalizedQuery) -> 190
                    normalizedQuery in normalizedTitle -> 170
                    normalizedQuery in normalizedRegion -> 125
                    titleMatches -> 120
                    else -> 60
                } + queryTokens.fold(0) { total, token ->
                    total + when {
                        token in normalizedTitle -> 10
                        token in normalizedRegion -> 4
                        token in normalizedDescription -> 1
                        else -> 0
                    }
                } + when (entry.image?.scope) {
                    "exact_route" -> 6
                    "route_landmark" -> 4
                    "region_fallback" -> 1
                    else -> 0
                } + when {
                    !entry.image?.thumbnailUrl.isNullOrBlank() -> 4
                    !entry.image?.imageUrl.isNullOrBlank() -> 2
                    else -> 0
                }

                RouteSearchSuggestion(
                    localCode = code,
                    entry = entry,
                    score = score
                )
            }
            .sortedWith(
                compareByDescending<RouteSearchSuggestion> { it.score }
                    .thenBy { it.entry.displayTitle.orEmpty() }
                    .thenBy { it.localCode }
            )
            .distinctBy { normalizeSearchText(it.entry.displayTitle ?: it.localCode) }
            .toList()
            .let { diversifySuggestionImages(it, limit) }
    }

    private fun normalizeSearchText(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return normalized
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
    }

    private fun matchesQuery(haystack: String, normalizedQuery: String, queryTokens: List<String>): Boolean {
        if (haystack.isBlank()) {
            return false
        }
        return normalizedQuery in haystack || queryTokens.all { haystack.contains(it) }
    }

    private fun hasUsableImage(entry: RouteEnrichmentEntry): Boolean =
        !entry.image?.thumbnailUrl.isNullOrBlank() || !entry.image?.imageUrl.isNullOrBlank()

    private fun diversifySuggestionImages(
        suggestions: List<RouteSearchSuggestion>,
        limit: Int
    ): List<RouteSearchSuggestion> {
        if (suggestions.size <= 1) {
            return suggestions.take(limit)
        }

        val selected = mutableListOf<RouteSearchSuggestion>()
        val seenImageSources = mutableSetOf<String>()

        suggestions.forEach { suggestion ->
            val imageSource = suggestion.entry.image?.sourcePageUrl
            if (imageSource.isNullOrBlank() || seenImageSources.add(imageSource)) {
                selected += suggestion
            }
            if (selected.size >= limit) {
                return selected
            }
        }

        suggestions.forEach { suggestion ->
            if (suggestion !in selected) {
                selected += suggestion
            }
            if (selected.size >= limit) {
                return selected
            }
        }

        return selected
    }
}
