package com.luoshui.paycardeditor

import android.net.Uri

internal object CardPreviewImageResolver {
    private const val MI_PAY_DOWNLOAD_BASE = "https://did-cdn.pay.xiaomi.com/mfc/download"

    fun resolve(snapshot: CardSnapshot, replacementFace: String = ""): String? {
        val replacement = replacementFace.trim()
        if (replacement.isNotEmpty()) {
            return replacement
        }
        return snapshot.primaryFace.toPreviewUrl()
    }

    private fun String.toPreviewUrl(): String? {
        val value = trim()
        if (value.isEmpty()) {
            return null
        }
        val scheme = Uri.parse(value).scheme
        return if (scheme.isNullOrBlank()) {
            "$MI_PAY_DOWNLOAD_BASE/${value.trimStart('/')}"
        } else {
            value
        }
    }
}
