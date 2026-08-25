package com.exteragram.messenger.icons

import android.util.SparseIntArray
import java.io.File

data class IconPack(
    val id: String,
    val name: String,
    val author: String,
    val version: String = "1.0",
    val icons: Map<String, String> = emptyMap(),
    val preinstalledMap: SparseIntArray? = null,
    val location: File? = null
) {
    val isBase: Boolean
        get() = id.startsWith("base.")
}
