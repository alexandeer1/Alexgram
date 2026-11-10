package tw.nekomimi.nekogram.translate

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.utils.UIUtil
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

val Locale.transDb: TranslateDb
    get() = TranslateDb.repo.getOrPut(this) { TranslateDb(locale2code) }

val String.transDbByCode: TranslateDb
    get() = code2Locale.transDb

class TranslateDb(val code: String) {

    private val memCache = ConcurrentHashMap<String, String>()

    companion object {
        private var dbHelper: DBHelper? = null
        val repo = ConcurrentHashMap<Locale, TranslateDb>()
        private val chatLanguages = ConcurrentHashMap<Long, String>()
        private val chatCCTargets = ConcurrentHashMap<Long, String>()

        private fun getHelper(): DBHelper {
            if (dbHelper == null) {
                synchronized(TranslateDb::class.java) {
                    if (dbHelper == null) {
                        val ctx = ApplicationLoader.applicationContext
                        dbHelper = DBHelper(ctx)
                    }
                }
            }
            return dbHelper!!
        }

        @JvmStatic
        fun getChatLanguage(chatId: Long, default: Locale): Locale {
            if (chatId == 0L) return default
            val cached = chatLanguages[chatId]
            if (cached != null) return cached.code2Locale

            return try {
                val db = getHelper().readableDatabase
                db.rawQuery("SELECT lang FROM chat_lang WHERE chat_id = ?", arrayOf(chatId.toString())).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val lang = cursor.getString(0)
                        chatLanguages[chatId] = lang
                        lang.code2Locale
                    } else {
                        default
                    }
                }
            } catch (e: Exception) {
                default
            }
        }

        @JvmStatic
        fun saveChatLanguage(chatId: Long, locale: Locale) = UIUtil.runOnIoDispatcher {
            chatLanguages[chatId] = locale.locale2code
            try {
                val db = getHelper().writableDatabase
                val values = ContentValues().apply {
                    put("chat_id", chatId)
                    put("lang", locale.locale2code)
                }
                db.insertWithOnConflict("chat_lang", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            } catch (_: Exception) {
            }
        }

        @JvmStatic
        fun getChatCCTarget(chatId: Long, default: String?): String? {
            if (chatId == 0L) return default
            val cached = chatCCTargets[chatId]
            if (cached != null) return cached

            return try {
                val db = getHelper().readableDatabase
                db.rawQuery("SELECT target FROM chat_cc WHERE chat_id = ?", arrayOf(chatId.toString())).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val target = cursor.getString(0)
                        chatCCTargets[chatId] = target
                        target
                    } else {
                        default
                    }
                }
            } catch (e: Exception) {
                default
            }
        }

        @JvmStatic
        fun saveChatCCTarget(chatId: Long, target: String) = UIUtil.runOnIoDispatcher {
            chatCCTargets[chatId] = target
            try {
                val db = getHelper().writableDatabase
                val values = ContentValues().apply {
                    put("chat_id", chatId)
                    put("target", target)
                }
                db.insertWithOnConflict("chat_cc", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            } catch (_: Exception) {
            }
        }

        @JvmStatic
        fun currentTarget(): TranslateDb {
            val code = NekoConfig.translateToLang.String()
            return if (!code.isNullOrBlank()) code.code2Locale.transDb else LocaleController.getInstance().currentLocale.transDb
        }

        @JvmStatic
        fun forLocale(locale: Locale): TranslateDb = locale.transDb

        @JvmStatic
        fun currentInputTarget(): TranslateDb {
            val code = NekoConfig.translateInputLang.String()
            return if (!code.isNullOrBlank()) code.code2Locale.transDb else LocaleController.getInstance().currentLocale.transDb
        }

        @JvmStatic
        fun clearAll() {
            repo.clear()
            chatLanguages.clear()
            chatCCTargets.clear()
            try {
                val db = getHelper().writableDatabase
                db.delete("trans_cache", null, null)
                db.delete("chat_lang", null, null)
                db.delete("chat_cc", null, null)
            } catch (_: Exception) {
            }
        }

        private class DBHelper(context: Context) : SQLiteOpenHelper(context, "translate_caches.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS trans_cache (code TEXT, text TEXT, trans TEXT, PRIMARY KEY (code, text))")
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_lang (chat_id INTEGER PRIMARY KEY, lang TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_cc (chat_id INTEGER PRIMARY KEY, target TEXT)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.execSQL("DROP TABLE IF EXISTS trans_cache")
                db.execSQL("DROP TABLE IF EXISTS chat_lang")
                db.execSQL("DROP TABLE IF EXISTS chat_cc")
                onCreate(db)
            }
        }
    }

    fun contains(text: String): Boolean {
        if (memCache.containsKey(text)) return true
        return query(text) != null
    }

    fun save(text: String, trans: String) {
        memCache[text] = trans
        try {
            val db = getHelper().writableDatabase
            val values = ContentValues().apply {
                put("code", code)
                put("text", text)
                put("trans", trans)
            }
            db.insertWithOnConflict("trans_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {
        }
    }

    fun query(text: String): String? {
        memCache[text]?.let { return it }
        return try {
            val db = getHelper().readableDatabase
            db.rawQuery("SELECT trans FROM trans_cache WHERE code = ? AND text = ?", arrayOf(code, text)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    memCache[text] = result
                    result
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        memCache.clear()
        try {
            val db = getHelper().writableDatabase
            db.delete("trans_cache", "code = ?", arrayOf(code))
        } catch (_: Exception) {
        }
    }
}
