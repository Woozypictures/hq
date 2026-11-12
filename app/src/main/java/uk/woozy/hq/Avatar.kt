package uk.woozy.hq

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The profile picture: one small square JPEG in app-private storage, taken from the icon of the
 * Notion DASHBOARD page. HQ Fit reads it through [AvatarProvider], so it is only ever changed in Notion.
 */
object Avatar {
    /** The DASHBOARD page whose icon is the profile picture. */
    const val DASHBOARD_PAGE = "ea4d45cd-5759-4ee7-987f-9120609c8293"

    private const val SIZE = 384
    private const val MAX_BYTES = 12 * 1024 * 1024

    fun fileOf(c: Context) = File(c.filesDir, "avatar.jpg")
    private fun keyFile(c: Context) = File(c.filesDir, "avatar.key")

    fun has(c: Context): Boolean = fileOf(c).exists()
    fun load(c: Context): Bitmap? = fileOf(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /**
     * Makes the stored picture match the Notion page icon. [icon] is (stable id, download url), or null when the
     * icon is an emoji or empty, which removes the picture. Notion's file links expire after about an hour, so the
     * stable id (the link without its signature) decides whether a new download is needed.
     */
    fun sync(c: Context, icon: Pair<String, String>?) {
        if (icon == null) {
            fileOf(c).delete(); keyFile(c).delete()
            return
        }
        val (key, url) = icon
        if (has(c) && keyFile(c).takeIf { it.exists() }?.readText() == key) return
        val bytes = download(url)
        saveBytes(c, bytes)
        keyFile(c).writeText(key)
    }

    private fun download(url: String): ByteArray {
        // A signed link: no Authorization header goes to the file host.
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("Profile picture: HTTP $code")
        val out = conn.inputStream.use { it.readBytes() }
        if (out.size > MAX_BYTES) throw java.io.IOException("Profile picture is too large")
        return out
    }

    /** Centre-crops to a 384 px square and stores it as a JPEG. */
    private fun saveBytes(c: Context, bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw java.io.IOException("Profile picture could not be read")
        val side = minOf(raw.width, raw.height)
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val tmp = File(c.filesDir, "avatar.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(fileOf(c))
    }
}
