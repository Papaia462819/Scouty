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
import org.maplibre.android.style.expressions.Expression.has
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
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
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
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import java.net.URI
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
    const val BASE_SOURCE_ID = "romania-base-source"
    const val BUCEGI_DEMO_SOURCE_ID = "bucegi-demo-source"
    const val REMOTE_TRAILS_SOURCE_ID = "remote-trails-source"
    const val REMOTE_WILDLIFE_SOURCE_ID = "remote-wildlife-source"
    const val REMOTE_ATTRACTIONS_SOURCE_ID = "remote-attractions-source"
    const val REMOTE_WATER_SOURCE_ID = "remote-water-source"
    const val LANDUSE_LAYER_ID = "landuse-layer"
    const val PARK_LAYER_ID = "park-layer"
    const val WATER_FILL_LAYER_ID = "water-fill-layer"
    const val WATERWAY_LAYER_ID = "waterway-layer"
    const val BUCEGI_WATERWAY_LAYER_ID = "waterway-layer-bucegi"
    const val WATER_LABELS_LAYER_ID = "water-labels-layer"
    const val WATER_LABELS_LINE_LAYER_ID = "water-labels-line-layer"
    const val BUCEGI_WATER_LABELS_LAYER_ID = "water-labels-layer-bucegi"
    const val BUCEGI_WATER_LABELS_LINE_LAYER_ID = "water-labels-line-layer-bucegi"
    const val ROAD_LAYER_ID = "road-layer"
    const val BUCEGI_ROAD_LAYER_ID = "road-layer-bucegi"
    const val HIKING_PATHS_LAYER_ID = "hiking-paths-layer"
    const val BUCEGI_HIKING_PATHS_LAYER_ID = "hiking-paths-layer-bucegi"
    const val CUSTOM_TRAILS_LAYER_ID = "custom-trails-layer"
    const val WILDLIFE_LAYER_ID = "wildlife-heatmap"
    const val WILDLIFE_SYMBOL_LAYER_ID = "wildlife-symbol-layer"
    const val ATTRACTIONS_LABELS_LAYER_ID = "attractions-labels-layer"
    const val WATER_POINT_SYMBOL_LAYER_ID = "water-point-symbol-layer"
    const val WATER_POINT_LABELS_LAYER_ID = "water-points-labels-layer"
    const val PEAK_SYMBOL_LAYER_ID = "peak-symbol-layer"
    const val BUCEGI_PEAK_SYMBOL_LAYER_ID = "peak-symbol-layer-bucegi"
    const val PEAK_LABELS_LAYER_ID = "peak-labels-layer"
    const val BUCEGI_PEAK_LABELS_LAYER_ID = "peak-labels-layer-bucegi"
    const val PLACE_LABELS_LAYER_ID = "place-labels-layer"
    const val BUCEGI_PLACE_LABELS_LAYER_ID = "place-labels-layer-bucegi"
    const val BOUNDARY_LAYER_ID = "boundary-layer"
    const val ATTRACTIONS_SYMBOL_LAYER_ID = "attractions-symbol-layer"

    private const val DemoMinZoom = 13.4f
    private const val OfflineTrailsAsset = "Trasee_Varfuri.geojson"
    private const val OfflineWildlifeAsset = "Pradatori.geojson"
    private const val OfflineAttractionsAsset = "Atractii.geojson"
    private const val OfflineWaterPointsAsset = "Izvoare_Adapost.geojson"

    private const val PeakIconId = "scouty-peak-icon"
    private const val WaterIconId = "scouty-water-icon"
    private const val AttractionIconId = "scouty-attraction-icon"
    private const val WildlifeIconId = "scouty-wildlife-icon"

    private val toggleGroups = mapOf(
        "trails" to listOf(CUSTOM_TRAILS_LAYER_ID),
        "peaks" to listOf(
            PEAK_SYMBOL_LAYER_ID,
            BUCEGI_PEAK_SYMBOL_LAYER_ID,
            PEAK_LABELS_LAYER_ID,
            BUCEGI_PEAK_LABELS_LAYER_ID
        ),
        "places" to listOf(PLACE_LABELS_LAYER_ID, BUCEGI_PLACE_LABELS_LAYER_ID),
        "wildlife" to listOf(WILDLIFE_LAYER_ID, WILDLIFE_SYMBOL_LAYER_ID),
        "attractions" to listOf(
            ATTRACTIONS_SYMBOL_LAYER_ID,
            ATTRACTIONS_LABELS_LAYER_ID
        ),
        "water" to listOf(
            WATER_FILL_LAYER_ID,
            WATERWAY_LAYER_ID,
            BUCEGI_WATERWAY_LAYER_ID,
            WATER_LABELS_LAYER_ID,
            WATER_LABELS_LINE_LAYER_ID,
            BUCEGI_WATER_LABELS_LAYER_ID,
            BUCEGI_WATER_LABELS_LINE_LAYER_ID,
            WATER_POINT_SYMBOL_LAYER_ID,
            WATER_POINT_LABELS_LAYER_ID
        )
    )

    fun createStyleBuilder(mapDataConfig: MapDataConfig): Style.Builder {
        val glyphsSnippet = mapDataConfig.glyphsUri()?.let { "\"glyphs\": \"$it\"," } ?: ""
        val styleJson = """
            {
              "version": 8,
              "name": "Scouty Offline",
              $glyphsSnippet
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

    fun installStyle(style: Style, mapDataConfig: MapDataConfig) {
        addBaseSources(style, mapDataConfig)
        addRemoteOverlaySources(style)
        ensurePointSymbolImages(style)
        addBaseLayers(style, mapDataConfig)
        addDemoHighDetailLayers(style, mapDataConfig)
        addRemoteOverlayLayers(style, mapDataConfig)
    }

    fun applyOverlayVisibility(style: Style, overlayState: MapOverlayState) {
        setGroupVisibility(style, "trails", overlayState.trails)
        setGroupVisibility(style, "peaks", overlayState.peaks)
        setGroupVisibility(style, "places", overlayState.places)
        setGroupVisibility(style, "wildlife", overlayState.wildlife)
        setGroupVisibility(style, "attractions", overlayState.attractions)
        setGroupVisibility(style, "water", overlayState.water)
    }

    fun trailQueryLayerIds(mapDataConfig: MapDataConfig): Array<String> {
        return arrayOf(CUSTOM_TRAILS_LAYER_ID)
    }

    fun hasWildlifeLayer(mapDataConfig: MapDataConfig): Boolean = true

    fun hasAttractionsLayer(mapDataConfig: MapDataConfig): Boolean = true

    fun hasWaterPointsLayer(mapDataConfig: MapDataConfig): Boolean = true

    private fun addBaseSources(style: Style, mapDataConfig: MapDataConfig) {
        mapDataConfig.baseSourceUri()?.let { sourceUri ->
            if (style.getSource(BASE_SOURCE_ID) == null) {
                style.addSource(VectorSource(BASE_SOURCE_ID, sourceUri))
            }
        }
        mapDataConfig.demoSourceUri()?.let { sourceUri ->
            if (style.getSource(BUCEGI_DEMO_SOURCE_ID) == null) {
                style.addSource(VectorSource(BUCEGI_DEMO_SOURCE_ID, sourceUri))
            }
        }
    }

    private fun addRemoteOverlaySources(style: Style) {
        if (style.getSource(REMOTE_TRAILS_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(REMOTE_TRAILS_SOURCE_ID, URI("asset://$OfflineTrailsAsset")))
        }
        if (style.getSource(REMOTE_WILDLIFE_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(REMOTE_WILDLIFE_SOURCE_ID, URI("asset://$OfflineWildlifeAsset")))
        }
        if (style.getSource(REMOTE_ATTRACTIONS_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(REMOTE_ATTRACTIONS_SOURCE_ID, URI("asset://$OfflineAttractionsAsset")))
        }
        if (style.getSource(REMOTE_WATER_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(REMOTE_WATER_SOURCE_ID, URI("asset://$OfflineWaterPointsAsset")))
        }
    }

    private fun addBaseLayers(style: Style, mapDataConfig: MapDataConfig) {
        addIfMissing(
            style,
            BackgroundLayer("scouty-background").withProperties(
                backgroundColor(Color.parseColor("#eef1e6"))
            )
        )

        addIfMissing(
            style,
            FillLayer(LANDUSE_LAYER_ID, BASE_SOURCE_ID).apply {
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
            FillLayer(PARK_LAYER_ID, BASE_SOURCE_ID).apply {
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
            FillLayer(WATER_FILL_LAYER_ID, BASE_SOURCE_ID).apply {
                sourceLayer = "water"
                setProperties(
                    fillColor(Color.parseColor("#7db7d8")),
                    fillOpacity(0.9f)
                )
            }
        )

        addIfMissing(
            style,
            LineLayer(WATERWAY_LAYER_ID, BASE_SOURCE_ID).apply {
                sourceLayer = "waterway"
                setProperties(
                    lineColor(Color.parseColor("#4d93c2")),
                    lineOpacity(0.9f),
                    lineWidth(1.4f)
                )
                setMinZoom(7f)
            }
        )

        if (mapDataConfig.hasLocalGlyphs) {
            addIfMissing(
                style,
                SymbolLayer(WATER_LABELS_LAYER_ID, BASE_SOURCE_ID).apply {
                    sourceLayer = "water_name"
                    setProperties(
                        textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.08f),
                                zoom(),
                                stop(7, 9.0f),
                                stop(10, 10.2f),
                                stop(13, 11.3f)
                            )
                        ),
                        textColor(Color.parseColor("#1d4f73")),
                        textHaloColor(Color.parseColor("#eef7fd")),
                        textHaloWidth(1f)
                    )
                    setMinZoom(7f)
                }
            )
            addIfMissing(
                style,
                SymbolLayer(WATER_LABELS_LINE_LAYER_ID, BASE_SOURCE_ID).apply {
                    sourceLayer = "water_name"
                    setProperties(
                        textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.08f),
                                zoom(),
                                stop(8, 9.0f),
                                stop(11, 10.0f),
                                stop(13, 11.0f)
                            )
                        ),
                        textColor(Color.parseColor("#25638d")),
                        textHaloColor(Color.parseColor("#eef7fd")),
                        textHaloWidth(1f),
                        symbolPlacement(Property.SYMBOL_PLACEMENT_LINE)
                    )
                    setMinZoom(8f)
                }
            )
        }

        addIfMissing(
            style,
            LineLayer(BOUNDARY_LAYER_ID, BASE_SOURCE_ID).apply {
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
            LineLayer(ROAD_LAYER_ID, BASE_SOURCE_ID).apply {
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
            SymbolLayer(PEAK_SYMBOL_LAYER_ID, BASE_SOURCE_ID).apply {
                sourceLayer = "mountain_peak"
                setProperties(
                    iconImage(PeakIconId),
                    iconSize(
                        interpolate(
                            exponential(1.18f),
                            zoom(),
                            stop(9.7, 0.7f),
                            stop(12, 0.9f),
                            stop(14, 1.06f)
                        )
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
                setMinZoom(9.5f)
            }
        )

        if (mapDataConfig.hasLocalGlyphs) {
            addIfMissing(
                style,
                SymbolLayer(PEAK_LABELS_LAYER_ID, BASE_SOURCE_ID).apply {
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
                SymbolLayer(PLACE_LABELS_LAYER_ID, BASE_SOURCE_ID).apply {
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
    }

    private fun addDemoHighDetailLayers(style: Style, mapDataConfig: MapDataConfig) {
        if (!mapDataConfig.hasDemoPack) {
            return
        }

        addIfMissing(
            style,
            LineLayer(BUCEGI_WATERWAY_LAYER_ID, BUCEGI_DEMO_SOURCE_ID).apply {
                sourceLayer = "waterway"
                setProperties(
                    lineColor(Color.parseColor("#4d93c2")),
                    lineOpacity(0.9f),
                    lineWidth(1.6f)
                )
                setMinZoom(DemoMinZoom)
            }
        )

        if (mapDataConfig.hasLocalGlyphs) {
            addIfMissing(
                style,
                SymbolLayer(BUCEGI_WATER_LABELS_LAYER_ID, BUCEGI_DEMO_SOURCE_ID).apply {
                    sourceLayer = "water_name"
                    setProperties(
                        textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.08f),
                                zoom(),
                                stop(13.5, 10.2f),
                                stop(15, 11.1f),
                                stop(16, 12.0f)
                            )
                        ),
                        textColor(Color.parseColor("#1d4f73")),
                        textHaloColor(Color.parseColor("#eef7fd")),
                        textHaloWidth(1f)
                    )
                    setMinZoom(DemoMinZoom)
                }
            )
            addIfMissing(
                style,
                SymbolLayer(BUCEGI_WATER_LABELS_LINE_LAYER_ID, BUCEGI_DEMO_SOURCE_ID).apply {
                    sourceLayer = "water_name"
                    setProperties(
                        textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.08f),
                                zoom(),
                                stop(13.5, 10.0f),
                                stop(15, 10.8f),
                                stop(16, 11.6f)
                            )
                        ),
                        textColor(Color.parseColor("#25638d")),
                        textHaloColor(Color.parseColor("#eef7fd")),
                        textHaloWidth(1f),
                        symbolPlacement(Property.SYMBOL_PLACEMENT_LINE)
                    )
                    setMinZoom(DemoMinZoom)
                }
            )
        }

        addIfMissing(
            style,
            LineLayer(BUCEGI_ROAD_LAYER_ID, BUCEGI_DEMO_SOURCE_ID).apply {
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
                    lineOpacity(0.62f),
                    lineWidth(1.5f)
                )
                setMinZoom(DemoMinZoom)
            }
        )

        addIfMissing(
            style,
            SymbolLayer(BUCEGI_PEAK_SYMBOL_LAYER_ID, BUCEGI_DEMO_SOURCE_ID).apply {
                sourceLayer = "mountain_peak"
                setProperties(
                    iconImage(PeakIconId),
                    iconSize(
                        interpolate(
                            exponential(1.18f),
                            zoom(),
                            stop(13.5, 0.9f),
                            stop(15, 1.04f),
                            stop(16, 1.16f)
                        )
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
                setMinZoom(DemoMinZoom)
            }
        )

        if (mapDataConfig.hasLocalGlyphs) {
            addIfMissing(
                style,
                SymbolLayer(BUCEGI_PEAK_LABELS_LAYER_ID, BUCEGI_DEMO_SOURCE_ID).apply {
                    sourceLayer = "mountain_peak"
                    setProperties(
                        textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                        textFont(arrayOf("Open Sans Semibold")),
                        textSize(
                            interpolate(
                                exponential(1.15f),
                                zoom(),
                                stop(13.5, 10.7f),
                                stop(15, 11.6f),
                                stop(16, 12.1f)
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
                    setMinZoom(DemoMinZoom)
                }
            )

            addIfMissing(
                style,
                SymbolLayer(BUCEGI_PLACE_LABELS_LAYER_ID, BUCEGI_DEMO_SOURCE_ID).apply {
                    sourceLayer = "place"
                    setProperties(
                        textField(coalesce(get("name:ro"), get("name"), get("name_en"))),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.1f),
                                zoom(),
                                stop(13.5, 10.4f),
                                stop(15, 11.2f),
                                stop(16, 12f)
                            )
                        ),
                        textColor(Color.parseColor("#40503d")),
                        textHaloColor(Color.parseColor("#eef1e6")),
                        textHaloWidth(1f)
                    )
                    setMinZoom(DemoMinZoom)
                }
            )
        }
    }

    private fun addRemoteOverlayLayers(style: Style, mapDataConfig: MapDataConfig) {
        addIfMissing(
            style,
            LineLayer(CUSTOM_TRAILS_LAYER_ID, REMOTE_TRAILS_SOURCE_ID).apply {
                setFilter(mnTrailFilter())
                setProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineColor(Color.parseColor("#141712")),
                    lineOpacity(0.88f),
                    lineWidth(
                        interpolate(
                            exponential(1.15f),
                            zoom(),
                            stop(7, 1.1f),
                            stop(10, 2.1f),
                            stop(13, 3.1f),
                            stop(15, 4.2f)
                        )
                    )
                )
                setMinZoom(7f)
            }
        )

        addIfMissing(
            style,
            HeatmapLayer(WILDLIFE_LAYER_ID, REMOTE_WILDLIFE_SOURCE_ID).apply {
                setProperties(
                    heatmapIntensity(
                        interpolate(
                            exponential(1.15f),
                            zoom(),
                            stop(5, 0.6f),
                            stop(8, 0.9f),
                            stop(11, 1.3f),
                            stop(14, 1.7f)
                        )
                    ),
                    heatmapRadius(
                        interpolate(
                            exponential(1.2f),
                            zoom(),
                            stop(5, 10f),
                            stop(8, 16f),
                            stop(11, 22f),
                            stop(14, 28f)
                        )
                    ),
                    heatmapOpacity(
                        interpolate(
                            exponential(1.0f),
                            zoom(),
                            stop(5, 0.6f),
                            stop(10, 0.78f),
                            stop(14, 0.5f)
                        )
                    ),
                    heatmapColor(
                        interpolate(
                            exponential(1.0f),
                            heatmapDensity(),
                            stop(0, rgba(0.0, 0.0, 0.0, 0.0)),
                            stop(0.2, rgba(249.0, 115.0, 22.0, 0.25)),
                            stop(0.45, rgba(239.0, 68.0, 68.0, 0.4)),
                            stop(0.7, rgba(220.0, 38.0, 38.0, 0.65)),
                            stop(1.0, rgba(127.0, 29.0, 29.0, 0.82))
                        )
                    )
                )
                setMinZoom(4f)
            }
        )
        addIfMissing(
            style,
            SymbolLayer(WILDLIFE_SYMBOL_LAYER_ID, REMOTE_WILDLIFE_SOURCE_ID).apply {
                setProperties(
                    iconImage(WildlifeIconId),
                    iconSize(
                        interpolate(
                            exponential(1.12f),
                            zoom(),
                            stop(11, 0.88f),
                            stop(13, 1.04f),
                            stop(15, 1.2f)
                        )
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
                setMinZoom(12f)
            }
        )

        addIfMissing(
            style,
            SymbolLayer(ATTRACTIONS_SYMBOL_LAYER_ID, REMOTE_ATTRACTIONS_SOURCE_ID).apply {
                setFilter(offlineAttractionFilter())
                setProperties(
                    iconImage(AttractionIconId),
                    iconSize(
                        interpolate(
                            exponential(1.1f),
                            zoom(),
                            stop(9, 0.84f),
                            stop(12, 0.98f),
                            stop(15, 1.14f)
                        )
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
                setMinZoom(8.5f)
            }
        )
        if (mapDataConfig.hasLocalGlyphs) {
            addIfMissing(
                style,
                SymbolLayer(ATTRACTIONS_LABELS_LAYER_ID, REMOTE_ATTRACTIONS_SOURCE_ID).apply {
                    setFilter(offlineAttractionFilter())
                    setProperties(
                        textField(namedPointLabelExpression()),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(
                            interpolate(
                                exponential(1.08f),
                                zoom(),
                                stop(9, 9.6f),
                                stop(12, 10.6f),
                                stop(15, 11.4f)
                            )
                        ),
                        textColor(Color.parseColor("#134e4a")),
                        textHaloColor(Color.parseColor("#f0fdf4")),
                        textHaloWidth(1f),
                        textOffset(arrayOf(0f, 1.0f))
                    )
                    setMinZoom(10f)
                }
            )
        }

        addIfMissing(
            style,
            SymbolLayer(WATER_POINT_SYMBOL_LAYER_ID, REMOTE_WATER_SOURCE_ID).apply {
                setFilter(offlineWaterPointFilter())
                setProperties(
                    iconImage(WaterIconId),
                    iconSize(
                        interpolate(
                            exponential(1.1f),
                            zoom(),
                            stop(9, 0.82f),
                            stop(12, 0.96f),
                            stop(15, 1.12f)
                        )
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
                setMinZoom(9f)
            }
        )
        if (mapDataConfig.hasLocalGlyphs) {
            addIfMissing(
                style,
                SymbolLayer(WATER_POINT_LABELS_LAYER_ID, REMOTE_WATER_SOURCE_ID).apply {
                    setFilter(offlineWaterPointFilter())
                    setProperties(
                        textField(namedPointLabelExpression()),
                        textFont(arrayOf("Open Sans Regular")),
                        textSize(10.2f),
                        textColor(Color.parseColor("#0f3d54")),
                        textHaloColor(Color.parseColor("#eff6ff")),
                        textHaloWidth(1f),
                        textOffset(arrayOf(0f, 1.0f))
                    )
                    setMinZoom(10f)
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

    private fun mnTrailFilter() =
        any(
            has("ref:MN"),
            has("ref_mn"),
            has("mn_code")
        )

    private fun offlineAttractionFilter() =
        any(
            eq(get("tourism"), literal("attraction")),
            eq(get("tourism"), literal("viewpoint")),
            eq(get("tourism"), literal("museum")),
            eq(get("tourism"), literal("gallery")),
            eq(get("historic"), literal("monument")),
            eq(get("historic"), literal("memorial")),
            eq(get("historic"), literal("ruins")),
            eq(get("historic"), literal("castle")),
            eq(get("amenity"), literal("monastery")),
            eq(get("amenity"), literal("place_of_worship")),
            eq(get("leisure"), literal("park"))
        )

    private fun offlineWaterPointFilter() =
        any(
            eq(get("amenity"), literal("drinking_water")),
            eq(get("drinking_water"), literal("yes")),
            eq(get("natural"), literal("spring"))
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
        val size = 64f
        val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = badgeFill
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = badgeStroke
            strokeWidth = 2.6f
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
