package com.luoshui.paycardeditor.hook

import com.luoshui.paycardeditor.model.CardSnapshot
import org.json.JSONArray
import org.json.JSONObject

internal data class BankCardRule(
    val enabled: Boolean = true,
    val matchCardType: String = "",
    val matchAid: String = "",
    val matchPanLastDigits: String = "",
    val matchProductId: String = "",
    val matchCardName: String = "",
    val matchCid: String = "",
    val matchVcUid: String = "",
    val replaceCardArt: String = "",
    val replaceCardFrontColor: String = "",
    val replaceBankLogoUrl: String = "",
    val replaceBankLogoWithNameUrl: String = "",
) {
    fun matches(snapshot: CardSnapshot): Boolean {
        if (!enabled) {
            return false
        }
        return matchesValue(matchCardType, snapshot.cardType)
            && matchesValue(matchAid, snapshot.aid)
            && matchesValue(matchPanLastDigits, snapshot.panLastDigits)
            && matchesValue(matchProductId, snapshot.productId)
            && matchesValue(matchCardName, snapshot.cardName)
            && matchesValue(matchCid, snapshot.cid)
            && matchesValue(matchVcUid, snapshot.vcUid)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("matchCardType", matchCardType)
        .put("matchAid", matchAid)
        .put("matchPanLastDigits", matchPanLastDigits)
        .put("matchProductId", matchProductId)
        .put("matchCardName", matchCardName)
        .put("matchCid", matchCid)
        .put("matchVcUid", matchVcUid)
        .put("replaceCardArt", replaceCardArt)
        .put("replaceCardFrontColor", replaceCardFrontColor)
        .put("replaceBankLogoUrl", replaceBankLogoUrl)
        .put("replaceBankLogoWithNameUrl", replaceBankLogoWithNameUrl)

    companion object {
        fun parseList(raw: String): List<BankCardRule> = runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            buildList(array.length()) {
                repeat(array.length()) { index ->
                    add(fromJson(array.optJSONObject(index) ?: JSONObject()))
                }
            }
        }.getOrElse { emptyList() }

        private fun fromJson(json: JSONObject): BankCardRule = BankCardRule(
            enabled = json.optBoolean("enabled", true),
            matchCardType = normalize(json.optString("matchCardType")),
            matchAid = normalize(json.optString("matchAid")),
            matchPanLastDigits = normalize(json.optString("matchPanLastDigits")),
            matchProductId = normalize(json.optString("matchProductId")),
            matchCardName = normalize(json.optString("matchCardName")),
            matchCid = normalize(json.optString("matchCid")),
            matchVcUid = normalize(json.optString("matchVcUid")),
            replaceCardArt = normalize(json.optString("replaceCardArt")),
            replaceCardFrontColor = normalize(json.optString("replaceCardFrontColor")),
            replaceBankLogoUrl = normalize(json.optString("replaceBankLogoUrl")),
            replaceBankLogoWithNameUrl = normalize(json.optString("replaceBankLogoWithNameUrl")),
        )

        private fun matchesValue(expected: String, actual: String): Boolean {
            if (expected.isBlank()) {
                return true
            }
            return normalize(expected).equals(normalize(actual), ignoreCase = true)
        }

        private fun normalize(value: String?): String = value?.trim().orEmpty()
    }
}
