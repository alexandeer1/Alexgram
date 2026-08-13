package com.exteragram.messenger.icons

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.collection.LruCache
import com.exteragram.messenger.icons.ui.components.InstallIconPackBottomSheet
import kotlinx.coroutines.*
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SvgHelper
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import tw.nekomimi.nekogram.helpers.MessageHelper
import tw.nekomimi.nekogram.ui.icons.IconsResources
import xyz.nextalone.nagram.NaConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object IconManager {
    val INSTANCE = this

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val activePacks = CopyOnWriteArrayList<IconPack>()
    val iconOwnerMap = ConcurrentHashMap<String, IconPack>()
    private val resNameCache = ConcurrentHashMap<Int, String>()
    private val scaledBitmapCache = LruCache<String, Bitmap>(250)

    init {
        initialize(false)
    }

    @JvmStatic
    fun initialize(force: Boolean = false) {
        scope.launch {
            val activeId = getActivePackId()
            if (activeId != BaseIconPacks.DEFAULT.id && activeId != BaseIconPacks.SOLAR.id && activeId != BaseIconPacks.REMIX.id) {
                val pack = IconPackStorage.INSTANCE.findPackById(activeId)
                activePacks.clear()
                if (pack != null) {
                    activePacks.add(pack)
                }
                rebuildOwnerMap()
            }
        }
    }

    fun getActivePackId(): String {
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
        return prefs.getString("active_icon_pack", BaseIconPacks.DEFAULT.id) ?: BaseIconPacks.DEFAULT.id
    }

    fun setActiveCustomPack(packId: String) {
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
        prefs.edit().putString("active_icon_pack", packId).apply()

        if (packId == BaseIconPacks.SOLAR.id) {
            NaConfig.iconReplacements.setConfigInt(1)
            activePacks.clear()
        } else if (packId == BaseIconPacks.REMIX.id) {
            NaConfig.iconReplacements.setConfigInt(2)
            activePacks.clear()
        } else if (packId == BaseIconPacks.DEFAULT.id) {
            NaConfig.iconReplacements.setConfigInt(0)
            activePacks.clear()
        } else {
            NaConfig.iconReplacements.setConfigInt(0)
            val pack = IconPackStorage.INSTANCE.findPackById(packId)
            activePacks.clear()
            if (pack != null) {
                activePacks.add(pack)
            }
        }
        rebuildOwnerMap()
        scaledBitmapCache.evictAll()

        AndroidUtilities.runOnUIThread {
            try {
                Theme.reloadAllResources(ApplicationLoader.applicationContext)
            } catch (e: Exception) {
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface)
        }
    }

    fun rebuildOwnerMap() {
        iconOwnerMap.clear()
        for (pack in activePacks) {
            for (key in pack.icons.keys) {
                val cleanKey = key.substringBeforeLast('.')
                iconOwnerMap[key] = pack
                iconOwnerMap[cleanKey] = pack
            }
        }
    }

    fun isIconPack(messageObject: MessageObject?): Boolean {
        if (messageObject == null || messageObject.documentName == null) return false
        val path = MessageHelper.getPathToMessage(messageObject)
        val name = messageObject.documentName
        return (name.endsWith(".icons", ignoreCase = true) || (path != null && path.endsWith(".icons", ignoreCase = true)))
    }

    fun handleIconPack(baseFragment: BaseFragment, messageObject: MessageObject) {
        val path = MessageHelper.getPathToMessage(messageObject)
        if (!TextUtils.isEmpty(path)) {
            handleIconPack(baseFragment, path!!)
        } else if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
            handleIconPack(baseFragment, messageObject.messageOwner.attachPath)
        }
    }

    fun handleIconPack(baseFragment: BaseFragment, path: String) {
        val file = File(path)
        if (!file.exists()) return

        scope.launch {
            val result = IconPackStorage.INSTANCE.parsePackFromZip(file)
            withContext(Dispatchers.Main) {
                when (result) {
                    is IconPackStorageResult.Success -> {
                        val pack = result.value
                        val parentActivity = baseFragment.parentActivity ?: return@withContext
                        baseFragment.showDialog(
                            InstallIconPackBottomSheet(
                                parentActivity,
                                pack,
                                object : InstallIconPackBottomSheet.InstallDelegate {
                                    override fun onInstall(enable: Boolean, update: Boolean) {
                                        scope.launch {
                                            val installRes = IconPackStorage.INSTANCE.installPack(file)
                                            withContext(Dispatchers.Main) {
                                                if (installRes is IconPackStorageResult.Success) {
                                                    if (enable) {
                                                        setActiveCustomPack(pack.id)
                                                    }
                                                    BulletinFactory.of(baseFragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.InstallPack)).show()
                                                } else if (installRes is IconPackStorageResult.Failure) {
                                                    showIconPackError(baseFragment, installRes.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    }
                    is IconPackStorageResult.Failure -> {
                        showIconPackError(baseFragment, result.error)
                    }
                }
            }
        }
    }

    fun showIconPackError(baseFragment: BaseFragment, error: IconPackStorageError) {
        BulletinFactory.of(baseFragment).createSimpleBulletin(R.raw.error, iconPackErrorText(error)).show()
    }

    fun iconPackErrorText(error: IconPackStorageError): String {
        val resId = when (error) {
            IconPackStorageError.INVALID_ARCHIVE -> R.string.IconPackErrorInvalidArchive
            IconPackStorageError.MISSING_METADATA -> R.string.IconPackErrorMissingMetadata
            IconPackStorageError.METADATA_TOO_LARGE -> R.string.IconPackErrorMetadataTooLarge
            IconPackStorageError.INVALID_METADATA -> R.string.IconPackErrorInvalidMetadata
            IconPackStorageError.TOO_MANY_FILES -> R.string.IconPackErrorTooManyFiles
            IconPackStorageError.ARCHIVE_TOO_LARGE -> R.string.IconPackErrorArchiveTooLarge
            IconPackStorageError.FILE_TOO_LARGE -> R.string.IconPackErrorFileTooLarge
            IconPackStorageError.COMPRESSION_RATIO_TOO_HIGH -> R.string.IconPackErrorCompressionRatioTooHigh
            IconPackStorageError.STORAGE_ERROR -> R.string.IconPackErrorStorage
            else -> R.string.UnknownError
        }
        return LocaleController.getString(resId)
    }

    fun getPackIconDrawable(iconPack: IconPack, resId: Int): Drawable? {
        val bitmap = createBitmapFromFile(iconPack, resId, 0, null) ?: return null
        return BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmap)
    }

    fun getDrawable(resId: Int, density: Int, theme: Resources.Theme?): Drawable? {
        val resourceName = getResourceEntryName(resId) ?: return null
        val pack = iconOwnerMap[resourceName] ?: return null
        
        val cacheKey = "$resId-$density"
        val cachedBitmap = scaledBitmapCache.get(cacheKey)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            return BitmapDrawable(ApplicationLoader.applicationContext.resources, cachedBitmap)
        }

        val bitmap = createBitmapFromFile(pack, resId, density, theme) ?: return null
        scaledBitmapCache.put(cacheKey, bitmap)
        return BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmap)
    }

    private fun createBitmapFromFile(pack: IconPack, resId: Int, density: Int, theme: Resources.Theme?): Bitmap? {
        val resourceName = getResourceEntryName(resId) ?: return null
        val customFileName = pack.icons[resourceName]
            ?: pack.icons["$resourceName.png"]
            ?: pack.icons["$resourceName.webp"]
            ?: return null

        val iconFile = IconPackStorage.INSTANCE.resolveIconFile(pack.id, customFileName) ?: return null
        if (!iconFile.exists()) return null

        try {
            // Fetch original drawable WITHOUT calling IconsResources.getDrawable recursively
            val origDrawable = try {
                val res = ApplicationLoader.applicationContext.resources
                if (res is IconsResources) {
                    res.getOriginalDrawable(resId, theme)
                } else {
                    res.getDrawable(resId, theme)
                }
            } catch (e: Exception) {
                null
            }

            val targetW = Math.max(1, if (origDrawable != null && origDrawable.intrinsicWidth > 0) origDrawable.intrinsicWidth else AndroidUtilities.dp(24f))
            val targetH = Math.max(1, if (origDrawable != null && origDrawable.intrinsicHeight > 0) origDrawable.intrinsicHeight else AndroidUtilities.dp(24f))

            if (iconFile.name.endsWith(".svg", ignoreCase = true)) {
                val bitmap = SvgHelper.getBitmap(iconFile, targetW, targetH, false)
                if (bitmap != null) {
                    bitmap.density = if (density > 0) density else AndroidUtilities.displayMetrics.densityDpi
                    return bitmap
                }
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(iconFile.absolutePath, options)
            val rawW = options.outWidth
            val rawH = options.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val decoded = BitmapFactory.decodeFile(iconFile.absolutePath, options) ?: return null

            if (decoded.width == targetW && decoded.height == targetH) {
                decoded.density = if (density > 0) density else AndroidUtilities.displayMetrics.densityDpi
                return decoded
            }

            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
            }
            scaled.density = if (density > 0) density else AndroidUtilities.displayMetrics.densityDpi
            return scaled
        } catch (e: Exception) {
            FileLog.e("Error loading scaled icon bitmap: ${iconFile.absolutePath}", e)
            return null
        }
    }

    private fun getResourceEntryName(resId: Int): String? {
        val cached = resNameCache[resId]
        if (cached != null) return cached
        val name = try {
            ApplicationLoader.applicationContext.resources.getResourceEntryName(resId)
        } catch (e: Exception) {
            null
        }
        if (name != null) {
            resNameCache[resId] = name
        }
        return name
    }

    fun deletePack(packId: String) {
        scope.launch {
            IconPackStorage.INSTANCE.deletePack(packId)
            if (getActivePackId() == packId) {
                setActiveCustomPack(BaseIconPacks.DEFAULT.id)
            }
        }
    }
}
