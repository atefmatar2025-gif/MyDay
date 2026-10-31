package com.myday.app.proof

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.util.UUID

object QrTools {

    fun newCode(): String = "MYDAY-" + UUID.randomUUID().toString().take(13).uppercase()

    fun qrBitmap(text: String, size: Int = 720): Bitmap {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
        )
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        return bmp
    }

    /** صورة قابلة للطباعة: الرمز + عنوان المهمة، ثم مشاركتها (طباعة، حفظ، واتساب...). */
    fun share(context: Context, code: String, title: String) {
        val qr = qrBitmap(code, 900)
        val out = Bitmap.createBitmap(1000, 1180, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(qr, 50f, 40f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 54f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(title.take(30), 500f, 1020f, paint)
        paint.textSize = 36f; paint.typeface = Typeface.DEFAULT
        c.drawText("امسحني لإيقاف المنبه — تطبيق يومي", 500f, 1090f, paint)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "myday-qr.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "اطبع أو احفظ الرمز").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
