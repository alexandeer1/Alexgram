package com.exteragram.messenger.icons

import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.File

object IconPackStorage {
    val INSTANCE = this

    fun getIconPacksDirectory(): File {
        val dir = File(ApplicationLoader.applicationContext.filesDir, "icon_packs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCustomPacks(): List<IconPack> {
        val root = getIconPacksDirectory()
        val list = mutableListOf<IconPack>()
        val files = root.listFiles() ?: return list
        for (f in files) {
            if (f.isDirectory) {
                val pack = findAndParseMetadata(f)
                if (pack != null) {
                    list.add(pack)
                }
            }
        }
        return list
    }

    fun findPackById(packId: String): IconPack? {
        val root = getIconPacksDirectory()
        val packDir = File(root, packId)
        if (!packDir.exists() || !packDir.isDirectory) return null
        return findAndParseMetadata(packDir)
    }

    fun findMetadataFile(dir: File): File? {
        if (!dir.exists()) return null
        val directMeta = File(dir, "metadata.json")
        if (directMeta.exists() && directMeta.isFile) return directMeta
        val directIconPack = File(dir, "iconpack.json")
        if (directIconPack.exists() && directIconPack.isFile) return directIconPack

        // Recursive search for metadata.json or iconpack.json
        return dir.walkTopDown().firstOrNull { file ->
            file.isFile && (file.name.equals("metadata.json", ignoreCase = true) || file.name.equals("iconpack.json", ignoreCase = true))
        }
    }

    fun findAndParseMetadata(dir: File): IconPack? {
        val metaFile = findMetadataFile(dir) ?: return null
        return parseMetadataFile(metaFile)
    }

    fun parseMetadataFile(file: File): IconPack? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val jsonText = file.readText()
            val json = JSONObject(jsonText)
            parseMetadata(json, file.parentFile)
        } catch (e: Exception) {
            FileLog.e("Error parsing icon pack metadata: ${file.absolutePath}", e)
            null
        }
    }

    fun parseMetadata(json: JSONObject, location: File?): IconPack {
        val id = json.optString("packId", json.optString("id", "")).trim()
        val name = json.optString("packName", json.optString("name", "Unnamed Pack")).trim()
        val author = json.optString("author", "Unknown").trim()
        val version = json.optString("version", "1.0").trim()
        val iconsObj = json.optJSONObject("icons")
        val map = mutableMapOf<String, String>()
        if (iconsObj != null) {
            val keys = iconsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = iconsObj.optString(key)
            }
        }
        return IconPack(id, name, author, version, map, null, location)
    }

    fun resolveIconFile(packId: String, fileName: String): File? {
        val root = getIconPacksDirectory()
        val packDir = File(root, packId)
        val file = File(packDir, fileName)
        if (file.exists()) return file

        // Fallback: check if file exists anywhere inside packDir
        val resolved = packDir.walkTopDown().firstOrNull { f ->
            f.isFile && (f.name.equals(fileName, ignoreCase = true) || f.name.equals("$fileName.png", ignoreCase = true) || f.name.equals("$fileName.webp", ignoreCase = true))
        }
        return resolved
    }

    suspend fun parsePackFromZip(zipFile: File): IconPackStorageResult<IconPack> {
        if (!zipFile.exists() || !zipFile.isFile) {
            return IconPackStorageResult.Failure(IconPackStorageError.INVALID_ARCHIVE)
        }
        return try {
            val tempDir = File(ApplicationLoader.applicationContext.cacheDir, "temp_iconpack_" + System.currentTimeMillis())
            tempDir.mkdirs()
            unzip(zipFile, tempDir)
            val metaFile = findMetadataFile(tempDir)
            val pack = if (metaFile != null) parseMetadataFile(metaFile) else null
            if (pack != null && pack.id.isNotBlank()) {
                IconPackStorageResult.Success(pack)
            } else {
                tempDir.deleteRecursively()
                IconPackStorageResult.Failure(IconPackStorageError.MISSING_METADATA)
            }
        } catch (e: Exception) {
            FileLog.e("Error parsing pack from zip: ${zipFile.absolutePath}", e)
            IconPackStorageResult.Failure(IconPackStorageError.INVALID_ARCHIVE)
        }
    }

    suspend fun installPack(zipFile: File): IconPackStorageResult<Unit> {
        val parsed = parsePackFromZip(zipFile)
        if (parsed is IconPackStorageResult.Failure) {
            return IconPackStorageResult.Failure(parsed.error)
        }
        val pack = (parsed as IconPackStorageResult.Success).value
        val destDir = File(getIconPacksDirectory(), pack.id)
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()
        
        // Unzip directly or flatten if wrapped in top-level directory
        val tempDir = File(ApplicationLoader.applicationContext.cacheDir, "temp_install_" + System.currentTimeMillis())
        tempDir.mkdirs()
        unzip(zipFile, tempDir)

        val metaFile = findMetadataFile(tempDir)
        if (metaFile != null && metaFile.parentFile != null) {
            metaFile.parentFile!!.copyRecursively(destDir, overwrite = true)
        } else {
            tempDir.copyRecursively(destDir, overwrite = true)
        }
        tempDir.deleteRecursively()

        return IconPackStorageResult.Success(Unit)
    }

    fun deletePack(packId: String) {
        val destDir = File(getIconPacksDirectory(), packId)
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
    }

    fun saveIconPackMetadata(pack: IconPack): Boolean {
        val packDir = File(getIconPacksDirectory(), pack.id)
        if (!packDir.exists()) {
            packDir.mkdirs()
        }
        val json = JSONObject()
        json.put("schemaVersion", 1)
        json.put("packId", pack.id)
        json.put("packName", pack.name)
        json.put("author", pack.author)
        json.put("version", pack.version)
        val iconsObj = JSONObject()
        for ((k, v) in pack.icons) {
            iconsObj.put(k, v)
        }
        json.put("icons", iconsObj)
        val metaFile = File(packDir, "metadata.json")
        return try {
            metaFile.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun bundlePackBlocking(packId: String): File? {
        val packDir = File(getIconPacksDirectory(), packId)
        if (!packDir.exists()) return null
        val zipFile = File(ApplicationLoader.applicationContext.cacheDir, "$packId.icons")
        if (zipFile.exists()) zipFile.delete()
        return try {
            zipDirectory(packDir, zipFile)
            zipFile
        } catch (e: Exception) {
            null
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name.contains("__MACOSX") || entry.name.startsWith(".")) {
                    continue
                }
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    newFile.outputStream().use { os ->
                        zis.copyTo(os)
                    }
                }
            }
        }
    }

    private fun zipDirectory(dir: File, zipFile: File) {
        java.util.zip.ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
            dir.walkTopDown().forEach { file ->
                if (file.isFile && !file.name.startsWith(".")) {
                    val relativePath = dir.toPath().relativize(file.toPath()).toString()
                    val entry = java.util.zip.ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}
