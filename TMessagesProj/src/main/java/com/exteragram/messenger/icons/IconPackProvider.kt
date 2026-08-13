package com.exteragram.messenger.icons

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.telegram.messenger.ApplicationLoader
import java.io.File

class IconPackProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        IconManager.initialize(false)
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val segments = uri.pathSegments
        if (segments.size >= 3 && segments[0] == "icon") {
            val packId = segments[1]
            val fileName = segments[2]
            val file = IconPackStorage.INSTANCE.resolveIconFile(packId, fileName)
            if (file != null && file.exists()) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        return null
    }

    override fun getType(uri: Uri): String? = null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        @JvmStatic
        fun getIconUri(packId: String, resourceName: String): Uri? {
            val iconFile = IconPackStorage.INSTANCE.resolveIconFile(packId, resourceName) ?: return null
            if (!iconFile.exists()) return null
            return Uri.Builder()
                .scheme("content")
                .authority("${ApplicationLoader.getApplicationId()}.icon_pack_provider")
                .appendPath("icon")
                .appendPath(packId)
                .appendPath(resourceName)
                .appendQueryParameter("v", "${iconFile.lastModified()}_${iconFile.length()}")
                .build()
        }
    }
}
