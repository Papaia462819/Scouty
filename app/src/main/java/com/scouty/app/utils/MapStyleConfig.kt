package com.scouty.app.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.any
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.exponential
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.heatmapDensity
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.rgba
import org.maplibre.android.style.expressions.Expression.rgb
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.heatmapColor
import org.maplibre.android.style.layers.PropertyFactory.heatmapIntensity
import org.maplibre.android.style.layers.PropertyFactory.heatmapOpacity
import org.maplibre.android.style.layers.PropertyFactory.heatmapRadius
import org.maplibre.android.style.layers.PropertyFactory.heatmapWeight
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloBlur
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.VectorSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class MapOverlayState(
    val trails: Boolean = true,
    val peaks: Boolean = true,
    val places: Boolean = true,
    val wildlife: Boolean = false,
    val attractions: Boolean = true,
    val water: Boolean = true
)

object MapStyleConfig {
    const val LANDUSE_LAYER_ID = "landuse-layer"
    const val PARK_LAYER_ID = "park-layer"
    const val WATER_FILL_LAYER_ID = "water-fill-layer"
    const val WATERWAY_LAYER_ID = "waterway-layer"
    const val ROAD_LAYER_ID = "road-layer"
    const val HIKING_PATHS_LAYER_ID = "hiking-paths-layer"
    const val CUSTOM_TRAILS_LAYER_ID = "custom-trails-layer"
    const val WILDLIFE_LAYER_ID = "wildlife-heatmap"
    const val WILDLIFE_SYMBOL_LAYER_ID = "wildlife-symbol-layer"
    const val ATTRACTIONS_LABELS_LAYER_ID = "attractions-labels-layer"
    const val WATER_POINT_SYMBOL_LAYER_ID = "water-point-symbol-layer"
    const val WATER_POINT_LABELS_LAYER_ID = "water-points-labels-layer"
    const val PEAK_SYMBOL_LAYER_ID = "peak-symbol-layer"
    const val PEAK_LABELS_LAYER_ID = "peak-labels-layer"
    const val PLACE_LABELS_LAYER_ID = "place-labels-layer"
    const val BOUNDARY_LAYER_ID = "boundary-layer"
    const val ATTRACTIONS_SYMBOL_LAYER_ID = "attractions-symbol-layer"

    private const val PeakIconId = "scouty-peak-icon"
    private const val WaterIconId = "scouty-water-icon"
    private const val AttractionIconId = "scouty-attraction-icon"
    private const val WildlifeIconId = "scouty-wildlife-icon"

    private val toggleGroups = mapOf(
        "trails" to listOf(HIKING_PATHS_LAYER_ID, CUSTOM_TRAILS_LAYER_ID),
        "peaks" to listOf(PEAK_SYMBOL_LAYER_ID, PEAK_LABELS_LAYER_ID),
        "places" to listOf(PLACE_LABELS_LAYER_ID),
        "wildlife" to listOf(WILDLIFE_LAYER_ID, WILDLIFE_SYMBOL_LAYER_ID),
        "attractions" to listOf(
            ATTRACTIONS_SYMBOL_LAYER_ID,
            ATTRACTIONS_LABELS_LAYER_ID
        ),
        "water" to listOf(
            WATER_FILL_LAYER_ID,
            WATERWAY_LAYER_ID,
            WATER_POINT_SYMBOL_LAYER_ID,
            WATER_POINT_LABELS_LAYER_ID
        )
    )

    fun createStyleBuilder(): Style.Builder {
        val styleJson = """
            {
              "version": 8,
              "name": "Scouty Remote",
              "glyphs": "${MapboxConfig.glyphsUrl()}",
              "sources": {},
              "layers": [
                {
                  "id": "background-base",
                  "type": "background",
                  "paint": {
                    "background-color": "#eef1e6"
                  }
                }
              ]
            }
        """.trimIndent()
        return Style.Builder().fromJson(styleJson)
    }

    fun installStyle(style: Style) {
        addBaseSources(style)
        addOptionalSources(style)
        ensurePointSymbolImages(style)
        addBaseLayers(style)
        addOptionalLayers(style)
    }

    fun applyOverlayVisibility(style: Style, overlayState: MapOverlayState) {
        setGroupVisibility(style, "trails", overlayState.trails)
        setGroupVisibility(style, "peaks", overlayState.peaks)
        setGroupVisibility(style, "places", overlayState.places)
        setGroupVisibility(style, "wildlife", overlayState.wildlife)
        setGroupVisibility(style, "attractions", overlayState.attractions)
        setGroupVisibility(style, "water", overlayState.water)
    }

    fun trailQueryLayerIds(): Array<String> {
        val ids = mutableListOf(CUSTOM_TRAILS_LAYER_ID, HIKING_PATHS_LAYER_ID)
        return ids.toTypedArray()
    }

    fun hasWildlifeLayer(): Boolean = MapboxConfig.customWildlife() != null

    fun hasAttractionsLayer(): Boolean = MapboxConfig.customAttractions() != null

    fun hasWaterPointsLayer(): Boolean = MapboxConfig.customWater() != null

    private fun addBaseSources(style: Style) {
        if (style.getSource(MapboxConfig.BASE_SOURCE_ID) == null) {
            style.addSource(VectorSource(MapboxConfig.BASE_SOURCE_ID, MapboxConfig.baseTilesetUrl()))
        }
    }

    private fun addOptionalSources(style: Style) {
        MapboxConfig.customTrails()?.let { config ->
            if (style.getSource(MapboxConfig.CUSTOM_TRAILS_SOURCE_ID) == null) {
                style.addSource(VectorSource(MapboxConfig.CUSTOM_TRAILS_SOURCE_ID, MapboxConfig.tileJsonUrl(config.tilesetId)))
            }
        }
        MapboxConfig.customWildlife()?.let { config ->
            if (style.getSource(MapboxConfig.CUSTOM_WILDLIFE_SOURCE_ID) == null) {
                style.addSource(VectorSource(MapboxConfig.CUSTOM_WILDLIFE_SOURCE_ID, MapboxConfig.tileJsonUrl(config.tilesetId)))
            }
        }
        MapboxConfig.customAttractions()?.let { config ->
            if (style.getSource(MapboxConfig.CUSTOM_ATTRACTIONS_SOURCE_ID) == null) {
                style.addSource(VectorSource(MapboxConfig.CUSTOM_ATTRACTIONS_SOURCE_ID, MapboxConfig.tileJsonUrl(config.tilesetId)))
            }
        }
        MapboxConfig.customWater()?.let { config ->
            if (style.getSource(MapboxConfig.CUSTOM_WATER_SOURCE_ID) == null) {
                style.addSource(VectorSource(MapboxConfig.CUSTOM_WATER_SOURCE_ID, MapboxConfig.tileJsonUrl(config.tilesetId)))
            }
        }
    }

    private fun addBaseLayers(style: Style) {
        addIfMissing(
            style,
            BackgroundLayer("scouty-background").withProperties(
                backgroundColor(Color.parseColor("#eef1e6"))
            )
        )

        addIfMissing(
            style,
            FillLayer(LANDUSE_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "landcover"
                setFilter(
                    any(
                        eq(get("class"), literal("forest")),
                        eq(get("class"), literal("wood")),
                        eq(get("class"), literal("grass"))
                    )
                )
                setProperties(
                    fillColor(Color.parseColor("#d9e7cc")),
                    fillOpacity(0.65f)
                )
                setMinZoom(4f)
            }
        )

        addIfMissing(
            style,
            FillLayer(PARK_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "park"
                setProperties(
                    fillColor(Color.parseColor("#cfe4b6")),
                    fillOpacity(0.6f)
                )
                setMinZoom(6f)
            }
        )

        addIfMissing(
            style,
            FillLayer(WATER_FILL_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "water"
                setProperties(
                    fillColor(Color.parseColor("#7db7d8")),
                    fillOpacity(0.9f)
                )
            }
        )

        addIfMissing(
            style,
            LineLayer(WATERWAY_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "waterway"
                setProperties(
                    lineColor(Color.parseColor("#4d93c2")),
                    lineOpacity(0.9f),
                    lineWidth(1.4f)
                )
                setMinZoom(7f)
            }
        )

        addIfMissing(
            style,
            LineLayer(BOUNDARY_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "boundary"
                setProperties(
                    lineColor(Color.parseColor("#7d8474")),
                    lineOpacity(0.45f),
                    lineWidth(1.2f)
                )
                setMinZoom(4f)
            }
        )

        addIfMissing(
            style,
            LineLayer(ROAD_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "transportation"
                setFilter(
                    any(
                        eq(get("class"), literal("motorway")),
                        eq(get("class"), literal("trunk")),
                        eq(get("class"), literal("primary")),
                        eq(get("class"), literal("secondary")),
                        eq(get("class"), literal("tertiary"))
                    )
                )
                setProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineColor(Color.parseColor("#ffffff")),
                    lineOpacity(0.55f),
                    lineWidth(1.2f)
                )
                setMinZoom(6f)
            }
        )

        addIfMissing(
            style,
            LineLayer(HIKING_PATHS_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "transportation"
                setFilter(
                    any(
                        eq(get("class"), literal("path")),
                        eq(get("class"), literal("track")),
                        eq(get("subclass"), literal("path")),
                        eq(get("subclass"), literal("track"))
                    )
                )
                setProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineColor(
                        match(
                            get("surface"),
                            rgb(53.0, 77.0, 42.0),
                            stop("gravel", rgb(119.0, 92.0, 67.0)),
                            stop("ground", rgb(89.0, 70.0, 52.0)),
                            stop("dirt", rgb(124.0, 87.0, 56.0))
                        )
                    ),
                    lineOpacity(0.95f),
                    lineWidth(2.0f)
                )
                setMinZoom(9f)
            }
        )

        addIfMissing(
            style,
            SymbolLayer(PEAK_SYMBOL_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "mountain_peak"
                setProperties(
                    iconImage(PeakIconId),
                    iconSize(
                        interpolate(
                            exponential(1.18f),
                            zoom(),
                            stop(9.7, 0.52f),
                            stop(12, 0.62f),
                            stop(14, 0.74f)
                        )
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
                setMinZoom(9.5f)
            }
        )

        addIfMissing(
            style,
            SymbolLayer(PEAK_LABELS_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "mountain_peak"
                setProperties(
                    textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                    textFont(arrayOf("Open Sans Semibold")),
                    textSize(
                        interpolate(
                            exponential(1.15f),
                            zoom(),
                            stop(10.7, 9.3f),
                            stop(13, 10.7f),
                            stop(15, 11.9f)
                        )
                    ),
                    textColor(Color.parseColor("#2d2b27")),
                    textHaloColor(Color.parseColor("#f4f0e8")),
                    textHaloWidth(1f),
                    textHaloBlur(0.5f),
                    textOffset(arrayOf(0f, 1.0f)),
                    textAllowOverlap(true),
                    textIgnorePlacement(true)
                )
                setMinZoom(10.7f)
            }
        )

        addIfMissing(
            style,
            SymbolLayer(PLACE_LABELS_LAYER_ID, MapboxConfig.BASE_SOURCE_ID).apply {
                sourceLayer = "place"
                setProperties(
                    textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                    textFont(arrayOf("Open Sans Regular")),
                    textSize(
                        interpolate(
                            exponential(1.1f),
                            zoom(),
                            stop(7, 9.2f),
                            stop(10, 10.6f),
                            stop(13, 12f)
                        )
                    ),
                    textColor(Color.parseColor("#40503d")),
                    textHaloColor(Color.parseColor("#eef1e6")),
                    textHaloWidth(1f)
                )
                setMinZoom(6.7f)
            }
        )
    }

    private fun addOptionalLayers(style: Style) {
        MapboxConfig.customTrails()?.let { config ->
            addIfMissing(
                style,
                LineLayer(CUSTOM_TRAILS_LAYER_ID, MapboxConfig.CUSTOM_TRAILS_SOURCE_ID).apply {
                    sourceLayer = config.sourceLayer
                    setProperties(
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineColor(
                            match(
                                get("difficulty"),
                                rgb(42.0, 63.0, 28.0),
                                stop("EASY", rgb(33.0, 150.0, 83.0)),
                                stop("MEDIUM", rgb(251.0, 191.0, 36.0)),
                                stop("HARD", rgb(249.0, 115.0, 22.0)),
                                stop("EXPERT", rgb(220.0, 38.0, 38.0))
                            )
                        ),
                        lineWidth(2.8f),
                        lineOpacity(0.95f)
                    )
                    setMinZoom(8f)
                }
            )
        }

        MapboxConfig.customWildlife()?.let { config ->
            addIfMissing(
                style,
                HeatmapLayer(WILDLIFE_LAYER_ID, MapboxConfig.CUSTOM_WILDLIFE_SOURCE_ID).apply {
                    sourceLayer = config.sourceLayer
                    setProperties(
                        heatmapWeight(
                            interpolate(
                                exponential(1.14f),
                                coalesce(get("year"), literal(2021)),
                                stop(2018, 0.14f),
                                stop(2021, 0.32f),
                                stop(2024, 0.68f),
                                stop(2026, 1.0f)
                            )
                        ),
                        heatmapIntensity(
                            interpolate(
                                exponential(1.12f),
                                zoom(),
                                stop(6, 0.7f),
                                stop(9, 1.0f),
                                stop(11.5, 1.22f)
                            )
                        ),
                        heatmapRadius(
                            interpolate(
                                exponential(1.16f),
                                zoom(),
                                stop(6, 16f),
                                stop(8, 26f),
                                stop(10, 38f),
                                stop(12, 52f)
                            )
                        ),
                        heatmapColor(
                            interpolate(
                                exponential(1f),
                                heatmapDensity(),
                                stop(0, rgba(0, 0, 0, 0f)),
                                stop(0.18, rgba(250, 204, 21, 0.28f)),
                                stop(0.38, rgba(249, 115, 22, 0.52f)),
                                stop(0.62, rgba(239, 68, 68, 0.74f)),
                                stop(1, rgba(127, 29, 29, 0.92f))
                            )
                        ),
                        heatmapOpacity(
                            interpolate(
                                exponential(1.08f),
                                zoom(),
                                stop(6, 0.84f),
                                stop(10.5, 0.72f),
                                stop(12.2, 0.18f),
                                stop(13, 0f)
                            )
                        )
                    )
                    setMinZoom(6.5f)
                }
            )
            addIfMissing(
                style,
                SymbolLayer(WILDLIFE_SYMBOL_LAYER_ID, MapboxConfig.CUSTOM_WILDLIFE_SOURCE_ID).apply {
                    sourceLayer = config.sourceLayer
                    setProperties(
                        iconImage(WildlifeIconId),
                        iconSize(
                            interpolate(
                                exponential(1.18f),
                                zoom(),
                                stop(11.4, 0.5f),
                                stop(13, 0.62f),
                                stop(15, 0.74f)
                            )
                        ),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
                    setMinZoom(11.2f)
                }
            )
        }

        MapboxConfig.customAttractions()?.let { config ->
            addIfMissing(
                style,
                SymbolLayer(ATTRACTIONS_SYMBOL_LAYER_ID, MapboxConfig.CUSTOM_ATTRACTIONS_SOURCE_ID).apply {
                    sourceLayer = config.sourceLayer
                    setFilter(nonPeakAttractionFilter())
                    setProperties(
                        iconImage(AttractionIconId),
                        iconSize(
                            interpolate(
                                exponential(1.18f),
                                zoom(),
                                stop(10.6, 0.5f),
                                stop(12.8, 0.6f),
                                stop(15, 0.72f)
                            )
                        ),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
                    setMinZoom(10.3f)
                }
            )
            addIfMissing(
                style,
                SymbolLayer(ATTRACTIONS_LABELS_LAYER_ID, MapboxConfig.CUSTOM_ATTRACTIONS_SOURCE_ID).apply {
                    sourceLayer = config.sourceLayer
                    setFilter(nonPeakAttractionFilter())
                    setProperties(
                        textField(namedPointLabelExpression()),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.15f),
                                zoom(),
                                stop(12.0, 8.8f),
                                stop(14, 10.0f),
                                stop(15.5, 11.2f)
                            )
                        ),
                        textColor(Color.parseColor("#14532d")),
                        textHaloColor(Color.parseColor("#f4fbf6")),
                        textHaloWidth(1.1f),
                        textHaloBlur(0.4f),
                        textOffset(arrayOf(0f, 1.05f)),
                        textAllowOverlap(true),
                        textIgnorePlacement(true)
                    )
                    setMinZoom(11.9f)
                }
            )
        }

        MapboxConfig.customWater()?.let { config ->
            addIfMissing(
                style,
                SymbolLayer(WATER_POINT_SYMBOL_LAYER_ID, MapboxConfig.CUSTOM_WATER_SOURCE_ID).apply {
                    sourceLayer = config.sourceLayer
                    setProperties(
                        iconImage(WaterIconId),
                        iconSize(
                            interpolate(
                                exponential(1.18f),
                                zoom(),
                                stop(10.8, 0.47f),
                                stop(13, 0.56f),
                                stop(15, 0.66f)
                            )
                        ),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
                    setMinZoom(10.4f)
                }
            )
            addIfMissing(
                style,
                SymbolLayer(WATER_POINT_LABELS_LAYER_ID, MapboxConfig.CUSTOM_WATER_SOURCE_ID).apply {
                    sourceLayer = config.sourceLayer
                    setProperties(
                        textField(namedPointLabelExpression()),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.15f),
                                zoom(),
                                stop(12.2, 8.6f),
                                stop(14, 9.8f),
                                stop(15.5, 10.9f)
                            )
                        ),
                        textColor(Color.parseColor("#0c4a6e")),
                        textHaloColor(Color.parseColor("#f0f9ff")),
                        textHaloWidth(1.1f),
                        textHaloBlur(0.4f),
                        textOffset(arrayOf(0f, 1.05f)),
                        textAllowOverlap(true),
                        textIgnorePlacement(true)
                    )
                    setMinZoom(12.1f)
                }
            )
        }
    }

    private fun setGroupVisibility(style: Style, group: String, visible: Boolean) {
        val desiredVisibility = if (visible) Property.VISIBLE else Property.NONE
        toggleGroups[group]
            .orEmpty()
            .forEach { layerId ->
                style.getLayer(layerId)?.setProperties(visibility(desiredVisibility))
            }
    }

    private fun namedPointLabelExpression() =
        coalesce(get("name:ro"), get("name"), get("description"))

    private fun nonPeakAttractionFilter() =
        match(
            get("natural"),
            literal(true),
            stop("peak", literal(false))
        )

    private fun ensurePointSymbolImages(style: Style) {
        style.addImage(PeakIconId, createPeakIcon())
        style.addImage(WaterIconId, createWaterIcon())
        style.addImage(AttractionIconId, createAttractionIcon())
        style.addImage(WildlifeIconId, createWildlifeIcon())
    }

    private fun createPeakIcon(): Bitmap =
        createBadgeIcon(
            badgeFill = Color.parseColor("#f5f4ec"),
            badgeStroke = Color.parseColor("#5b6470")
        ) { canvas, size ->
            val mountainBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#64748b")
            }
            val mountainFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#334155")
            }
            val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#f8fafc")
            }
            val backPath = Path().apply {
                moveTo(size * 0.18f, size * 0.66f)
                lineTo(size * 0.36f, size * 0.36f)
                lineTo(size * 0.54f, size * 0.66f)
                close()
            }
            val frontPath = Path().apply {
                moveTo(size * 0.3f, size * 0.68f)
                lineTo(size * 0.52f, size * 0.24f)
                lineTo(size * 0.76f, size * 0.68f)
                close()
            }
            val snowPath = Path().apply {
                moveTo(size * 0.46f, size * 0.37f)
                lineTo(size * 0.52f, size * 0.24f)
                lineTo(size * 0.59f, size * 0.38f)
                lineTo(size * 0.53f, size * 0.42f)
                close()
            }
            canvas.drawPath(backPath, mountainBack)
            canvas.drawPath(frontPath, mountainFront)
            canvas.drawPath(snowPath, snowPaint)
        }

    private fun createWaterIcon(): Bitmap =
        createBadgeIcon(
            badgeFill = Color.parseColor("#eff6ff"),
            badgeStroke = Color.parseColor("#7dd3fc")
        ) { canvas, size ->
            val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#0284c7")
            }
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#bae6fd")
            }
            val dropPath = Path().apply {
                moveTo(size * 0.5f, size * 0.18f)
                cubicTo(size * 0.7f, size * 0.38f, size * 0.76f, size * 0.52f, size * 0.76f, size * 0.62f)
                cubicTo(size * 0.76f, size * 0.78f, size * 0.64f, size * 0.88f, size * 0.5f, size * 0.88f)
                cubicTo(size * 0.36f, size * 0.88f, size * 0.24f, size * 0.78f, size * 0.24f, size * 0.62f)
                cubicTo(size * 0.24f, size * 0.52f, size * 0.3f, size * 0.38f, size * 0.5f, size * 0.18f)
                close()
            }
            val highlightPath = Path().apply {
                moveTo(size * 0.44f, size * 0.32f)
                cubicTo(size * 0.34f, size * 0.44f, size * 0.32f, size * 0.56f, size * 0.38f, size * 0.66f)
                cubicTo(size * 0.42f, size * 0.72f, size * 0.48f, size * 0.78f, size * 0.54f, size * 0.8f)
                lineTo(size * 0.5f, size * 0.84f)
                cubicTo(size * 0.38f, size * 0.8f, size * 0.3f, size * 0.7f, size * 0.3f, size * 0.58f)
                cubicTo(size * 0.3f, size * 0.5f, size * 0.34f, size * 0.42f, size * 0.44f, size * 0.32f)
                close()
            }
            canvas.drawPath(dropPath, dropPaint)
            canvas.drawPath(highlightPath, highlightPaint)
        }

    private fun createAttractionIcon(): Bitmap =
        createBadgeIcon(
            badgeFill = Color.parseColor("#f4fbf6"),
            badgeStroke = Color.parseColor("#86efac")
        ) { canvas, size ->
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#0f766e")
            }
            canvas.drawPath(
                starPath(
                    centerX = size * 0.5f,
                    centerY = size * 0.52f,
                    outerRadius = size * 0.23f,
                    innerRadius = size * 0.1f
                ),
                starPaint
            )
        }

    private fun createWildlifeIcon(): Bitmap =
        createBadgeIcon(
            badgeFill = Color.parseColor("#fff7ed"),
            badgeStroke = Color.parseColor("#fb923c")
        ) { canvas, size ->
            val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#991b1b")
            }
            canvas.drawCircle(size * 0.5f, size * 0.63f, size * 0.11f, padPaint)
            canvas.drawCircle(size * 0.35f, size * 0.42f, size * 0.055f, padPaint)
            canvas.drawCircle(size * 0.45f, size * 0.32f, size * 0.05f, padPaint)
            canvas.drawCircle(size * 0.56f, size * 0.32f, size * 0.05f, padPaint)
            canvas.drawCircle(size * 0.66f, size * 0.42f, size * 0.055f, padPaint)
        }

    private fun createBadgeIcon(
        badgeFill: Int,
        badgeStroke: Int,
        contentDrawer: (Canvas, Float) -> Unit
    ): Bitmap {
        val size = 48f
        val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = badgeFill
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = badgeStroke
            strokeWidth = 2.2f
        }
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.34f, fillPaint)
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.34f, strokePaint)
        contentDrawer(canvas, size)
        return bitmap
    }

    private fun starPath(
        centerX: Float,
        centerY: Float,
        outerRadius: Float,
        innerRadius: Float
    ): Path {
        val path = Path()
        repeat(10) { index ->
            val angle = (-PI / 2) + index * (PI / 5)
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val x = centerX + (cos(angle) * radius).toFloat()
            val y = centerY + (sin(angle) * radius).toFloat()
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return path
    }

    private fun addIfMissing(style: Style, layer: org.maplibre.android.style.layers.Layer) {
        if (style.getLayer(layer.id) == null) {
            style.addLayer(layer)
        }
    }
}
