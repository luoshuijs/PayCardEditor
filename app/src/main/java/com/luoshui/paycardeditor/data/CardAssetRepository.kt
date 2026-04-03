package com.luoshui.paycardeditor.data

import com.luoshui.paycardeditor.app.App
import com.luoshui.paycardeditor.core.HookEnvironment


import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CardAsset(
    val id: String,
    val displayName: String,
    val fileName: String,
    val updatedAt: Long,
) {
    val absolutePath: String
        get() = CardAssetRepository.assetDirectory.resolve(fileName).absolutePath

    fun contentUri(): Uri = Uri.Builder()
        .scheme("content")
        .authority(HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY)
        .appendPath(HookEnvironment.PATH_CARD_ASSETS)
        .appendPath(id)
        .build()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("displayName", displayName)
        .put("fileName", fileName)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(jsonObject: JSONObject): CardAsset = CardAsset(
            id = jsonObject.optString("id").ifBlank { UUID.randomUUID().toString() },
            displayName = jsonObject.optString("displayName").ifBlank { "未命名卡面" },
            fileName = jsonObject.optString("fileName").ifBlank { "${jsonObject.optString("id")}.png" },
            updatedAt = jsonObject.optLong("updatedAt", System.currentTimeMillis()),
        )
    }
}

object CardAssetRepository {

    private const val PNG_SUFFIX = ".png"

    internal val assetDirectory: File
        get() = requireContext().filesDir.resolve(HookEnvironment.CARD_ASSET_DIRECTORY).apply { mkdirs() }

    fun listAssets(): List<CardAsset> {
        val metadata = loadMetadata()
        val assets = metadata.filter { File(it.absolutePath).exists() }
        val missingIds = metadata.map { it.id }.toSet() - assets.map { it.id }.toSet()
        if (missingIds.isNotEmpty()) {
            saveMetadata(assets)
        }
        return assets.sortedByDescending { it.updatedAt }
    }

    fun saveAsset(sourceFile: File, displayName: String, existingAssetId: String? = null): CardAsset {
        val safeName = sanitizeDisplayName(displayName)
        val metadata = loadMetadata().toMutableList()
        val now = System.currentTimeMillis()
        val generatedId = existingAssetId ?: UUID.randomUUID().toString()
        val asset = metadata.firstOrNull { it.id == existingAssetId }?.copy(
            displayName = safeName,
            updatedAt = now,
        ) ?: CardAsset(
            id = generatedId,
            displayName = safeName,
            fileName = "$generatedId$PNG_SUFFIX",
            updatedAt = now,
        )
        val normalizedAsset = if (asset.fileName.endsWith(PNG_SUFFIX)) asset else asset.copy(fileName = "${asset.id}$PNG_SUFFIX")
        sourceFile.copyTo(File(normalizedAsset.absolutePath), overwrite = true)
        metadata.removeAll { it.id == normalizedAsset.id }
        metadata += normalizedAsset
        saveMetadata(metadata)
        return normalizedAsset
    }

    fun deleteAsset(assetId: String): CardAsset? {
        val metadata = loadMetadata().toMutableList()
        val asset = metadata.firstOrNull { it.id == assetId } ?: return null
        File(asset.absolutePath).delete()
        metadata.removeAll { it.id == assetId }
        saveMetadata(metadata)
        return asset
    }

    fun findAsset(assetId: String): CardAsset? = listAssets().firstOrNull { it.id == assetId }

    fun createTempFile(prefix: String): File = File.createTempFile(prefix, ".png", requireContext().cacheDir)

    fun inferDisplayName(fileName: String?): String {
        val rawName = fileName?.substringBeforeLast('.')?.trim().orEmpty()
        return sanitizeDisplayName(rawName)
    }

    private fun loadMetadata(): List<CardAsset> {
        val raw = prefs().getString(HookEnvironment.PREF_KEY_CARD_ASSETS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            buildList(array.length()) {
                repeat(array.length()) { index ->
                    add(CardAsset.fromJson(array.optJSONObject(index) ?: JSONObject()))
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveMetadata(assets: List<CardAsset>) {
        val array = JSONArray()
        assets.sortedByDescending { it.updatedAt }.forEach { array.put(it.toJson()) }
        prefs().edit { putString(HookEnvironment.PREF_KEY_CARD_ASSETS, array.toString()) }
    }

    private fun prefs() = requireContext().getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)

    private fun requireContext(): Context = checkNotNull(App.appContext) { "Application context not ready" }

    private fun sanitizeDisplayName(value: String): String = value.ifBlank { "卡面 ${System.currentTimeMillis()}" }
}
