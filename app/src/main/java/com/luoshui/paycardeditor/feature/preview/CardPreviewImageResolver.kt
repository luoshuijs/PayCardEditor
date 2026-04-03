package com.luoshui.paycardeditor.feature.preview

import com.luoshui.paycardeditor.model.CardSnapshot


import androidx.core.net.toUri

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
        val scheme = value.toUri().scheme
        return if (scheme.isNullOrBlank()) {
            "$MI_PAY_DOWNLOAD_BASE/${value.trimStart('/')}"
        } else {
            value
        }
    }
}
