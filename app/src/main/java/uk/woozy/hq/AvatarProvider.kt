package uk.woozy.hq

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Shares the profile picture with HQ Fit (same signing key, signature permission, read only).
 * `content://uk.woozy.hq.avatar/avatar`: query gives one `stamp` (modified time) row when there is a picture;
 * openFile returns the JPEG.
 */
class AvatarProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cur = MatrixCursor(arrayOf("stamp"))
        val f = Avatar.fileOf(context ?: return cur)
        if (f.exists()) cur.addRow(arrayOf<Any>(f.lastModified()))
        return cur
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = Avatar.fileOf(context ?: throw FileNotFoundException())
        if (mode != "r" || !f.exists()) throw FileNotFoundException("No picture")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
