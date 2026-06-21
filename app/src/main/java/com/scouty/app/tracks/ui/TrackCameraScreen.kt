package com.scouty.app.tracks.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.TriangleAlert
import com.scouty.app.tracks.data.TrackSafetyLevel
import com.scouty.app.tracks.data.TrackSpeciesCatalog
import com.scouty.app.tracks.domain.TrackConfidenceBand
import com.scouty.app.tracks.domain.TrackIdentificationResult
import com.scouty.app.tracks.domain.TrackIdentificationUseCase
import com.scouty.app.tracks.domain.TrackPrediction
import com.scouty.app.ui.components.PrimaryButton
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.components.SecondaryButton
import com.scouty.app.ui.components.StatTile
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.AccentGreenOnSurface
import com.scouty.app.ui.theme.BgPrimary
import com.scouty.app.ui.theme.BorderDefault
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.Info as InfoColor
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ScannerWhite = Color(0xFFFFFFFF)

private enum class TrackScannerState {
    Scanning,
    Processing,
    Detected,
    NeedsRetry,
}

@Composable
fun TrackCameraScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val useCase = remember(context) { TrackIdentificationUseCase(context.applicationContext) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var result by remember { mutableStateOf<TrackIdentificationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val topPrediction = result?.topPrediction
    val hasDetection = topPrediction != null
    val hasFailure = error != null || (result != null && topPrediction == null)
    val scannerState = when {
        analyzing -> TrackScannerState.Processing
        hasDetection -> TrackScannerState.Detected
        hasFailure -> TrackScannerState.NeedsRetry
        else -> TrackScannerState.Scanning
    }

    fun resetScan() {
        result = null
        error = null
    }

    fun analyzeImageFile(file: File) {
        analyzing = true
        error = null
        result = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    useCase.identify(file)
                }
            }.onSuccess {
                result = it
            }.onFailure {
                error = it.message ?: "Analiza a esuat."
            }
            analyzing = false
        }
    }

    fun analyzeGalleryImage(uri: Uri) {
        analyzing = true
        error = null
        result = null
        scope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    copyGalleryImageToCache(context, uri)
                }
                withContext(Dispatchers.Default) {
                    useCase.identify(file)
                }
            }.onSuccess {
                result = it
            }.onFailure {
                error = it.message ?: "Nu am putut analiza imaginea selectata."
            }
            analyzing = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::analyzeGalleryImage)
    }

    fun captureCurrentFrame() {
        val capture = imageCapture ?: return
        analyzing = true
        error = null
        result = null
        capture.flashMode = ImageCapture.FLASH_MODE_OFF
        captureTrackImage(
            context = context,
            imageCapture = capture,
            onSaved = ::analyzeImageFile,
            onError = {
                error = it
                analyzing = false
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    bindCamera(
                        context = viewContext,
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        onImageCaptureReady = { imageCapture = it },
                        onError = { error = it.message },
                    )
                }
            },
        )

        ScannerScrimAndViewfinder(
            state = scannerState,
            detectedName = topPrediction?.let { displayName(it.className) },
        )

        ScannerTopBar(
            onBack = onBack,
            onInfo = { showInfo = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 4.dp, start = 14.dp, end = 14.dp),
        )

        BottomCaptureControls(
            captureEnabled = !analyzing && imageCapture != null,
            galleryEnabled = !analyzing,
            processing = analyzing,
            onCapture = ::captureCurrentFrame,
            onGallery = { galleryLauncher.launch("image/*") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        )

        AnimatedVisibility(
            visible = showInfo,
            modifier = Modifier.fillMaxSize(),
        ) {
            ScannerInfoSheet(
                onDismiss = { showInfo = false },
                bottomPadding = contentPadding.calculateBottomPadding(),
            )
        }

        if (result != null && topPrediction != null) {
            ScannerDim()
            TrackSuccessSheet(
                prediction = topPrediction,
                onScanAgain = ::resetScan,
                onDone = onBack,
                bottomPadding = contentPadding.calculateBottomPadding(),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (hasFailure) {
            ScannerDim()
            TrackErrorSheet(
                message = error,
                result = result,
                onBack = onBack,
                onRetry = ::resetScan,
                bottomPadding = contentPadding.calculateBottomPadding(),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ScannerTopBar(
    onBack: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = Lucide.ChevronLeft,
            contentDescription = "Înapoi",
            onClick = onBack,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Scaneaza urma",
                style = MaterialTheme.typography.labelLarge,
                color = ScannerWhite,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Pentru identificare locală",
                fontSize = 10.sp,
                color = ScannerWhite.copy(alpha = 0.6f),
                lineHeight = 13.sp,
            )
        }
        GlassIconButton(
            icon = Lucide.Info,
            contentDescription = "Informații",
            onClick = onInfo,
        )
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(0.5.dp, ScannerWhite.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ScannerWhite,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ScannerScrimAndViewfinder(
    state: TrackScannerState,
    detectedName: String?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scanSize = when {
            maxWidth < 360.dp -> (maxWidth - 88.dp).coerceAtLeast(220.dp)
            maxWidth < 420.dp -> (maxWidth - 110.dp).coerceAtLeast(230.dp)
            else -> 270.dp
        }
        val scanOffsetY = (-26).dp
        val density = LocalDensity.current
        val scanSizePx = with(density) { scanSize.toPx() }
        val scanOffsetYPx = with(density) { scanOffsetY.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val left = (size.width - scanSizePx) / 2f
            val top = (size.height - scanSizePx) / 2f + scanOffsetYPx
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                size = size,
            )
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(scanSizePx, scanSizePx),
                cornerRadius = CornerRadius(20.dp.toPx()),
                blendMode = BlendMode.Clear,
            )
        }

        Box(
            modifier = Modifier
                .size(scanSize)
                .align(Alignment.Center)
                .padding(0.dp)
                .graphicsLayer { translationY = with(density) { scanOffsetY.toPx() } },
        ) {
            ScannerBrackets(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.03f
                        scaleY = 1.03f
                    },
            )
            ScannerStatePill(
                state = state,
                detectedName = detectedName,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
            )
            ScannerScanLine(
                active = state == TrackScannerState.Scanning || state == TrackScannerState.NeedsRetry,
                scanSize = scanSize,
            )
            ScannerHint(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            )
        }
    }
}


@Composable
private fun ScannerBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val length = 32.dp.toPx()
        val outside = 2.dp.toPx()
        val stroke = 3.dp.toPx()
        val left = -outside
        val top = -outside
        val right = size.width + outside
        val bottom = size.height + outside

        fun bracket(start: Offset, mid: Offset, end: Offset) {
            drawLine(AccentGreen, start, mid, stroke, cap = StrokeCap.Round)
            drawLine(AccentGreen, mid, end, stroke, cap = StrokeCap.Round)
        }

        bracket(
            Offset(left, top + length),
            Offset(left, top),
            Offset(left + length, top),
        )
        bracket(
            Offset(right - length, top),
            Offset(right, top),
            Offset(right, top + length),
        )
        bracket(
            Offset(left, bottom - length),
            Offset(left, bottom),
            Offset(left + length, bottom),
        )
        bracket(
            Offset(right - length, bottom),
            Offset(right, bottom),
            Offset(right, bottom - length),
        )
    }
}

@Composable
private fun ScannerScanLine(
    active: Boolean,
    scanSize: Dp,
) {
    if (!active) return
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "trackScanLine")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "trackScanProgress",
    )
    val y = (scanSize - 18.dp) * progress
    val yPx = with(density) { y.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .padding(horizontal = 18.dp)
            .graphicsLayer { translationY = yPx },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .blur(8.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            AccentGreen.copy(alpha = 0.75f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            AccentGreen,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun ScannerStatePill(
    state: TrackScannerState,
    detectedName: String?,
    modifier: Modifier = Modifier,
) {
    val (background, content, label) = when (state) {
        TrackScannerState.Scanning -> Triple(
            AccentGreen.copy(alpha = 0.95f),
            AccentGreenOnSurface,
            "Detectare urma...",
        )
        TrackScannerState.Detected -> Triple(
            AccentGreen,
            AccentGreenOnSurface,
            "${detectedName ?: "Urma"} detectata",
        )
        TrackScannerState.NeedsRetry -> Triple(
            Warning.copy(alpha = 0.95f),
            AccentGreenOnSurface,
            "Apropie-te de urma",
        )
        TrackScannerState.Processing -> Triple(
            AccentGreen.copy(alpha = 0.95f),
            AccentGreenOnSurface,
            "Se identifica...",
        )
    }

    val transition = rememberInfiniteTransition(label = "scannerDot")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scannerDotAlpha",
    )

    Row(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            TrackScannerState.Scanning -> Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(content),
            )
            TrackScannerState.Detected -> Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(10.dp),
            )
            TrackScannerState.NeedsRetry -> Icon(
                imageVector = Lucide.TriangleAlert,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(10.dp),
            )
            TrackScannerState.Processing -> CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                color = content,
                strokeWidth = 1.5.dp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = content,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun ScannerHint(
    state: TrackScannerState,
    modifier: Modifier = Modifier,
) {
    val hint = when (state) {
        TrackScannerState.Scanning -> "Aliniaza urma in cadru"
        TrackScannerState.NeedsRetry -> "Fotografiaza direct de sus, la 30-50 cm"
        TrackScannerState.Processing -> "Tine telefonul stabil"
        TrackScannerState.Detected -> null
    } ?: return

    Box(
        modifier = modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(0.5.dp, ScannerWhite.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = hint,
            color = ScannerWhite,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BottomCaptureControls(
    captureEnabled: Boolean,
    galleryEnabled: Boolean,
    processing: Boolean,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScannerIconAction(
            icon = Lucide.ImagePlus,
            label = "Galerie",
            enabled = galleryEnabled,
            onClick = onGallery,
        )
        Spacer(Modifier.width(24.dp))
        CaptureButton(
            enabled = captureEnabled,
            processing = processing,
            onClick = onCapture,
        )
    }
}

@Composable
private fun ScannerIconAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.alpha(if (enabled) 1f else 0.55f),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ScannerWhite.copy(alpha = 0.1f))
                .border(0.5.dp, ScannerWhite.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ScannerWhite,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            color = ScannerWhite.copy(alpha = 0.7f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
        )
    }
}

@Composable
private fun CaptureButton(
    enabled: Boolean,
    processing: Boolean,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "captureRing")
    val ringAlpha by transition.animateFloat(
        initialValue = if (processing) 0.25f else 0.4f,
        targetValue = if (processing) 0.9f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "captureRingAlpha",
    )
    Box(
        modifier = Modifier.size(86.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .border(2.dp, AccentGreen.copy(alpha = ringAlpha), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = if (enabled) 1f else 0.45f))
                .border(4.dp, ScannerWhite.copy(alpha = 0.95f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (processing) {
                CircularProgressIndicator(
                    color = AccentGreenOnSurface,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Icon(
                    imageVector = Lucide.Camera,
                    contentDescription = "Fotografiază urma",
                    tint = AccentGreenOnSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun ScannerDim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f)),
    )
}

@Composable
private fun ScannerInfoSheet(
    onDismiss: () -> Unit,
    bottomPadding: Dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScannerDim()
        ScannerBottomSheet(
            bottomPadding = bottomPadding,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(InfoColor.copy(alpha = 0.12f))
                        .border(0.5.dp, InfoColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Lucide.Info,
                        contentDescription = null,
                        tint = InfoColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ce poti scana",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Urme de animal fotografiate clar, direct de sus.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Pentru rezultate mai bune, centrează urma, folosește lumină bună și evită unghiurile oblice. Modelul rulează local pe telefon și poate întoarce un rezultat incert.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Am inteles",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrackSuccessSheet(
    prediction: TrackPrediction,
    onScanAgain: () -> Unit,
    onDone: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    ScannerBottomSheet(
        bottomPadding = bottomPadding,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrackMarkIcon(prediction = prediction)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Urma: ${displayName(prediction.className)} (estimare, nu lua ca atare)",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        StatTile(
            label = "SCOR",
            value = "${(prediction.confidence * 100).toInt()}",
            unit = "%",
            accent = AccentGreen,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        SpeciesCard(prediction = prediction)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = "Cauta din nou",
                onClick = onScanAgain,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Gata",
                onClick = onDone,
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}

@Composable
private fun TrackErrorSheet(
    message: String?,
    result: TrackIdentificationResult?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    ScannerBottomSheet(
        bottomPadding = bottomPadding,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Warning.copy(alpha = 0.12f))
                .border(0.5.dp, Warning.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.SearchX,
                contentDescription = null,
                tint = Warning,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Nu am putut identifica urma",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message ?: if (result?.topPrediction == null) {
                "Nu am detectat o urma in imagine. Incearca lumina mai buna si centreaza urma in patrat."
            } else {
                "Nu am putut procesa rezultatul. Fotografiaza mai aproape, direct de sus."
            },
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = "Inapoi",
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Incearca din nou",
                onClick = onRetry,
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}

@Composable
private fun ScannerBottomSheet(
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .navigationBarsPadding()
            .padding(bottom = bottomPadding + 8.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(BgPrimary.copy(alpha = 0.98f))
            .border(
                0.5.dp,
                BorderDefault,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

@Composable
private fun TrackMarkIcon(prediction: TrackPrediction) {
    val species = TrackSpeciesCatalog.find(prediction.className)
    val color = when (species?.safetyLevel) {
        TrackSafetyLevel.DANGER -> Danger
        TrackSafetyLevel.CAUTION -> Warning
        else -> AccentGreen
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.62f)
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.22f,
                    size.width * 0.72f,
                    size.height * 0.22f,
                    size.width * 0.8f,
                    size.height * 0.62f,
                )
                cubicTo(
                    size.width * 0.62f,
                    size.height * 0.5f,
                    size.width * 0.38f,
                    size.height * 0.5f,
                    size.width * 0.2f,
                    size.height * 0.62f,
                )
            }
            drawPath(path = path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun TrackImagePreview(result: TrackIdentificationResult, topPrediction: TrackPrediction) {
    val imageFile = result.imageFile ?: return
    val imageBounds = remember(imageFile) {
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imageFile.absolutePath, this)
        }.rotatedSize(imageFile)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color.Black, RoundedCornerShape(12.dp)),
    ) {
        Image(
            painter = rememberAsyncImagePainter(imageFile),
            contentDescription = "Fotografie urmă",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val imageWidth = imageBounds.first.toFloat().takeIf { it > 0f } ?: return@Canvas
            val imageHeight = imageBounds.second.toFloat().takeIf { it > 0f } ?: return@Canvas
            val scale = minOf(size.width / imageWidth, size.height / imageHeight)
            val displayedWidth = imageWidth * scale
            val displayedHeight = imageHeight * scale
            val offsetX = (size.width - displayedWidth) / 2f
            val offsetY = (size.height - displayedHeight) / 2f
            val box = topPrediction.boundingBox
            drawRect(
                color = AccentGreen,
                topLeft = Offset(offsetX + box.left * scale, offsetY + box.top * scale),
                size = Size(box.width * scale, box.height * scale),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private fun BitmapFactory.Options.rotatedSize(imageFile: File): Pair<Int, Int> {
    val orientation = ExifInterface(imageFile.absolutePath)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270 -> outHeight to outWidth
        else -> outWidth to outHeight
    }
}

@Composable
private fun SpeciesCard(prediction: TrackPrediction) {
    val species = TrackSpeciesCatalog.find(prediction.className)
    val safetyColor = when (species?.safetyLevel) {
        TrackSafetyLevel.DANGER -> Danger
        TrackSafetyLevel.CAUTION -> Warning
        else -> AccentGreen
    }
    ScoutyCard(
        modifier = Modifier.fillMaxWidth(),
        semantic = safetyColor,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(safetyColor, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = species?.romanianName ?: prediction.className,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = species?.scientificName.orEmpty(),
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = confidenceLabel(prediction.confidence),
                color = safetyColor,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
        species?.features?.let { features ->
            Spacer(Modifier.height(8.dp))
            features.take(3).forEach {
                Text(text = "- $it", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (species?.safetyLevel == TrackSafetyLevel.DANGER) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Atentie: pastreaza distanta, nu urmari urma si indeparteaza-te linistit.",
                color = Danger,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun resultTitle(result: TrackIdentificationResult): String =
    when (result.band) {
        TrackConfidenceBand.PROBABIL -> "Probabil urma de ${displayName(result.topPrediction?.className)}"
        TrackConfidenceBand.POSIBIL -> "Posibila urma de ${displayName(result.topPrediction?.className)}"
        TrackConfidenceBand.INCERT -> "Nu am putut identifica urma cu incredere"
    }

private fun displayName(className: String?): String =
    className?.let { TrackSpeciesCatalog.find(it)?.romanianName ?: it } ?: "necunoscuta"

private fun confidenceLabel(confidence: Float): String =
    when {
        confidence >= 0.70f -> "Probabil"
        confidence >= 0.40f -> "Posibil"
        else -> "Incert"
    }

private fun PreviewView.bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
        {
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
                onImageCaptureReady(imageCapture)
            }.onFailure(onError)
        },
        ContextCompat.getMainExecutor(context),
    )
}

private fun captureTrackImage(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: (File) -> Unit,
    onError: (String) -> Unit,
) {
    val file = File.createTempFile("track_capture_", ".jpg", context.cacheDir)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(file)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "Captura a esuat.")
            }
        },
    )
}

private fun copyGalleryImageToCache(context: Context, uri: Uri): File {
    val file = File.createTempFile("track_gallery_", ".jpg", context.cacheDir)
    val input = context.contentResolver.openInputStream(uri)
        ?: error("Nu am putut citi imaginea selectata.")
    input.use { source ->
        file.outputStream().use { target ->
            source.copyTo(target)
        }
    }
    return file
}
