package com.myday.app.proof

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** يقرأ QR وباركود المنتجات من إطارات الكاميرا (ZXing، دون إنترنت). */
class CodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39, BarcodeFormat.DATA_MATRIX, BarcodeFormat.ITF,
                ),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }
    @Volatile private var done = false
    private var frame = 0

    override fun analyze(image: ImageProxy) {
        try {
            if (done) return
            val plane = image.planes[0]
            val w = image.width
            val h = image.height
            val rowStride = plane.rowStride
            val buf = plane.buffer
            val data = ByteArray(w * h)
            buf.rewind()
            if (rowStride == w) {
                buf.get(data, 0, minOf(data.size, buf.remaining()))
            } else {
                val row = ByteArray(rowStride)
                for (y in 0 until h) {
                    buf.position(y * rowStride)
                    buf.get(row, 0, minOf(rowStride, buf.remaining()))
                    System.arraycopy(row, 0, data, y * w, w)
                }
            }
            // الباركود الطولي يُقرأ باتجاه واحد؛ نجرب الصورة مدورة كل إطار ثانٍ
            frame++
            val text = decode(data, w, h) ?: if (frame % 2 == 0) decode(rotate(data, w, h), h, w) else null
            if (text != null && !done) {
                done = true
                onCode(text)
            }
        } catch (e: Exception) {
            // إطار تالف — نتجاهله
        } finally {
            image.close()
        }
    }

    private fun decode(data: ByteArray, w: Int, h: Int): String? = try {
        val src = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
    } catch (e: NotFoundException) {
        null
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }

    private fun rotate(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = src[y * w + x]
        return out
    }

    /** للسماح بمسح جديد بعد نتيجة غير مطابقة. */
    fun reset() { done = false }
}
