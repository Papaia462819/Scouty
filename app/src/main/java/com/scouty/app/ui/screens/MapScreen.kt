package com.scouty.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scouty.app.R
import com.scouty.app.ui.models.ActiveTrail
import com.scouty.app.data.RouteEnrichmentCatalog
import com.scouty.app.data.RouteImage
import com.scouty.app.data.RouteEnrichmentRepository
import com.scouty.app.data.RouteBounds
import com.scouty.app.data.RouteCoordinate
import com.scouty.app.data.RouteGeometryEntry
import com.scouty.app.data.RouteGeometryIndex
import com.scouty.app.data.RouteGeometryRepository
import com.scouty.app.data.RouteSearchSuggestion
import com.scouty.app.ui.components.RouteRemoteImage
import com.scouty.app.ui.MainViewModel
import com.scouty.app.ui.models.HomeStatus
import com.scouty.app.utils.MapLifecycleManager
import com.scouty.app.utils.MapOverlayState
import com.scouty.app.utils.MapStyleConfig
import com.scouty.app.utils.MapboxConfig
import com.scouty.app.utils.TrailDifficulty
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val RomaniaCenter = LatLng(45.9432, 24.9668)
private const val DefaultRomaniaZoom = 7.2
private const val GpsFocusZoom = 11.5
private const val ActiveTrailZoom = 13.2
private const val RomaniaBoundsMinLat = 43.59703
private const val RomaniaBoundsMaxLat = 48.28633
private const val RomaniaBoundsMinLon = 20.24181
private const val RomaniaBoundsMaxLon = 30.27896
private val RomaniaCameraBounds = LatLngBounds.from(
    RomaniaBoundsMaxLat,
    RomaniaBoundsMaxLon,
    RomaniaBoundsMinLat,
    RomaniaBoundsMinLon
)
private val RouteSheetPeekHeight = 56.dp
private const val RomaniaFallbackFocusKey = "fallback:romania"
private const val SelectedRouteSourceId = "selected-route-source"
private const val SelectedRouteLayerId = "selected-route-layer"

private data class SelectedTrailDetails(
    val name: String,
    val difficulty: TrailDifficulty,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val elevationGain: Int,
    val estimatedDuration: String,
    val selectionToken: Long = System.currentTimeMillis(),
    val localCode: String? = null,
    val region: String? = null,
    val descriptionRo: String? = null,
    val imageUrl: String? = null,
    val imageAttribution: String? = null,
    val imageLicense: String? = null,
    val imageSourcePageUrl: String? = null,
    val imageScope: String? = null,
    val highlightSegments: List<List<RouteCoordinate>> = emptyList(),
    val highlightBounds: RouteBounds? = null
)

private data class LayerToggleSpec(
    val label: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

private data class CameraFocusTarget(
    val key: String,
    val center: LatLng,
    val zoom: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    status: HomeStatus,
    contentPadding: PaddingValues,
    viewModel: MainViewModel? = null,
    openActiveTrailToken: Long? = null
) {
    val context = LocalContext.current
    val sheetState = rememberStandardBottomSheetState(skipHiddenState = true)
    val sheetScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val routeCatalog by produceState(initialValue = RouteEnrichmentCatalog(), context) {
        value = RouteEnrichmentRepository.load(context)
    }
    val routeGeometryIndex by produceState(initialValue = RouteGeometryIndex(), context) {
        value = RouteGeometryRepository.load(context)
    }
    val defaultSelection = remember {
        SelectedTrailDetails(
            name = "Mountain Trail",
            difficulty = TrailDifficulty.MEDIUM,
            latitude = RomaniaCenter.latitude,
            longitude = RomaniaCenter.longitude,
            distanceKm = 0.0,
            elevationGain = 0,
            estimatedDuration = "--"
        )
    }
    var selectedTrail by remember { mutableStateOf(defaultSelection) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var consumedActiveTrailOpenToken by remember { mutableStateOf<Long?>(null) }

    remember { MapLibre.getInstance(context) }

    var searchText by rememberSaveable { mutableStateOf("") }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchHistory = remember {
        mutableStateListOf("Vârful Caraiman", "Cabana Omu", "Cascada Urlătoarea", "Creasta Cocoșului")
    }
    var liveSuggestions by remember { mutableStateOf<List<RouteSearchSuggestion>>(emptyList()) }

    LaunchedEffect(searchText, routeCatalog) {
        val query = searchText.trim()
        if (query.length < 2) {
            liveSuggestions = emptyList()
            return@LaunchedEffect
        }

        // Search is local, but running it off the main thread keeps typing responsive.
        delay(120)
        liveSuggestions = withContext(Dispatchers.Default) {
            RouteEnrichmentRepository.search(routeCatalog, query, limit = 10)
        }
    }

    var showLayerMenu by remember { mutableStateOf(false) }
    var showTrails by rememberSaveable { mutableStateOf(true) }
    var showPeaks by rememberSaveable { mutableStateOf(true) }
    var showPlaces by rememberSaveable { mutableStateOf(true) }
    var showWater by rememberSaveable { mutableStateOf(true) }
    var showWildlife by rememberSaveable { mutableStateOf(false) }
    var showAttractions by rememberSaveable { mutableStateOf(true) }

    val overlayState = remember(
        showTrails,
        showPeaks,
        showPlaces,
        showWater,
        showWildlife,
        showAttractions
    ) {
        MapOverlayState(
            trails = showTrails,
            peaks = showPeaks,
            places = showPlaces,
            water = showWater,
            wildlife = showWildlife,
            attractions = showAttractions
        )
    }

    val toggleItems = remember(
        showTrails,
        showPeaks,
        showPlaces,
        showWater,
        showWildlife,
        showAttractions
    ) {
        buildList {
            add(LayerToggleSpec(context.getString(R.string.overlay_trails), showTrails) { showTrails = it })
            add(LayerToggleSpec(context.getString(R.string.overlay_peaks), showPeaks) { showPeaks = it })
            add(LayerToggleSpec(context.getString(R.string.overlay_places), showPlaces) { showPlaces = it })
            add(LayerToggleSpec(context.getString(R.string.overlay_water_points), showWater) { showWater = it })
            if (MapStyleConfig.hasWildlifeLayer()) {
                add(LayerToggleSpec(context.getString(R.string.overlay_wildlife_risk), showWildlife) { showWildlife = it })
            }
            if (MapStyleConfig.hasAttractionsLayer()) {
                add(LayerToggleSpec(context.getString(R.string.overlay_attractions), showAttractions) { showAttractions = it })
            }
        }
    }

    fun selectSuggestion(suggestion: RouteSearchSuggestion) {
        val geometry = RouteGeometryRepository.findByLocalCode(routeGeometryIndex, suggestion.localCode)
        selectedTrail = buildSelectedTrailFromSearch(suggestion, geometry)
        suggestion.entry.displayTitle?.let { title ->
            searchText = title
            if (!searchHistory.contains(title)) {
                searchHistory.add(0, title)
            }
        }
        showBottomSheet = true
        isSearchExpanded = false
        showLayerMenu = false
    }

    LaunchedEffect(showBottomSheet, selectedTrail.selectionToken) {
        if (showBottomSheet) {
            try {
                snapshotFlow { sheetState.hasExpandedState }.first { it }
                sheetState.expand()
            } catch (error: Throwable) {
                Log.w("ScoutyMap", "Failed to expand route sheet", error)
            }
        }
    }

    LaunchedEffect(openActiveTrailToken, status.activeTrail) {
        val activeTrail = status.activeTrail ?: return@LaunchedEffect
        if (openActiveTrailToken == null) return@LaunchedEffect
        if (openActiveTrailToken == consumedActiveTrailOpenToken) return@LaunchedEffect

        selectedTrail = buildSelectedTrailFromActiveTrail(activeTrail, routeCatalog, routeGeometryIndex)
        showBottomSheet = true
        isSearchExpanded = false
        showLayerMenu = false
        consumedActiveTrailOpenToken = openActiveTrailToken
    }

    if (!MapboxConfig.isConfigured) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.map_missing_config_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.map_missing_config_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = sheetScaffoldState,
            sheetPeekHeight = if (showBottomSheet) RouteSheetPeekHeight else 0.dp,
            sheetSwipeEnabled = showBottomSheet,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetDragHandle = if (showBottomSheet) {
                { BottomSheetDefaults.DragHandle() }
            } else {
                null
            },
            containerColor = Color.Transparent,
            sheetContent = {
                if (showBottomSheet) {
                    TrailDetailContent(
                        trail = selectedTrail,
                        onSetTrail = { date ->
                        viewModel?.setActiveTrail(
                            name = selectedTrail.name,
                            date = date,
                            lat = selectedTrail.latitude,
                            lon = selectedTrail.longitude,
                            difficulty = selectedTrail.difficulty.name,
                            distanceKm = selectedTrail.distanceKm,
                            elevationGain = selectedTrail.elevationGain,
                            estimatedDuration = selectedTrail.estimatedDuration,
                            imageUrl = selectedTrail.imageUrl,
                            localCode = selectedTrail.localCode,
                            region = selectedTrail.region,
                            descriptionRo = selectedTrail.descriptionRo,
                            imageAttribution = selectedTrail.imageAttribution,
                            imageLicense = selectedTrail.imageLicense,
                            imageSourcePageUrl = selectedTrail.imageSourcePageUrl,
                            imageScope = selectedTrail.imageScope
                        )
                        showBottomSheet = false
                    }
                )
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            MapLibreView(
                status = status,
                routeCatalog = routeCatalog,
                routeGeometryIndex = routeGeometryIndex,
                selectedRouteCode = selectedTrail.localCode,
                selectedRouteSegments = selectedTrail.highlightSegments,
                selectedRouteBounds = selectedTrail.highlightBounds,
                selectedRouteCenter = LatLng(selectedTrail.latitude, selectedTrail.longitude),
                selectedRouteToken = selectedTrail.selectionToken,
                overlayState = overlayState,
                onTrailClick = { selection ->
                    selectedTrail = selection
                    showBottomSheet = true
                },
                modifier = Modifier.fillMaxSize()
            )

            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchText,
                        onQueryChange = { searchText = it },
                        onSearch = { query ->
                            liveSuggestions.firstOrNull()?.let(::selectSuggestion)
                                ?: run {
                                    if (query.isNotBlank() && !searchHistory.contains(query)) {
                                        searchHistory.add(0, query)
                                    }
                                    isSearchExpanded = false
                                }
                        },
                        expanded = isSearchExpanded,
                        onExpandedChange = { isSearchExpanded = it },
                        placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (isSearchExpanded) {
                                IconButton(onClick = {
                                    if (searchText.isNotEmpty()) searchText = "" else isSearchExpanded = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            } else {
                                IconButton(onClick = { showLayerMenu = !showLayerMenu }) {
                                    Icon(
                                        Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )
                },
                expanded = isSearchExpanded,
                onExpandedChange = { isSearchExpanded = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isSearchExpanded) 0.dp else 16.dp)
                    .fillMaxWidth(if (isSearchExpanded) 1f else 0.92f),
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (searchText.length >= 2) {
                        items(liveSuggestions) { suggestion ->
                            RouteSuggestionItem(
                                suggestion = suggestion,
                                onClick = { selectSuggestion(suggestion) },
                                showPreviewImage = false
                            )
                        }
                    } else {
                        items(searchHistory) { historyItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchText = historyItem
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(text = historyItem)
                            }
                        }
                    }

                    if (searchText.length >= 2 && liveSuggestions.isEmpty()) {
                        item {
                            Text(
                                text = "Nu am gasit trasee pentru \"$searchText\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (showLayerMenu && !isSearchExpanded) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp)
                        .width(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        toggleItems.forEach { toggle ->
                            LayerToggleRow(toggle.label, toggle.checked, toggle.onCheckedChange)
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrailDetailContent(trail: SelectedTrailDetails, onSetTrail: (Calendar) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var showDatePicker by rememberSaveable(trail.localCode) { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(trail.localCode) { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    DifficultyBadge(trail.difficulty)
                }
            }

            item {
                Text(
                    text = trail.region ?: "Romania",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            trail.imageUrl?.let { imageUrl ->
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RouteRemoteImage(
                                imageUrl = imageUrl,
                                contentDescription = trail.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(214.dp),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val imageMeta = listOfNotNull(
                                    trail.imageScope?.let(::imageScopeLabel),
                                    trail.imageLicense
                                ).joinToString(" · ")
                                if (imageMeta.isNotBlank()) {
                                    Text(
                                        text = imageMeta,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                trail.imageAttribution?.takeIf { it.isNotBlank() }?.let { attribution ->
                                    Text(
                                        text = attribution,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                trail.imageSourcePageUrl?.let { sourceUrl ->
                                    TextButton(
                                        onClick = {
                                            runCatching { uriHandler.openUri(sourceUrl) }
                                                .onFailure { error ->
                                                    Log.w("ScoutyMap", "Failed to open image source $sourceUrl", error)
                                                }
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Vezi sursa si licenta foto")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            trail.descriptionRo?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Despre traseu",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.animateContentSize()
                            )
                            if (description.length > 220) {
                                TextButton(
                                    onClick = { descriptionExpanded = !descriptionExpanded },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (descriptionExpanded) "Restrange descrierea" else "Citeste mai mult")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoChip(
                            icon = Icons.Default.Route,
                            label = formatDistance(trail.distanceKm),
                            modifier = Modifier.weight(1f)
                        )
                        InfoChip(
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            label = formatElevation(trail.elevationGain),
                            modifier = Modifier.weight(1f)
                        )
                        InfoChip(
                            icon = Icons.Default.Timer,
                            label = compactDurationChipLabel(trail.estimatedDuration),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = buildString {
                            trail.localCode?.let {
                                append(it)
                                append(" · ")
                            }
                            append(String.format("Lat %.5f, Lon %.5f", trail.latitude, trail.longitude))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = { showDatePicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Seteaza traseul")
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                            }
                            onSetTrail(cal)
                            showDatePicker = false
                        }
                    ) {
                        Text("Confirm")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
private fun RouteSuggestionItem(
    suggestion: RouteSearchSuggestion,
    onClick: () -> Unit,
    showPreviewImage: Boolean = true
) {
    val entry = suggestion.entry
    val distance = entry.mnData?.distanceKm?.let(::formatDistance).orEmpty()
    val duration = entry.mnData?.durationText.orEmpty()
    val metadata = listOfNotNull(
        entry.region?.takeIf { it.isNotBlank() },
        suggestion.localCode
    ).joinToString(" · ")
    val stats = listOf(duration, distance).filter { it.isNotBlank() }.joinToString(" · ")
    val difficulty = resolveDifficulty(
        feature = null,
        lengthKm = entry.mnData?.distanceKm ?: 0.0,
        elevationGain = entry.mnData?.ascentM ?: 0,
        difficultyLabel = entry.mnData?.difficultyLabel
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val suggestionImage = preferredSuggestionImageUrl(entry.image)
            if (showPreviewImage && suggestionImage != null) {
                RouteRemoteImage(
                    imageUrl = suggestionImage,
                    contentDescription = entry.displayTitle ?: suggestion.localCode,
                    modifier = Modifier
                        .width(76.dp)
                        .height(76.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(if (showPreviewImage) 76.dp else 44.dp)
                        .height(if (showPreviewImage) 76.dp else 44.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.displayTitle ?: suggestion.localCode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (stats.isNotBlank()) {
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            DifficultyBadge(difficulty = difficulty)
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: TrailDifficulty) {
    val color = when (difficulty) {
        TrailDifficulty.EASY -> Color(0xFF2ECC71)
        TrailDifficulty.MEDIUM -> Color(0xFFF1C40F)
        TrailDifficulty.HARD -> Color(0xFFE67E22)
        TrailDifficulty.EXPERT -> Color(0xFFC0392B)
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = difficulty.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun compactDurationChipLabel(duration: String): String =
    duration.trim().replace("\\s+h$".toRegex(RegexOption.IGNORE_CASE), "")

@Composable
private fun LayerToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MapLibreView(
    status: HomeStatus,
    routeCatalog: RouteEnrichmentCatalog,
    routeGeometryIndex: RouteGeometryIndex,
    selectedRouteCode: String?,
    selectedRouteSegments: List<List<RouteCoordinate>>,
    selectedRouteBounds: RouteBounds?,
    selectedRouteCenter: LatLng,
    selectedRouteToken: Long?,
    overlayState: MapOverlayState,
    onTrailClick: (SelectedTrailDetails) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOverlayState by rememberUpdatedState(overlayState)
    val currentOnTrailClick by rememberUpdatedState(onTrailClick)
    val currentRouteCatalog by rememberUpdatedState(routeCatalog)
    val currentRouteGeometryIndex by rememberUpdatedState(routeGeometryIndex)
    val currentSelectedRouteCode by rememberUpdatedState(selectedRouteCode)
    val currentSelectedRouteSegments by rememberUpdatedState(selectedRouteSegments)
    val currentSelectedRouteBounds by rememberUpdatedState(selectedRouteBounds)
    val currentSelectedRouteCenter by rememberUpdatedState(selectedRouteCenter)
    val currentSelectedRouteToken by rememberUpdatedState(selectedRouteToken)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycleManager = remember { MapLifecycleManager(mapView) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var lastFocusedTrail by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycle) {
        lifecycle.addObserver(lifecycleManager)
        onDispose { lifecycle.removeObserver(lifecycleManager) }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    mapLibreMap = map
                    map.setLatLngBoundsForCameraTarget(RomaniaCameraBounds)
                    map.setMinZoomPreference(DefaultRomaniaZoom)
                    map.setMaxZoomPreference(15.5)
                    if (map.style == null) {
                        mapView.addOnDidFailLoadingMapListener { errorMessage ->
                            Log.e("ScoutyMap", "MapLibre failed to load: $errorMessage")
                        }
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(RomaniaCenter, DefaultRomaniaZoom))
                        map.setStyle(MapStyleConfig.createStyleBuilder()) { style ->
                            MapStyleConfig.installStyle(style)
                            ensureSelectedRouteLayers(style)
                            MapStyleConfig.applyOverlayVisibility(style, currentOverlayState)
                            logBaseSourceDiagnostics(style)
                            enableLocationComponent(map, style, context)

                            map.addOnMapClickListener { tappedPoint ->
                                handleMapTap(map, tappedPoint, currentRouteCatalog)?.let { selection ->
                                    currentOnTrailClick(selection)
                                    true
                                } ?: false
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier,
        update = {
            val map = mapLibreMap ?: return@AndroidView
            map.style?.let { style ->
                ensureSelectedRouteLayers(style)
                MapStyleConfig.applyOverlayVisibility(style, currentOverlayState)
                val selectedGeometry = RouteGeometryRepository.findByLocalCode(
                    currentRouteGeometryIndex,
                    currentSelectedRouteCode
                )
                val selectedSegments = currentSelectedRouteSegments.ifEmpty {
                    selectedGeometry?.let(RouteGeometryRepository::decodeRenderableSegments).orEmpty()
                }
                val selectedBounds = currentSelectedRouteBounds ?: selectedGeometry?.bbox
                updateSelectedRouteHighlight(style, selectedSegments)

                val hasSelectedRoute = currentSelectedRouteCode != null || selectedSegments.isNotEmpty()
                val selectedRouteFocus = if (hasSelectedRoute) {
                    CameraFocusTarget(
                        key = "selected:${currentSelectedRouteCode ?: "custom"}:${currentSelectedRouteToken ?: 0L}",
                        center = clampToRomaniaBounds(currentSelectedRouteCenter),
                        zoom = selectedBounds?.let(::zoomForRouteBounds) ?: ActiveTrailZoom
                    )
                } else {
                    null
                }
                if (!hasSelectedRoute && lastFocusedTrail?.startsWith("selected:") == true) {
                    lastFocusedTrail = null
                }

                if (selectedRouteFocus != null && selectedRouteFocus.key != lastFocusedTrail) {
                    focusRoute(map, selectedBounds, currentSelectedRouteCenter)
                    lastFocusedTrail = selectedRouteFocus.key
                    return@AndroidView
                }
                if (selectedRouteFocus != null) {
                    return@AndroidView
                }
            }

            val activeTrailFocus = status.activeTrail?.let {
                CameraFocusTarget(
                    key = "${it.name}:${it.latitude}:${it.longitude}",
                    center = clampToRomaniaBounds(LatLng(it.latitude, it.longitude)),
                    zoom = ActiveTrailZoom
                )
            }
            if (activeTrailFocus != null && activeTrailFocus.key != lastFocusedTrail) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        activeTrailFocus.center,
                        activeTrailFocus.zoom
                    )
                )
                lastFocusedTrail = activeTrailFocus.key
            } else if (status.activeTrail == null) {
                resolveGpsFocusTarget(status)?.let { focusTarget ->
                    val shouldMoveCamera =
                        lastFocusedTrail == null ||
                            (lastFocusedTrail == RomaniaFallbackFocusKey &&
                                focusTarget.key != RomaniaFallbackFocusKey)
                    if (shouldMoveCamera) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                focusTarget.center,
                                focusTarget.zoom
                            )
                        )
                        lastFocusedTrail = focusTarget.key
                    }
                }
            }
        }
    )
}

private fun resolveGpsFocusTarget(status: HomeStatus): CameraFocusTarget? {
    if (!status.gpsFixed) {
        return null
    }
    val lat = status.latitude ?: return null
    val lon = status.longitude ?: return null
    return if (isInsideRomaniaTileset(lat, lon)) {
        CameraFocusTarget(
            key = "gps:$lat:$lon",
            center = clampToRomaniaBounds(LatLng(lat, lon)),
            zoom = GpsFocusZoom
        )
    } else {
        Log.w(
            "ScoutyMap",
            "GPS position $lat,$lon is outside Romania tileset bounds; keeping default Romania view"
        )
        CameraFocusTarget(
            key = RomaniaFallbackFocusKey,
            center = RomaniaCenter,
            zoom = DefaultRomaniaZoom
        )
    }
}

private fun isInsideRomaniaTileset(latitude: Double, longitude: Double): Boolean =
    latitude in RomaniaBoundsMinLat..RomaniaBoundsMaxLat &&
        longitude in RomaniaBoundsMinLon..RomaniaBoundsMaxLon

private fun logBaseSourceDiagnostics(style: Style) {
    val baseSource = style.getSourceAs<VectorSource>(MapboxConfig.BASE_SOURCE_ID) ?: return
    runCatching {
        val water = baseSource.querySourceFeatures(arrayOf("water"), null).size
        val place = baseSource.querySourceFeatures(arrayOf("place"), null).size
        val transportation = baseSource.querySourceFeatures(arrayOf("transportation"), null).size
        Log.d(
            "ScoutyMap",
            "Base source features loaded water=$water place=$place transportation=$transportation"
        )
    }.onFailure { error ->
        Log.e("ScoutyMap", "Base source query failed", error)
    }
}

private fun handleMapTap(
    map: MapLibreMap,
    tappedPoint: LatLng,
    routeCatalog: RouteEnrichmentCatalog
): SelectedTrailDetails? {
    val screenPoint = map.projection.toScreenLocation(tappedPoint)
    val features = map.queryRenderedFeatures(screenPoint, *MapStyleConfig.trailQueryLayerIds())
    val feature = features.firstOrNull { !featureRouteCode(it).isNullOrBlank() } ?: features.firstOrNull() ?: return null
    val routeCode = featureRouteCode(feature)
    val enrichment = RouteEnrichmentRepository.findByLocalCode(routeCatalog, routeCode)

    val coordinateSegments = extractCoordinateSegments(feature.geometry())
    val coordinates = coordinateSegments.flatten()
    val highlightSegments = coordinateSegments
        .filter { it.size >= 2 }
        .map { segment ->
            segment.map { point -> RouteCoordinate(lat = point.latitude(), lon = point.longitude()) }
        }
    val fallbackPoint = coordinates.getOrNull(coordinates.size / 2) ?: Point.fromLngLat(tappedPoint.longitude, tappedPoint.latitude)
    val enrichedDistanceKm = enrichment?.mnData?.distanceKm
    val lengthKm = enrichedDistanceKm ?: calculatePathDistanceKm(coordinateSegments)
    val enrichedGain = enrichment?.mnData?.ascentM
    val elevationGain = enrichedGain ?: estimateElevationGain(feature)
    val difficulty = resolveDifficulty(
        feature = feature,
        lengthKm = lengthKm,
        elevationGain = elevationGain,
        difficultyLabel = enrichment?.mnData?.difficultyLabel
    )
    val duration = enrichment?.mnData?.durationText ?: estimateDuration(lengthKm, elevationGain)
    val trailName = enrichment?.displayTitle ?: featureString(feature, "name:ro", "name", "name_en", "ref")
        ?: when (featureString(feature, "class")) {
            "path", "track" -> "Mountain trail"
            else -> "Trail segment"
        }

    return SelectedTrailDetails(
        name = trailName,
        difficulty = difficulty,
        latitude = fallbackPoint.latitude(),
        longitude = fallbackPoint.longitude(),
        distanceKm = lengthKm,
        elevationGain = elevationGain,
        estimatedDuration = duration,
        localCode = routeCode,
        region = enrichment?.region,
        descriptionRo = enrichment?.description?.textRo,
        imageUrl = preferredDetailImageUrl(enrichment?.image),
        imageAttribution = enrichment?.image?.attributionText,
        imageLicense = enrichment?.image?.license,
        imageSourcePageUrl = enrichment?.image?.sourcePageUrl,
        imageScope = enrichment?.image?.scope,
        highlightSegments = highlightSegments,
        highlightBounds = routeBoundsForSegments(highlightSegments)
    )
}

private fun featureRouteCode(feature: Feature): String? =
    featureString(feature, "ref:MN", "ref_mn", "mn_code")

private fun extractCoordinateSegments(geometry: Geometry?): List<List<Point>> =
    when (geometry) {
        is LineString -> listOf(geometry.coordinates())
        is MultiLineString -> geometry.lineStrings().map { it.coordinates() }
        is Point -> listOf(listOf(geometry))
        else -> emptyList()
    }

private fun featureString(feature: Feature, vararg keys: String): String? {
    keys.forEach { key ->
        if (feature.hasProperty(key)) {
            val value = runCatching { feature.getStringProperty(key) }.getOrNull()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
    }
    return null
}

private fun resolveDifficulty(
    feature: Feature?,
    lengthKm: Double,
    elevationGain: Int,
    difficultyLabel: String? = null
): TrailDifficulty {
    difficultyLabel?.lowercase()?.let { label ->
        when {
            "expert" in label -> return TrailDifficulty.EXPERT
            "dificil" in label || "hard" in label -> return TrailDifficulty.HARD
            "mediu" in label || "medium" in label -> return TrailDifficulty.MEDIUM
            "usor" in label || "ușor" in label || "easy" in label -> return TrailDifficulty.EASY
        }
    }

    feature?.let {
        featureString(it, "difficulty")?.uppercase()?.let { difficulty ->
            return runCatching { TrailDifficulty.valueOf(difficulty) }.getOrElse { TrailDifficulty.MEDIUM }
        }
    }

    feature?.let {
        featureString(it, "sac_scale")?.lowercase()?.let { scale ->
            return when {
                "demanding_mountain_hiking" in scale -> TrailDifficulty.HARD
                "alpine_hiking" in scale -> TrailDifficulty.EXPERT
                "mountain_hiking" in scale -> TrailDifficulty.MEDIUM
                else -> TrailDifficulty.EASY
            }
        }
    }

    return when {
        elevationGain >= 1400 || lengthKm >= 24 -> TrailDifficulty.EXPERT
        elevationGain >= 900 || lengthKm >= 16 -> TrailDifficulty.HARD
        elevationGain >= 350 || lengthKm >= 8 -> TrailDifficulty.MEDIUM
        else -> TrailDifficulty.EASY
    }
}

private fun calculatePathDistanceKm(coordinateSegments: List<List<Point>>): Double =
    coordinateSegments.sumOf { segment ->
        if (segment.size < 2) {
            0.0
        } else {
            segment.zipWithNext { a, b ->
                haversineKm(a.latitude(), a.longitude(), b.latitude(), b.longitude())
            }.sum()
        }
    }

private fun estimateElevationGain(feature: Feature): Int {
    val keys = listOf("elevation_gain", "elevationGain", "gain", "ascent", "climb", "uphill")
    keys.forEach { key ->
        if (feature.hasProperty(key)) {
            val numericValue = runCatching { feature.getNumberProperty(key).toDouble() }.getOrNull()
                ?: runCatching { feature.getStringProperty(key).toDouble() }.getOrNull()
            if (numericValue != null) {
                return numericValue.roundToInt().coerceAtLeast(0)
            }
        }
    }
    return 0
}

private fun estimateDuration(lengthKm: Double, elevationGain: Int): String {
    if (lengthKm <= 0.0) return "--"
    val durationHours = lengthKm / 4.2 + elevationGain / 500.0
    val totalMinutes = (durationHours * 60).roundToInt().coerceAtLeast(30)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0) "~${hours}h" else "~${hours}h ${minutes}m"
}

private fun formatDistance(distanceKm: Double): String =
    if (distanceKm <= 0.0) "--" else String.format("%.1f km", distanceKm)

private fun formatElevation(elevationGain: Int): String =
    if (elevationGain <= 0) "--" else "+${elevationGain} m"

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
}

private fun buildSelectedTrailFromSearch(
    suggestion: RouteSearchSuggestion,
    geometry: RouteGeometryEntry?
): SelectedTrailDetails {
    val entry = suggestion.entry
    val highlightSegments = geometry?.let(RouteGeometryRepository::decodeRenderableSegments).orEmpty()
    val highlightBounds = routeBoundsForSegments(highlightSegments) ?: geometry?.bbox
    val distanceKm = entry.mnData?.distanceKm ?: geometry?.let(::estimateRouteLengthKm).orEmptyDouble()
    val elevationGain = entry.mnData?.ascentM ?: 0
    val duration = entry.mnData?.durationText ?: estimateDuration(distanceKm, elevationGain)
    val centerLat = highlightBounds?.let { (it.minLat + it.maxLat) / 2 } ?: geometry?.center?.lat
    val centerLon = highlightBounds?.let { (it.minLon + it.maxLon) / 2 } ?: geometry?.center?.lon
    return SelectedTrailDetails(
        name = entry.displayTitle ?: suggestion.localCode,
        difficulty = resolveDifficulty(
            feature = null,
            lengthKm = distanceKm,
            elevationGain = elevationGain,
            difficultyLabel = entry.mnData?.difficultyLabel
        ),
        latitude = centerLat ?: RomaniaCenter.latitude,
        longitude = centerLon ?: RomaniaCenter.longitude,
        distanceKm = distanceKm,
        elevationGain = elevationGain,
        estimatedDuration = duration,
        localCode = suggestion.localCode,
        region = entry.region,
        descriptionRo = entry.description?.textRo,
        imageUrl = preferredDetailImageUrl(entry.image),
        imageAttribution = entry.image?.attributionText,
        imageLicense = entry.image?.license,
        imageSourcePageUrl = entry.image?.sourcePageUrl,
        imageScope = entry.image?.scope,
        highlightSegments = highlightSegments,
        highlightBounds = highlightBounds
    )
}

private fun buildSelectedTrailFromActiveTrail(
    activeTrail: ActiveTrail,
    routeCatalog: RouteEnrichmentCatalog,
    routeGeometryIndex: RouteGeometryIndex
): SelectedTrailDetails {
    val geometry = RouteGeometryRepository.findByLocalCode(routeGeometryIndex, activeTrail.localCode)
    val entry = RouteEnrichmentRepository.findByLocalCode(routeCatalog, activeTrail.localCode)
    val highlightSegments = geometry?.let(RouteGeometryRepository::decodeRenderableSegments).orEmpty()
    val highlightBounds = routeBoundsForSegments(highlightSegments) ?: geometry?.bbox

    val distanceKm = activeTrail.distanceKm.takeIf { it > 0.0 }
        ?: entry?.mnData?.distanceKm
        ?: geometry?.let(::estimateRouteLengthKm)
        ?: 0.0
    val elevationGain = activeTrail.elevationGain.takeIf { it > 0 }
        ?: entry?.mnData?.ascentM
        ?: 0
    val duration = activeTrail.estimatedDuration.takeIf { it.isNotBlank() && it != "--" }
        ?: entry?.mnData?.durationText
        ?: estimateDuration(distanceKm, elevationGain)

    return SelectedTrailDetails(
        name = entry?.displayTitle ?: activeTrail.name,
        difficulty = resolveDifficulty(
            feature = null,
            lengthKm = distanceKm,
            elevationGain = elevationGain,
            difficultyLabel = entry?.mnData?.difficultyLabel ?: activeTrail.difficulty
        ),
        latitude = highlightBounds?.let { (it.minLat + it.maxLat) / 2 } ?: geometry?.center?.lat ?: activeTrail.latitude,
        longitude = highlightBounds?.let { (it.minLon + it.maxLon) / 2 } ?: geometry?.center?.lon ?: activeTrail.longitude,
        distanceKm = distanceKm,
        elevationGain = elevationGain,
        estimatedDuration = duration,
        localCode = activeTrail.localCode,
        region = activeTrail.region ?: entry?.region,
        descriptionRo = activeTrail.descriptionRo ?: entry?.description?.textRo,
        imageUrl = activeTrail.imageUrl ?: preferredDetailImageUrl(entry?.image),
        imageAttribution = activeTrail.imageAttribution ?: entry?.image?.attributionText,
        imageLicense = activeTrail.imageLicense ?: entry?.image?.license,
        imageSourcePageUrl = activeTrail.imageSourcePageUrl ?: entry?.image?.sourcePageUrl,
        imageScope = activeTrail.imageScope ?: entry?.image?.scope,
        highlightSegments = highlightSegments,
        highlightBounds = highlightBounds
    )
}

private fun estimateRouteLengthKm(entry: RouteGeometryEntry): Double =
    RouteGeometryRepository.decodeRenderableSegments(entry).sumOf { segment ->
        if (segment.size < 2) {
            0.0
        } else {
            segment.zipWithNext { start, end ->
                haversineKm(start.lat, start.lon, end.lat, end.lon)
            }.sum()
        }
    }

private fun routeBoundsForSegments(segments: List<List<RouteCoordinate>>): RouteBounds? {
    val coordinates = segments.flatten()
    if (coordinates.isEmpty()) return null
    return RouteBounds(
        minLat = coordinates.minOf { it.lat },
        minLon = coordinates.minOf { it.lon },
        maxLat = coordinates.maxOf { it.lat },
        maxLon = coordinates.maxOf { it.lon }
    )
}

private fun ensureSelectedRouteLayers(style: Style) {
    if (style.getSource(SelectedRouteSourceId) == null) {
        style.addSource(GeoJsonSource(SelectedRouteSourceId, emptySelectedRouteGeoJson()))
    }

    if (style.getLayer(SelectedRouteLayerId) == null) {
        style.addLayer(
            LineLayer(SelectedRouteLayerId, SelectedRouteSourceId).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineColor("#2dd4bf"),
                lineOpacity(0.96f),
                lineWidth(5.2f)
            )
        )
    }
}

private fun updateSelectedRouteHighlight(style: Style, decodedSegments: List<List<RouteCoordinate>>) {
    val routeSource = style.getSourceAs<GeoJsonSource>(SelectedRouteSourceId) ?: return
    if (decodedSegments.isEmpty()) {
        routeSource.setGeoJson(emptySelectedRouteGeoJson())
        return
    }

    routeSource.setGeoJson(buildSelectedRouteGeoJson(decodedSegments))
}

private fun buildSelectedRouteGeoJson(decodedSegments: List<List<RouteCoordinate>>): FeatureCollection {
    val features = decodedSegments.mapNotNull { segment ->
            if (segment.size < 2) {
                null
            } else {
                Feature.fromGeometry(
                    LineString.fromLngLats(
                        segment.map { coordinate -> Point.fromLngLat(coordinate.lon, coordinate.lat) }
                    )
                )
            }
        }
    return FeatureCollection.fromFeatures(features)
}

private fun emptySelectedRouteGeoJson(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyList())

private fun focusRoute(map: MapLibreMap, bounds: RouteBounds?, fallbackCenter: LatLng) {
    if (bounds == null) {
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                clampToRomaniaBounds(fallbackCenter),
                ActiveTrailZoom
            ),
            850
        )
        return
    }

    val clampedBounds = clampToRomaniaBounds(bounds)
    runCatching {
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(clampedBounds, 96),
            850
        )
    }.getOrElse {
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                clampToRomaniaBounds(fallbackCenter),
                zoomForRouteBounds(bounds)
            ),
            850
        )
    }
}

private fun clampToRomaniaBounds(point: LatLng): LatLng =
    LatLng(
        point.latitude.coerceIn(RomaniaBoundsMinLat, RomaniaBoundsMaxLat),
        point.longitude.coerceIn(RomaniaBoundsMinLon, RomaniaBoundsMaxLon)
    )

private fun clampToRomaniaBounds(bounds: RouteBounds): LatLngBounds =
    LatLngBounds.from(
        bounds.maxLat.coerceIn(RomaniaBoundsMinLat, RomaniaBoundsMaxLat),
        bounds.maxLon.coerceIn(RomaniaBoundsMinLon, RomaniaBoundsMaxLon),
        bounds.minLat.coerceIn(RomaniaBoundsMinLat, RomaniaBoundsMaxLat),
        bounds.minLon.coerceIn(RomaniaBoundsMinLon, RomaniaBoundsMaxLon)
    )

private fun zoomForRouteBounds(bounds: RouteBounds): Double {
    val latSpan = (bounds.maxLat - bounds.minLat).coerceAtLeast(0.002)
    val lonSpan = (bounds.maxLon - bounds.minLon).coerceAtLeast(0.002)
    val span = maxOf(latSpan, lonSpan)
    return (12.9 - log2(span / 0.02)).coerceIn(8.2, 15.0)
}

private fun imageScopeLabel(scope: String): String =
    when (scope) {
        "exact_route" -> "foto de traseu"
        "route_landmark" -> "reper de pe traseu"
        "region_fallback" -> "cadru de regiune"
        else -> scope
    }

private fun preferredDetailImageUrl(image: RouteImage?): String? =
    image?.imageUrl?.takeIf { it.isNotBlank() }
        ?: image?.thumbnailUrl?.takeIf { it.isNotBlank() }

private fun preferredSuggestionImageUrl(image: RouteImage?): String? =
    image?.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: image?.imageUrl?.takeIf { it.isNotBlank() }

private fun Double?.orEmptyDouble(): Double = this ?: 0.0

@SuppressLint("MissingPermission")
private fun enableLocationComponent(map: MapLibreMap, loadedMapStyle: Style, context: Context) {
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarseLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasFineLocation && !hasCoarseLocation) {
        return
    }

    runCatching {
        val locationComponent = map.locationComponent
        val activationOptions = LocationComponentActivationOptions
            .builder(context, loadedMapStyle)
            .build()
        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS
    }
}
