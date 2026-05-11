package com.luoshui.paycardeditor.hook.card

import com.luoshui.paycardeditor.hook.ReflectionCacheUtils
import com.luoshui.paycardeditor.model.CardSnapshot
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Pre-resolved reflection accessors for a specific card class.
 * Holds cached Method/Field references to avoid repeated lookups.
 */
internal class CardClassAccessors private constructor(
    private val cardClass: Class<*>
) {
    // Methods
    val serializeMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "serialize")
    val getAidMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getAid")
    val getCardTypeMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getCardType")
    val getCardNameMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getCardName")
    val getProductNameMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getProductName")
    val getCardNoMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getCardNo")
    val getProductIdMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getProductId")
    val getCidMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getCid")
    val getVcUidMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getVcUid")
    val getCardArtMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "getCardArt")
    val isBankCardMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "isBankCard")
    val isTransCardMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "isTransCard")
    val isMiFareCardMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "isMiFareCard")
    val isTraditionalCarKeyCardMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "isTraditionalCarKeyCard")
    val isCardActiveMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "isCardActive")
    val isServiceStatusIssuedMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "isServiceStatusIssued")
    val isDummyMethod: Method? = ReflectionCacheUtils.findZeroArgMethodIfExists(cardClass, "isDummy")

    // Fields
    val mCardUIInfoField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mCardUIInfo")
    val mCardProductNameField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mCardProductName")
    val mProductNameField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mProductName")
    val mBankNameField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mBankName")
    val mCardNoField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mCardNo")
    val mRealCardNoField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mRealCardNo")
    val mIssuerNameField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mIssuerName")
    val mProductIdField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mProductId")
    val mCardProductTypeIdField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mCardProductTypeId")
    val mCIdField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mCId")
    val mVcUidField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mVcUid")
    val mPanLastDigitsField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mPanLastDigits")
    val mCardArtField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mCardArt")
    val mCardFrontColorField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mCardFrontColor")
    val mBankLogoUrlField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mBankLogoUrl")
    val mBankLogoWithNameUrlField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mBankLogoWithNameUrl")
    val mHasIssueField: Field? = ReflectionCacheUtils.findFieldIfExists(cardClass, "mHasIssue")

    // Lazy-initialized UI info accessors (only if mCardUIInfo class exists)
    private var uiInfoClass: Class<*>? = null
    private var uiInfoAccessorsInitialized = false
    private var mPersonalCardFaceField: Field? = null
    private var mCardIssuedListBgHdUrlField: Field? = null
    private var mCardIssuedListBgUrlField: Field? = null
    private var mCardLogoUrlField: Field? = null

    private fun ensureUIInfoAccessors() {
        if (uiInfoAccessorsInitialized) return
        synchronized(this) {
            if (uiInfoAccessorsInitialized) return
            val uiInfoObj = mCardUIInfoField?.type
            if (uiInfoObj != null) {
                uiInfoClass = uiInfoObj
                mPersonalCardFaceField = ReflectionCacheUtils.findFieldIfExists(uiInfoObj, "mPersonalCardFace")
                mCardIssuedListBgHdUrlField = ReflectionCacheUtils.findFieldIfExists(uiInfoObj, "mCardIssuedListBgHdUrl")
                mCardIssuedListBgUrlField = ReflectionCacheUtils.findFieldIfExists(uiInfoObj, "mCardIssuedListBgUrl")
                mCardLogoUrlField = ReflectionCacheUtils.findFieldIfExists(uiInfoObj, "mCardLogoUrl")
            }
            uiInfoAccessorsInitialized = true
        }
    }

    fun getUIInfoField(uiInfoObject: Any?, fieldName: String): String {
        if (uiInfoObject == null) return ""
        ensureUIInfoAccessors()
        return when (fieldName) {
            "mPersonalCardFace" -> mPersonalCardFaceField?.let {
                runCatching { CardSnapshot.normalize(it.get(uiInfoObject) as? String) }.getOrDefault("")
            } ?: ""
            "mCardIssuedListBgHdUrl" -> mCardIssuedListBgHdUrlField?.let {
                runCatching { CardSnapshot.normalize(it.get(uiInfoObject) as? String) }.getOrDefault("")
            } ?: ""
            "mCardIssuedListBgUrl" -> mCardIssuedListBgUrlField?.let {
                runCatching { CardSnapshot.normalize(it.get(uiInfoObject) as? String) }.getOrDefault("")
            } ?: ""
            "mCardLogoUrl" -> mCardLogoUrlField?.let {
                runCatching { CardSnapshot.normalize(it.get(uiInfoObject) as? String) }.getOrDefault("")
            } ?: ""
            else -> ""
        }
    }

    // High-level read methods
    fun invokeString(card: Any, method: Method?): String {
        if (method == null) return ""
        return runCatching {
            CardSnapshot.normalize(method.invoke(card) as? String)
        }.getOrDefault("")
    }

    fun invokeBoolean(card: Any, method: Method?): Boolean {
        if (method == null) return false
        return runCatching {
            method.invoke(card) as? Boolean ?: false
        }.getOrDefault(false)
    }

    fun readStringField(card: Any, field: Field?): String {
        if (field == null) return ""
        return runCatching {
            CardSnapshot.normalize(field.get(card) as? String)
        }.getOrDefault("")
    }

    fun readBooleanField(card: Any, field: Field?): Boolean {
        if (field == null) return false
        return runCatching {
            field.getBoolean(card)
        }.getOrDefault(false)
    }

    fun readObjectField(card: Any, field: Field?): Any? {
        if (field == null) return null
        return runCatching {
            field.get(card)
        }.getOrNull()
    }

    fun readSerializedJson(card: Any): JSONObject? {
        val method = serializeMethod ?: return null
        return runCatching {
            method.invoke(card) as? JSONObject
        }.getOrNull()
    }

    companion object {
        private val accessorsCache = ConcurrentHashMap<Class<*>, CardClassAccessors>()

        /**
         * Get or create cached accessors for a card class.
         */
        fun of(cardClass: Class<*>): CardClassAccessors {
            return accessorsCache.computeIfAbsent(cardClass) { clazz ->
                CardClassAccessors(clazz)
            }
        }

        /**
         * Clear the accessors cache. Useful for testing.
         */
        fun clearCache() {
            accessorsCache.clear()
        }
    }
}