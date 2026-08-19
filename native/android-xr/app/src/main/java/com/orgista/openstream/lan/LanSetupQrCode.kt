package com.orgista.openstream.lan

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object LanSetupQrCode {
    fun matrix(content: String, pixels: Int = 384) = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        pixels.coerceAtLeast(96),
        pixels.coerceAtLeast(96),
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        ),
    )

    fun imageBitmap(content: String, pixels: Int = 384): ImageBitmap {
        val matrix = matrix(content, pixels)
        val colors = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                colors[y * matrix.width + x] = if (matrix[x, y]) 0xFF05070A.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(colors, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
