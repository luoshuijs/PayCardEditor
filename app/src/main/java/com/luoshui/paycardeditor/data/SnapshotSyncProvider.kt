package com.luoshui.paycardeditor.data

import com.luoshui.paycardeditor.core.HookEnvironment
import com.luoshui.paycardeditor.model.CardSnapshot


import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class SnapshotSyncProvider : ContentProvider() {

    companion object {
        private const val TAG = "PayCardEditorProvider"
        private val SNAPSHOT_COMPARATOR = Comparator<CardSnapshot> { left, right ->
            compareValues(left.cardName.isBlank(), right.cardName.isBlank())
                .takeIf { it != 0 }
                ?: compareValues(left.title.lowercase(), right.title.lowercase())
                    .takeIf { it != 0 }
                ?: compareValues(left.cardType, right.cardType)
        }
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val prefs = context?.getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
            ?: return Bundle()
        when (method) {
            HookEnvironment.METHOD_UPSERT_SNAPSHOTS -> upsertSnapshots(prefs, extras)
            HookEnvironment.METHOD_RECORD_ERROR -> recordError(prefs, extras)
            HookEnvironment.METHOD_UPDATE_TROUBLESHOOT_STATE -> updateTroubleshootState(prefs, extras)
            HookEnvironment.METHOD_GET_BANK_RULES -> return Bundle().apply {
                putString(HookEnvironment.EXTRA_RULES_JSON, loadBankRulesJson())
            }
            HookEnvironment.METHOD_GET_CARD_SNAPSHOTS -> return Bundle().apply {
                putString(HookEnvironment.EXTRA_SNAPSHOTS_JSON, loadSnapshotsJson(prefs))
            }
            HookEnvironment.METHOD_UPSERT_DEXKIT_CACHE -> upsertDexKitCache(prefs, extras)
            HookEnvironment.METHOD_GET_DEXKIT_CACHE -> return Bundle().apply {
                putString(HookEnvironment.EXTRA_DEXKIT_CACHE_JSON, loadDexKitCacheJson(prefs))
            }
            HookEnvironment.METHOD_CLEAR_DEXKIT_CACHE -> clearDexKitCache(prefs)
        }
        return Bundle()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? {
        val pathSegments = uri.pathSegments
        val result = if (pathSegments.size == 2 && pathSegments.first() == HookEnvironment.PATH_CARD_ASSETS) {
            "image/png"
        } else {
            null
        }
        Log.i(TAG, "getType uri=$uri result=$result")
        return result
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val pathSegments = uri.pathSegments
        Log.i(TAG, "openFile uri=$uri mode=$mode segments=$pathSegments")
        if (pathSegments.size == 2 && pathSegments.first() == HookEnvironment.PATH_CARD_ASSETS) {
            val file = resolveAssetFile(pathSegments[1]) ?: run {
                Log.w(TAG, "openFile miss assetId=${pathSegments[1]} uri=$uri")
                return null
            }
            Log.i(TAG, "openFile hit assetId=${pathSegments[1]} path=${file.absolutePath} size=${file.length()}")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        Log.w(TAG, "openFile unsupported uri=$uri")
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun upsertSnapshots(prefs: android.content.SharedPreferences, extras: Bundle?) {
        val payload = extras?.getString(HookEnvironment.EXTRA_PAYLOAD).orEmpty()
        val replace = extras?.getBoolean(HookEnvironment.EXTRA_REPLACE, false) == true
        val source = extras?.getString(HookEnvironment.EXTRA_SOURCE).orEmpty()

        val existing = readSnapshots(prefs)
        val merged = if (replace) LinkedHashMap<String, CardSnapshot>() else LinkedHashMap(existing)
        parseSnapshots(payload).forEach { snapshot ->
            val previous = existing[snapshot.key]
            merged[snapshot.key] = snapshot.mergeWith(previous)
        }

        val sortedSnapshots = ArrayList(merged.values)
        sortedSnapshots.sortWith(SNAPSHOT_COMPARATOR)
        val json = JSONArray().apply {
            sortedSnapshots.forEach { put(it.toJson()) }
        }

        prefs.edit {
            putString(HookEnvironment.PREF_KEY_CARD_SNAPSHOTS, json.toString())
                .putLong(HookEnvironment.PREF_KEY_LAST_UPDATED, System.currentTimeMillis())
                .putString(HookEnvironment.PREF_KEY_LAST_SOURCE, source)
                .putString(HookEnvironment.PREF_KEY_LAST_ERROR, "")
        }
    }

    private fun recordError(prefs: android.content.SharedPreferences, extras: Bundle?) {
        prefs.edit {
            putLong(HookEnvironment.PREF_KEY_LAST_UPDATED, System.currentTimeMillis())
                .putString(
                    HookEnvironment.PREF_KEY_LAST_SOURCE,
                    extras?.getString(HookEnvironment.EXTRA_SOURCE).orEmpty()
                )
                .putString(
                    HookEnvironment.PREF_KEY_LAST_ERROR,
                    extras?.getString(HookEnvironment.EXTRA_ERROR).orEmpty()
                )
        }
    }

    private fun updateTroubleshootState(prefs: android.content.SharedPreferences, extras: Bundle?) {
        prefs.edit {
            putString(
                HookEnvironment.PREF_KEY_DEBUG_STATUS,
                extras?.getString(HookEnvironment.EXTRA_DEBUG_STATUS).orEmpty()
            ).putString(
                HookEnvironment.PREF_KEY_HOOK_METHODS,
                extras?.getString(HookEnvironment.EXTRA_HOOK_METHODS).orEmpty()
            ).putLong(
                HookEnvironment.PREF_KEY_TROUBLESHOOT_UPDATED_AT,
                System.currentTimeMillis()
            )
        }
    }

    private fun readSnapshots(prefs: android.content.SharedPreferences): LinkedHashMap<String, CardSnapshot> {
        val raw = prefs.getString(HookEnvironment.PREF_KEY_CARD_SNAPSHOTS, "[]").orEmpty()
        return parseSnapshots(raw).associateByTo(LinkedHashMap()) { it.key }
    }

    private fun parseSnapshots(raw: String): List<CardSnapshot> = runCatching {
        val jsonArray = JSONArray(if (raw.isBlank()) "[]" else raw)
        buildList(jsonArray.length()) {
            repeat(jsonArray.length()) { index ->
                val item = jsonArray.optJSONObject(index) ?: JSONObject()
                add(CardSnapshot.fromJson(item))
            }
        }
    }.getOrElse { emptyList() }

    private fun loadBankRulesJson(): String = runCatching {
        val prefs = context?.getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.getString(HookEnvironment.PREF_KEY_BANK_RULES, "[]").orEmpty().ifBlank { "[]" }
    }.getOrDefault("[]")

    private fun loadSnapshotsJson(prefs: android.content.SharedPreferences): String =
        prefs.getString(HookEnvironment.PREF_KEY_CARD_SNAPSHOTS, "[]").orEmpty().ifBlank { "[]" }

    /**
     * Persists a serialized DexKit descriptor bundle keyed by host APK + module identity.
     * The provider performs no schema validation; the hook side is the source of truth for
     * cache shape and is responsible for invalidating on mismatch.
     */
    private fun upsertDexKitCache(prefs: android.content.SharedPreferences, extras: Bundle?) {
        val payload = extras?.getString(HookEnvironment.EXTRA_DEXKIT_CACHE_JSON).orEmpty()
        prefs.edit {
            putString(HookEnvironment.PREF_KEY_DEXKIT_CACHE, payload)
        }
    }

    private fun loadDexKitCacheJson(prefs: android.content.SharedPreferences): String =
        prefs.getString(HookEnvironment.PREF_KEY_DEXKIT_CACHE, "").orEmpty()

    private fun clearDexKitCache(prefs: android.content.SharedPreferences) {
        prefs.edit { remove(HookEnvironment.PREF_KEY_DEXKIT_CACHE) }
    }

    private fun resolveAssetFile(assetId: String): java.io.File? {
        val baseDir = context?.filesDir?.resolve(HookEnvironment.CARD_ASSET_DIRECTORY) ?: run {
            Log.w(TAG, "resolveAssetFile missing baseDir assetId=$assetId")
            return null
        }
        val file = baseDir.resolve("$assetId.png")
        val exists = file.exists()
        val isFile = file.isFile
        Log.i(
            TAG,
            "resolveAssetFile assetId=$assetId path=${file.absolutePath} exists=$exists isFile=$isFile size=${if (exists) file.length() else -1}"
        )
        return file.takeIf { exists && isFile }
    }
}
