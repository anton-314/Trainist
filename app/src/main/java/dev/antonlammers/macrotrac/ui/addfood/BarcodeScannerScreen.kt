package dev.antonlammers.macrotrac.ui.addfood

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import java.util.concurrent.atomic.AtomicBoolean

@ExperimentalGetImage
@Composable
fun BarcodeScannerScreen(navController: NavController) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            val detected = remember { AtomicBoolean(false) }
            // A scanned or manually entered barcode is returned the same way: set it on the
            // previous entry and pop. The AtomicBoolean guards against firing more than once.
            val submitBarcode: (String) -> Unit = remember(navController) {
                { barcode ->
                    val trimmed = barcode.trim()
                    if (trimmed.isNotEmpty() && detected.compareAndSet(false, true)) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("barcode", trimmed)
                        navController.popBackStack()
                    }
                }
            }
            // Torch starts off; only revealed once we know the device actually has a flash unit.
            var torchOn by remember { mutableStateOf(false) }
            var hasFlashUnit by remember { mutableStateOf(false) }
            CameraPreview(
                torchEnabled = torchOn,
                onFlashUnitAvailable = { hasFlashUnit = it },
                analyzer = BarcodeAnalyzer { barcode -> submitBarcode(barcode) },
            )
            ScanOverlay()
            if (hasFlashUnit) {
                IconButton(
                    onClick = { torchOn = !torchOn },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (torchOn) "Taschenlampe ausschalten" else "Taschenlampe einschalten",
                        tint = Color.White,
                    )
                }
            }
            var manualBarcode by remember { mutableStateOf("") }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Barcode in den Rahmen halten oder Nummer manuell eingeben",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumericTextField(
                            value = manualBarcode,
                            onValueChange = { manualBarcode = it },
                            label = "Barcode-Nummer",
                            decimal = false,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { submitBarcode(manualBarcode) },
                            enabled = manualBarcode.isNotBlank(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Barcode suchen",
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Kamerazugriff wird benötigt", color = Color.White)
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Erlauben")
                }
            }
        }

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}

@ExperimentalGetImage
@Composable
private fun CameraPreview(
    analyzer: BarcodeAnalyzer,
    torchEnabled: Boolean,
    onFlashUnitAvailable: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { ContextCompat.getMainExecutor(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Apply the requested torch state whenever it changes or the camera (re)binds.
    LaunchedEffect(camera, torchEnabled) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, analyzer) }
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    ).also { onFlashUnitAvailable(it.cameraInfo.hasFlashUnit()) }
                }, executor)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ScanOverlay() {
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "scan")
    val scanProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanLine",
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        // Semi-transparent overlay
        drawRect(color = Color(0xBB000000))

        val frameW = size.width * 0.72f
        val frameH = frameW * 0.55f
        val left = (size.width - frameW) / 2f
        val top = (size.height - frameH) / 2f
        val right = left + frameW
        val bottom = top + frameH

        // Transparent cutout (rounded)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            blendMode = BlendMode.Clear,
        )

        // Corner brackets
        val cLen = 36.dp.toPx()
        val cStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        // Top-left
        drawLine(primary, Offset(left, top + cLen), Offset(left, top), cStroke.width, StrokeCap.Round)
        drawLine(primary, Offset(left, top), Offset(left + cLen, top), cStroke.width, StrokeCap.Round)
        // Top-right
        drawLine(primary, Offset(right - cLen, top), Offset(right, top), cStroke.width, StrokeCap.Round)
        drawLine(primary, Offset(right, top), Offset(right, top + cLen), cStroke.width, StrokeCap.Round)
        // Bottom-left
        drawLine(primary, Offset(left, bottom - cLen), Offset(left, bottom), cStroke.width, StrokeCap.Round)
        drawLine(primary, Offset(left, bottom), Offset(left + cLen, bottom), cStroke.width, StrokeCap.Round)
        // Bottom-right
        drawLine(primary, Offset(right - cLen, bottom), Offset(right, bottom), cStroke.width, StrokeCap.Round)
        drawLine(primary, Offset(right, bottom), Offset(right, bottom - cLen), cStroke.width, StrokeCap.Round)

        // Animated scan line
        val lineY = top + frameH * scanProgress
        drawLine(
            color = primary.copy(alpha = 0.7f),
            start = Offset(left + 4.dp.toPx(), lineY),
            end = Offset(right - 4.dp.toPx(), lineY),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
