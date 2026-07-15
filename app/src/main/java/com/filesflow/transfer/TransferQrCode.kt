package com.filesflow.transfer

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal object TransferQrEncoder {
    fun encode(text: String, size: Int = 768): Bitmap {
        require(text.isNotBlank()) { "QR content cannot be blank" }
        require(size >= 128) { "QR size must be at least 128 pixels" }
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
            ),
        )
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }
}

@Composable
internal fun TransferQrCode(url: String, modifier: Modifier = Modifier) {
    val bitmap = remember(url) { TransferQrEncoder.encode(url) }
    Box(
        modifier = modifier
            .testTag("transfer-qr-code")
            .background(Color.White)
            .padding(12.dp),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code for transfer link",
            modifier = Modifier.size(232.dp),
        )
    }
}
