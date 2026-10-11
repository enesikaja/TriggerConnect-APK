package com.triggerconnect.app.ui.components

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A CameraX preview with ML Kit QR analysis. Invokes [onCode] exactly once with
 * the first decoded QR payload. Caller is responsible for the CAMERA permission.
 */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraQrScanner(
    onCode: (String) -> Unit,
    scanningEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    val cameraProvider = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val scanner = remember { BarcodeScanning.getClient() }
    val currentOnCode by rememberUpdatedState(onCode)
    val currentScanningEnabled by rememberUpdatedState(scanningEnabled)

    // Allow another QR after a failed resolve or after the confirmation dialog
    // is dismissed. Previously `handled` stayed true for the lifetime of this
    // screen, so the camera silently ignored every later QR.
    LaunchedEffect(scanningEnabled) {
        if (scanningEnabled) handled.set(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            cameraProvider.get()?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                if (disposed.get()) return@addListener
                val provider = cameraProviderFuture.get()
                cameraProvider.set(provider)
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || !currentScanningEnabled || handled.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes
                                .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                ?.rawValue
                            if (value != null && handled.compareAndSet(false, true)) {
                                currentOnCode(value)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
