package com.luoshui.paycardeditor.model

import org.json.JSONObject

data class CardSnapshot(
    val aid: String = "",
    val cardType: String = "",
    val cardName: String = "",
    val cardNo: String = "",
    val realCardNo: String = "",
    val issuerName: String = "",
    val productId: String = "",
    val cid: String = "",
    val vcUid: String = "",
    val panLastDigits: String = "",
    val cardArt: String = "",
    val cardFrontColor: String = "",
    val personalCardFace: String = "",
    val issuedListBgHd: String = "",
    val issuedListBg: String = "",
    val logo: String = "",
    val logoWithName: String = "",
    val isBankCard: Boolean = false,
    val isTransCard: Boolean = false,
    val isMifareCard: Boolean = false,
    val isCarKeyCard: Boolean = false,
) {
    val supportsCustomCardArt: Boolean
        get() = isBankCard || isTransCard || isMifareCard || isCarKeyCard

    val key: String
        get() = listOf(
            normalize(cardType),
            normalize(aid),
            normalize(cardNo),
            normalize(realCardNo),
            normalize(cid),
            normalize(vcUid),
            normalize(productId),
            normalize(panLastDigits),
            normalize(cardName),
            normalize(issuerName),
        ).filter { it.isNotBlank() }.ifEmpty { listOf("unknown") }.joinToString("#")

    val title: String
        get() = firstNonBlank(cardName, issuerName, productId, panLastDigits, aid, "未命名卡片")

    val primaryFace: String
        get() = firstNonBlank(personalCardFace, issuedListBgHd, issuedListBg, cardArt, logoWithName, logo)

    val categoryLabel: String
        get() = when {
            isBankCard -> "银行卡"
            isTransCard -> "交通卡"
            isMifareCard -> "Mifare/门禁卡"
            isCarKeyCard -> "车钥匙"
            cardType.isNotBlank() -> cardType
            else -> "未知卡种"
        }

    val secondaryLabel: String
        get() = firstNonBlank(panLastDigits, cardType, productId, aid, "暂无")

    fun mergeWith(previous: CardSnapshot?): CardSnapshot {
        if (previous == null) {
            return this
        }
        return copy(
            aid = firstNonBlank(aid, previous.aid),
            cardType = firstNonBlank(cardType, previous.cardType),
            cardName = firstNonBlank(cardName, previous.cardName),
            cardNo = firstNonBlank(cardNo, previous.cardNo),
            realCardNo = firstNonBlank(realCardNo, previous.realCardNo),
            issuerName = firstNonBlank(issuerName, previous.issuerName),
            productId = firstNonBlank(productId, previous.productId),
            cid = firstNonBlank(cid, previous.cid),
            vcUid = firstNonBlank(vcUid, previous.vcUid),
            panLastDigits = firstNonBlank(panLastDigits, previous.panLastDigits),
            cardArt = firstNonBlank(cardArt, previous.cardArt),
            cardFrontColor = firstNonBlank(cardFrontColor, previous.cardFrontColor),
            personalCardFace = firstNonBlank(personalCardFace, previous.personalCardFace),
            issuedListBgHd = firstNonBlank(issuedListBgHd, previous.issuedListBgHd),
            issuedListBg = firstNonBlank(issuedListBg, previous.issuedListBg),
            logo = firstNonBlank(logo, previous.logo),
            logoWithName = firstNonBlank(logoWithName, previous.logoWithName),
            isBankCard = isBankCard || previous.isBankCard,
            isTransCard = isTransCard || previous.isTransCard,
            isMifareCard = isMifareCard || previous.isMifareCard,
            isCarKeyCard = isCarKeyCard || previous.isCarKeyCard,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("aid", aid)
        .put("cardType", cardType)
        .put("cardName", cardName)
        .put("cardNo", cardNo)
        .put("realCardNo", realCardNo)
        .put("issuerName", issuerName)
        .put("productId", productId)
        .put("cid", cid)
        .put("vcUid", vcUid)
        .put("panLastDigits", panLastDigits)
        .put("cardArt", cardArt)
        .put("cardFrontColor", cardFrontColor)
        .put("personalCardFace", personalCardFace)
        .put("issuedListBgHd", issuedListBgHd)
        .put("issuedListBg", issuedListBg)
        .put("logo", logo)
        .put("logoWithName", logoWithName)
        .put("isBankCard", isBankCard)
        .put("isTransCard", isTransCard)
        .put("isMifareCard", isMifareCard)
        .put("isCarKeyCard", isCarKeyCard)

    companion object {
        fun fromJson(jsonObject: JSONObject): CardSnapshot = CardSnapshot(
            aid = normalize(jsonObject.optString("aid")),
            cardType = normalize(jsonObject.optString("cardType")),
            cardName = normalize(jsonObject.optString("cardName")),
            cardNo = normalize(jsonObject.optString("cardNo")),
            realCardNo = normalize(jsonObject.optString("realCardNo")),
            issuerName = normalize(jsonObject.optString("issuerName")),
            productId = normalize(jsonObject.optString("productId")),
            cid = normalize(jsonObject.optString("cid")),
            vcUid = normalize(jsonObject.optString("vcUid")),
            panLastDigits = normalize(jsonObject.optString("panLastDigits")),
            cardArt = normalize(jsonObject.optString("cardArt")),
            cardFrontColor = normalize(jsonObject.optString("cardFrontColor")),
            personalCardFace = normalize(jsonObject.optString("personalCardFace")),
            issuedListBgHd = normalize(jsonObject.optString("issuedListBgHd")),
            issuedListBg = normalize(jsonObject.optString("issuedListBg")),
            logo = normalize(jsonObject.optString("logo")),
            logoWithName = normalize(jsonObject.optString("logoWithName")),
            isBankCard = jsonObject.optBoolean("isBankCard"),
            isTransCard = jsonObject.optBoolean("isTransCard"),
            isMifareCard = jsonObject.optBoolean("isMifareCard"),
            isCarKeyCard = jsonObject.optBoolean("isCarKeyCard"),
        )

        fun normalize(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
        }
    }
}

data class CardSnapshotState(
    val cards: List<CardSnapshot> = emptyList(),
    val lastUpdated: Long = 0L,
    val lastSource: String = "",
    val warning: String = "",
)

data class HomeState(
    val moduleStatus: ModuleStatusState,
    val cardState: CardSnapshotState,
)

data class ModuleStatusState(
    val level: ModuleStatusLevel,
    val title: String,
    val detail: String,
)

enum class ModuleStatusLevel {
    ACTIVE,
    WAITING,
    INACTIVE,
}

internal fun firstNonBlank(vararg values: String): String =
    values.firstOrNull { it.isNotBlank() }.orEmpty()
