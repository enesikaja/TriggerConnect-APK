package com.triggerconnect.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders a QR code for [content] on a white card so it scans reliably in dark mode. */
@Composable
fun QrImage(content: String, modifier: Modifier = Modifier, sizeDp: Int = 240) {
    val painter = remember(content) { generateQr(content, 512)?.let { BitmapPainter(it.asImageBitmap()) } }
    Box(
        modifier = modifier
            .background(Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (painter != null) {
            Image(painter = painter, contentDescription = "QR code", modifier = Modifier.size(sizeDp.dp))
        }
    }
}

private fun generateQr(content: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bmp
}.getOrNull()
