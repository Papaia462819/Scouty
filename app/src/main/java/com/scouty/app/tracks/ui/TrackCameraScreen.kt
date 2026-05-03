package com.scouty.app.tracks.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.scouty.app.tracks.data.TrackSafetyLevel
import com.scouty.app.tracks.data.TrackSpeciesCatalog
import com.scouty.app.tracks.domain.TrackConfidenceBand
import com.scouty.app.tracks.domain.TrackIdentificationResult
import com.scouty.app.tracks.domain.TrackIdentificationUseCase
import com.scouty.app.tracks.domain.TrackPrediction
import com.scouty.app.ui.components.ScoutyCard
import com.scouty.app.ui.theme.AccentGreen
import com.scouty.app.ui.theme.BgSurface
import com.scouty.app.ui.theme.Danger
import com.scouty.app.ui.theme.TextPrimary
import com.scouty.app.ui.theme.TextSecondary
import com.scouty.app.ui.theme.TextTertiary
import com.scouty.app.ui.theme.Warning
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Inapoi", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Identifica o urma",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Text(
                    text = "Offline, pe telefon",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .background(BgSurface, RoundedCornerShape(18.dp)),
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
            FramingGuide()
            if (analyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentGreen)
                        Spacer(Modifier.height(10.dp))
                        Text("Analizam urma...", color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    analyzing = true
                    error = null
                    captureTrackImage(
                        context = context,
                        imageCapture = capture,
                        onSaved = { file ->
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
                        },
                        onError = {
                            error = it
                            analyzing = false
                        },
                    )
                },
                enabled = !analyzing && imageCapture != null,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Fotografiaza urma")
            }

            Button(
                onClick = {
                    result = null
                    error = null
                },
                enabled = result != null || error != null,
                colors = ButtonDefaults.buttonColors(containerColor = BgSurface),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, color = Danger, style = MaterialTheme.typography.bodyMedium)
        }

        result?.let {
            Spacer(Modifier.height(16.dp))
            TrackResultPanel(result = it)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FramingGuide() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension * 0.62f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.7f),
            topLeft = Offset(left, top),
            size = Size(side, side),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun TrackResultPanel(result: TrackIdentificationResult) {
    val top = result.topPrediction
    ScoutyCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Text(
            text = resultTitle(result),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Timp analiza: ${result.elapsedMs} ms",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
        Spacer(Modifier.height(10.dp))

        if (result.imageFile != null && top != null) {
            TrackImagePreview(result = result, topPrediction = top)
            Spacer(Modifier.height(12.dp))
        }

        if (top != null && result.band != TrackConfidenceBand.INCERT) {
            val prediction = top
            SpeciesCard(prediction = prediction)
        } else {
            Text(
                text = if (top == null) {
                    "Nu am detectat o urma in imagine. Fotografiaza urma direct de sus, cu ea centrata."
                } else {
                    "Am detectat o posibila urma, dar scorul nu este suficient pentru identificare."
                },
                color = TextSecondary,
            )
        }

        if (result.predictions.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("Alte posibilitati", color = TextSecondary, fontWeight = FontWeight.Medium)
            result.predictions.drop(1).take(2).forEach {
                Text(
                    text = "${displayName(it.className)} · ${confidenceLabel(it.confidence)}",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Identificare automata. Nu garantam precizia.",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
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
            contentDescription = "Fotografie urma",
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
