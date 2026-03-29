package com.luoshui.paycardeditor

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CardSnapshotFormatter {
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.getDefault())

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) {
            return "尚未同步"
        }
        return timestampFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }

    fun buildPreview(cards: List<CardSnapshot>, maxItems: Int = 3): String {
        if (cards.isEmpty()) {
            return "暂无卡片快照"
        }
        return cards.take(maxItems).joinToString("\n\n") { snapshot ->
            buildString {
                append(snapshot.title)
                append(" · ")
                append(categoryLabel(snapshot))
                val face = snapshot.primaryFace
                if (face.isNotBlank()) {
                    append("\n")
                    append(shorten(face, 84))
                }
            }
        }
    }

    fun buildDialogText(state: HomeState): String = buildString {
        append("状态: ")
        append(state.moduleStatus.title)
        append('\n')
        append(state.moduleStatus.detail)
        append("\n\n")
        append("快照数量: ")
        append(state.cardState.cards.size)
        append('\n')
        append("最近同步: ")
        append(formatTimestamp(state.cardState.lastUpdated))
        append('\n')
        append("最近来源: ")
        append(if (state.cardState.lastSource.isBlank()) "暂无" else state.cardState.lastSource)

        if (state.cardState.cards.isEmpty()) {
            append("\n\n")
            append(if (state.cardState.warning.isBlank()) "暂无卡片快照。" else state.cardState.warning)
            return@buildString
        }

        state.cardState.cards.forEachIndexed { index, snapshot ->
            append("\n\n[")
            append(index + 1)
            append("] ")
            append(snapshot.title)
            append('\n')
            append("卡种: ")
            append(categoryLabel(snapshot))
            if (snapshot.cardType.isNotBlank()) {
                append(" / ")
                append(snapshot.cardType)
            }
            append('\n')
            append("AID: ")
            append(snapshot.aid.ifBlank { "--" })
            append('\n')
            append("cardNo: ")
            append(snapshot.cardNo.ifBlank { "--" })
            append('\n')
            append("realCardNo: ")
            append(snapshot.realCardNo.ifBlank { "--" })
            append('\n')
            append("productId: ")
            append(snapshot.productId.ifBlank { "--" })
            append('\n')
            append("panLastDigits: ")
            append(snapshot.panLastDigits.ifBlank { "--" })
            append('\n')
            append("cid: ")
            append(snapshot.cid.ifBlank { "--" })
            append('\n')
            append("vcUid: ")
            append(snapshot.vcUid.ifBlank { "--" })
            append('\n')
            append("cardArt: ")
            append(snapshot.cardArt.ifBlank { "--" })
            append('\n')
            append("cardFrontColor: ")
            append(snapshot.cardFrontColor.ifBlank { "--" })
            append('\n')
            append("personalCardFace: ")
            append(snapshot.personalCardFace.ifBlank { "--" })
            append('\n')
            append("issuedListBgHd: ")
            append(snapshot.issuedListBgHd.ifBlank { "--" })
            append('\n')
            append("issuedListBg: ")
            append(snapshot.issuedListBg.ifBlank { "--" })
            if (snapshot.logo.isNotBlank()) {
                append('\n')
                append("logo: ")
                append(snapshot.logo)
            }
            if (snapshot.logoWithName.isNotBlank()) {
                append('\n')
                append("logoWithName: ")
                append(snapshot.logoWithName)
            }
        }
    }

    private fun categoryLabel(snapshot: CardSnapshot): String = snapshot.categoryLabel

    private fun shorten(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }
        return value.take(maxLength - 3) + "..."
    }
}
