package app.iptvtest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code desenhado direto na tela (fundo branco e margem, para a câmera do celular ler fácil na TV). */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            )
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            null
        }
    }
    Canvas(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val m = matrix ?: return@Canvas
        val quiet = 3
        val n = m.width
        val cell = size.minDimension / (n + quiet * 2)
        val side = cell * (n + quiet * 2)
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        for (y in 0 until m.height) {
            for (x in 0 until n) {
                if (m.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(ox + (x + quiet) * cell, oy + (y + quiet) * cell),
                        size = Size(cell + 0.6f, cell + 0.6f),
                    )
                }
            }
        }
    }
}
