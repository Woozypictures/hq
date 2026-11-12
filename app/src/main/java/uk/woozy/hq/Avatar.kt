package uk.woozy.hq

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** The profile picture: one small square JPEG in app-private storage. It never leaves the phone. */
object Avatar {
    private fun file(c: Context) = File(c.filesDir, "avatar.jpg")
    fun has(c: Context): Boolean = file(c).exists()

    /** Centre-crops the chosen picture to a 384 px square. Returns false if it could not be read. */
    fun save(c: Context, uri: Uri): Boolean = runCatching {
        val size = 384
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val raw = c.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
        val side = minOf(raw.width, raw.height)
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, size, size, true)
        file(c).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    fun clear(c: Context) { file(c).delete() }
    fun load(c: Context): Bitmap? = file(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
}
