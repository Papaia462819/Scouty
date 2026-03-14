package com.scouty.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class RouteGeometryIndex(
    @SerialName("routes_by_local_code")
    val routesByLocalCode: Map<String, RouteGeometryEntry> = emptyMap()
)

@Serializable
data class RouteGeometryEntry(
    @SerialName("local_code")
    val localCode: String,
    val center: RouteCenter,
    val bbox: RouteBounds,
    val segments: List<String> = emptyList(),
    @SerialName("point_count")
    val pointCount: Int = 0
)

@Serializable
data class RouteCenter(
    val lat: Double,
    val lon: Double
)

@Serializable
data class RouteBounds(
    @SerialName("min_lat")
    val minLat: Double,
    @SerialName("min_lon")
    val minLon: Double,
    @SerialName("max_lat")
    val maxLat: Double,
    @SerialName("max_lon")
    val maxLon: Double
)

data class RouteCoordinate(
    val lat: Double,
    val lon: Double
)

private const val MaxRenderableSegmentGapKm = 2.0
private const val EndpointJoinToleranceKm = 0.3
private const val MaxShortLeafSegmentKm = 1.2
private const val ShortLeafVsLongestLeafRatio = 0.5

object RouteGeometryRepository {
    private const val AssetName = "local_route_geometry_index.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedIndex: RouteGeometryIndex? = null

    suspend fun load(context: Context): RouteGeometryIndex = withContext(Dispatchers.IO) {
        cachedIndex?.let { return@withContext it }
        runCatching {
            context.assets.open(AssetName).use { input ->
                json.decodeFromString<RouteGeometryIndex>(input.bufferedReader().readText())
            }
        }.onFailure { error ->
            Log.e("ScoutyRouteGeometry", "Failed to load $AssetName", error)
        }.getOrElse {
            RouteGeometryIndex()
        }.also { cachedIndex = it }
    }

    fun findByLocalCode(
        index: RouteGeometryIndex,
        rawCode: String?
    ): RouteGeometryEntry? {
        val normalized = RouteEnrichmentRepository.normalizeLocalCode(rawCode) ?: return null
        return index.routesByLocalCode[normalized]
    }

    fun decodeSegments(entry: RouteGeometryEntry): List<List<RouteCoordinate>> =
        entry.segments.map(::decodePolyline)

    fun decodeRenderableSegments(entry: RouteGeometryEntry): List<List<RouteCoordinate>> =
        pruneShortLeafSegments(
            decodeSegments(entry).flatMap(::splitDiscontinuousSegment)
        )

    private fun decodePolyline(encoded: String): List<RouteCoordinate> {
        val coordinates = mutableListOf<RouteCoordinate>()
        var index = 0
        var lat = 0
        var lon = 0

        while (index < encoded.length) {
            lat += decodeValue(encoded, index).also { index = it.nextIndex }.value
            lon += decodeValue(encoded, index).also { index = it.nextIndex }.value
            coordinates += RouteCoordinate(
                lat = lat / 1e5,
                lon = lon / 1e5
            )
        }

        return coordinates
    }

    private fun splitDiscontinuousSegment(
        coordinates: List<RouteCoordinate>
    ): List<List<RouteCoordinate>> {
        if (coordinates.size < 2) return emptyList()

        val segments = mutableListOf<List<RouteCoordinate>>()
        var currentSegment = mutableListOf(coordinates.first())

        coordinates.drop(1).forEach { coordinate ->
            val previous = currentSegment.last()
            if (haversineKm(previous, coordinate) > MaxRenderableSegmentGapKm) {
                if (currentSegment.size >= 2) {
                    segments += currentSegment.toList()
                }
                currentSegment = mutableListOf(coordinate)
            } else {
                currentSegment += coordinate
            }
        }

        if (currentSegment.size >= 2) {
            segments += currentSegment.toList()
        }

        return segments
    }

    private fun pruneShortLeafSegments(
        segments: List<List<RouteCoordinate>>
    ): List<List<RouteCoordinate>> {
        if (segments.size < 3) return segments

        val endpointNodes = mutableListOf<RouteCoordinate>()
        val edges = segments.mapIndexed { index, segment ->
            SegmentEdge(
                index = index,
                startNode = assignEndpointNode(endpointNodes, segment.first()),
                endNode = assignEndpointNode(endpointNodes, segment.last()),
                lengthKm = segmentLengthKm(segment)
            )
        }

        if (edges.size < 3) return segments

        val degrees = IntArray(endpointNodes.size)
        edges.forEach { edge ->
            degrees[edge.startNode] += 1
            degrees[edge.endNode] += 1
        }

        val leafEdges = edges.filter { edge ->
            degrees[edge.startNode] == 1 || degrees[edge.endNode] == 1
        }
        if (leafEdges.size < 2) return segments

        val longestLeafKm = leafEdges.maxOf { it.lengthKm }
        val retainedIndexes = edges.filterNot { edge ->
            val isLeaf = degrees[edge.startNode] == 1 || degrees[edge.endNode] == 1
            isLeaf &&
                edge.lengthKm <= MaxShortLeafSegmentKm &&
                edge.lengthKm < longestLeafKm * ShortLeafVsLongestLeafRatio
        }.mapTo(linkedSetOf()) { it.index }

        return if (retainedIndexes.size >= 2 && retainedIndexes.size < segments.size) {
            segments.filterIndexed { index, _ -> index in retainedIndexes }
        } else {
            segments
        }
    }

    private fun assignEndpointNode(
        nodes: MutableList<RouteCoordinate>,
        coordinate: RouteCoordinate
    ): Int {
        nodes.forEachIndexed { index, node ->
            if (haversineKm(node, coordinate) <= EndpointJoinToleranceKm) {
                return index
            }
        }
        nodes += coordinate
        return nodes.lastIndex
    }

    private fun segmentLengthKm(segment: List<RouteCoordinate>): Double =
        segment.zipWithNext { start, end -> haversineKm(start, end) }.sum()

    private fun haversineKm(start: RouteCoordinate, end: RouteCoordinate): Double {
        val earthRadiusKm = 6371.0
        val lat1 = Math.toRadians(start.lat)
        val lat2 = Math.toRadians(end.lat)
        val deltaLat = Math.toRadians(end.lat - start.lat)
        val deltaLon = Math.toRadians(end.lon - start.lon)
        val a = sin(deltaLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2).let { it * it }
        return 2 * earthRadiusKm * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun decodeValue(encoded: String, startIndex: Int): DecodedValue {
        var index = startIndex
        var shift = 0
        var result = 0
        while (index < encoded.length) {
            val value = encoded[index++].code - 63
            result = result or ((value and 0x1F) shl shift)
            shift += 5
            if (value < 0x20) {
                break
            }
        }
        val delta = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        return DecodedValue(value = delta, nextIndex = index)
    }

    private data class DecodedValue(
        val value: Int,
        val nextIndex: Int
    )

    private data class SegmentEdge(
        val index: Int,
        val startNode: Int,
        val endNode: Int,
        val lengthKm: Double
    )
}
