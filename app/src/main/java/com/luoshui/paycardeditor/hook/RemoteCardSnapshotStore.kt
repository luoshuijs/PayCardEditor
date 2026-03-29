package com.luoshui.paycardeditor.hook

import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.luoshui.paycardeditor.CardSnapshot
import com.luoshui.paycardeditor.HookEnvironment
import com.luoshui.paycardeditor.firstNonBlank
import io.github.libxposed.api.XposedModule
import org.json.JSONArray
import org.json.JSONObject

internal class RemoteCardSnapshotStore(
    private val module: XposedModule,
) {
    companion object {
        private const val TAG = "PayCardEditorHook"

        private val SNAPSHOT_COMPARATOR = Comparator<CardSnapshot> { left, right ->
            compareValues(left.cardName.isBlank(), right.cardName.isBlank())
                .takeIf { it != 0 }
                ?: compareValues(left.title.lowercase(), right.title.lowercase())
                    .takeIf { it != 0 }
                ?: compareValues(left.cardType, right.cardType)
        }
    }

    private val lock = Any()
    private val providerUri = Uri.parse("content://${HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY}")
    private val pendingCalls = ArrayDeque<PendingProviderCall>()
    private var flushingPendingCalls = false

    fun mergeCard(card: Any?, source: String) {
        safelySync(source) {
            if (card == null) {
                return@safelySync
            }
            persist(arrayListOf(card), source, replace = false)
        }
    }

    fun mergeCards(cards: Collection<*>?, source: String) {
        safelySync(source) {
            persist(copyCards(cards), source, replace = false)
        }
    }

    fun replaceCards(cards: Collection<*>?, source: String) {
        safelySync(source) {
            persist(copyCards(cards), source, replace = true)
        }
    }

    fun recordInstallError(source: String, throwable: Throwable) {
        safelySync(source) {
            callProvider(
                HookEnvironment.METHOD_RECORD_ERROR,
                Bundle().apply {
                    putString(HookEnvironment.EXTRA_SOURCE, source)
                    putString(
                        HookEnvironment.EXTRA_ERROR,
                        "$source: ${throwable.message.orEmpty()}\n${throwable.stackTraceToString()}"
                    )
                }
            )
        }
    }

    private inline fun safelySync(source: String, block: () -> Unit) {
        try {
            block()
        } catch (throwable: Throwable) {
            module.log(Log.ERROR, TAG, "suppressed snapshot sync failure in $source: ${throwable.stackTraceToString()}")
        }
    }

    private fun persist(cards: Collection<*>?, source: String, replace: Boolean) {
        try {
            if (cards.isNullOrEmpty()) {
                return
            }
            val newSnapshots = LinkedHashMap<String, CardSnapshot>()
            cards.forEach { card ->
                if (card == null) {
                    return@forEach
                }
                runCatching {
                    CardReflectionReader.read(card)
                }.onFailure { throwable ->
                    module.log(Log.WARN, TAG, "$source skipped unreadable card: ${throwable.stackTraceToString()}")
                }.getOrNull()?.let { snapshot ->
                    newSnapshots[snapshot.key] = snapshot
                }
            }
            if (newSnapshots.isEmpty()) {
                return
            }
            val sortedSnapshots = ArrayList(newSnapshots.values)
            sortedSnapshots.sortWith(SNAPSHOT_COMPARATOR)
            val payload = JSONArray().apply {
                sortedSnapshots.forEach { snapshot ->
                    runCatching {
                        put(snapshot.toJson())
                    }.onFailure { throwable ->
                        module.log(Log.WARN, TAG, "$source skipped snapshot payload for ${snapshot.title}: ${throwable.stackTraceToString()}")
                    }
                }
            }
            if (payload.length() == 0) {
                return
            }
            synchronized(lock) {
                callProvider(
                    HookEnvironment.METHOD_UPSERT_SNAPSHOTS,
                    Bundle().apply {
                        putString(HookEnvironment.EXTRA_PAYLOAD, payload.toString())
                        putBoolean(HookEnvironment.EXTRA_REPLACE, replace)
                        putString(HookEnvironment.EXTRA_SOURCE, source)
                    }
                )
            }
            module.log(Log.DEBUG, TAG, "$source synced ${newSnapshots.size} card snapshots")
        } catch (throwable: Throwable) {
            module.log(Log.ERROR, TAG, "snapshot sync failed: ${throwable.stackTraceToString()}")
        }
    }

    private fun copyCards(cards: Collection<*>?): ArrayList<Any> {
        if (cards.isNullOrEmpty()) {
            return arrayListOf()
        }
        val copied = ArrayList<Any>()
        cards.forEach { card ->
            if (card != null) {
                copied += card
            }
        }
        return copied
    }

    private fun callProvider(method: String, extras: Bundle) {
        val context = HookProcessContext.resolve() ?: run {
            enqueuePendingCall(method, extras)
            module.log(Log.WARN, TAG, "skip provider sync: no application context")
            return
        }
        flushPendingCalls(context)
        invokeProvider(context, method, extras)
    }

    private fun invokeProvider(context: android.content.Context, method: String, extras: Bundle) {
        try {
            context.contentResolver.call(providerUri, method, null, Bundle(extras))
        } catch (throwable: Throwable) {
            module.log(Log.ERROR, TAG, "provider sync failed: ${throwable.stackTraceToString()}")
        }
    }

    private fun enqueuePendingCall(method: String, extras: Bundle) {
        synchronized(lock) {
            if (pendingCalls.size >= 32) {
                pendingCalls.removeFirstOrNull()
            }
            pendingCalls.addLast(PendingProviderCall(method, Bundle(extras)))
        }
    }

    private fun flushPendingCalls(context: android.content.Context) {
        val queuedCalls = synchronized(lock) {
            if (flushingPendingCalls || pendingCalls.isEmpty()) {
                return
            }
            flushingPendingCalls = true
            ArrayList(pendingCalls).also { pendingCalls.clear() }
        }
        try {
            queuedCalls.forEach { pendingCall ->
                invokeProvider(context, pendingCall.method, pendingCall.extras)
            }
        } finally {
            synchronized(lock) {
                flushingPendingCalls = false
            }
        }
    }

    private data class PendingProviderCall(
        val method: String,
        val extras: Bundle,
    )

}

internal object CardReflectionReader {
    fun read(card: Any): CardSnapshot? {
        if (!shouldIncludeCard(card)) {
            return null
        }
        val json = readSerializedJson(card)
        val cardUiInfo = json?.optJSONObject("card_ui_info")
        val cardUiInfoObject = readObjectField(card, "mCardUIInfo")
        val aid = firstNonBlank(
            CardSnapshot.normalize(json?.optString("aid")),
            invokeString(card, "getAid"),
        )
        val cardType = firstNonBlank(
            CardSnapshot.normalize(json?.optString("cardType")),
            invokeString(card, "getCardType"),
        )
        val cardName = firstNonBlank(
            CardSnapshot.normalize(json?.optString("title")),
            invokeString(card, "getCardName"),
            invokeString(card, "getProductName"),
            readField(card, "mCardProductName"),
            readField(card, "mProductName"),
            readField(card, "mBankName"),
        )
        if (aid.isBlank() && cardType.isBlank() && cardName.isBlank()) {
            return null
        }
        return CardSnapshot(
            aid = aid,
            cardType = cardType,
            cardName = cardName,
            cardNo = firstNonBlank(invokeString(card, "getCardNo"), readField(card, "mCardNo")),
            realCardNo = readField(card, "mRealCardNo"),
            issuerName = firstNonBlank(readField(card, "mIssuerName"), readField(card, "mBankName")),
            productId = firstNonBlank(invokeString(card, "getProductId"), readField(card, "mProductId"), readField(card, "mCardProductTypeId")),
            cid = firstNonBlank(invokeString(card, "getCid"), readField(card, "mCId")),
            vcUid = firstNonBlank(invokeString(card, "getVcUid"), readField(card, "mVcUid")),
            panLastDigits = readField(card, "mPanLastDigits"),
            cardArt = firstNonBlank(
                CardSnapshot.normalize(json?.optString("cardArt")),
                invokeString(card, "getCardArt"),
                readField(card, "mCardArt"),
            ),
            cardFrontColor = readField(card, "mCardFrontColor"),
            personalCardFace = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("personalCardFace")),
                readField(cardUiInfoObject, "mPersonalCardFace"),
            ),
            issuedListBgHd = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("issuedListBgHd")),
                readField(cardUiInfoObject, "mCardIssuedListBgHdUrl"),
            ),
            issuedListBg = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("issuedListBg")),
                readField(cardUiInfoObject, "mCardIssuedListBgUrl"),
            ),
            logo = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("logo")),
                readField(cardUiInfoObject, "mCardLogoUrl"),
                readField(card, "mBankLogoUrl"),
            ),
            logoWithName = readField(card, "mBankLogoWithNameUrl"),
            isBankCard = invokeBoolean(card, "isBankCard"),
            isTransCard = invokeBoolean(card, "isTransCard"),
            isMifareCard = invokeBoolean(card, "isMiFareCard"),
        )
    }

    private fun shouldIncludeCard(card: Any): Boolean {
        val isActive = invokeBoolean(card, "isCardActive")
        val isServiceIssued = invokeBoolean(card, "isServiceStatusIssued")
        val hasIssue = readBooleanField(card, "mHasIssue")
        val mifareIssued = invokeBoolean(card, "isMiFareCard") && !invokeBoolean(card, "isDummy")
        return isActive || isServiceIssued || hasIssue || mifareIssued
    }

    private fun readSerializedJson(card: Any): JSONObject? = runCatching {
        val method = card.javaClass.methods.firstOrNull { it.name == "serialize" && it.parameterCount == 0 } ?: return null
        method.invoke(card) as? JSONObject
    }.getOrNull()

    private fun invokeString(card: Any, methodName: String): String = runCatching {
        val method = card.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 } ?: return ""
        CardSnapshot.normalize(method.invoke(card) as? String)
    }.getOrDefault("")

    private fun invokeBoolean(card: Any, methodName: String): Boolean = runCatching {
        val method = card.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 } ?: return false
        method.invoke(card) as? Boolean ?: false
    }.getOrDefault(false)

    private fun readField(card: Any?, fieldName: String): String {
        if (card == null) {
            return ""
        }
        var current: Class<*>? = card.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { it.name == fieldName }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    CardSnapshot.normalize(field.get(card) as? String)
                }.getOrDefault("")
            }
            current = current.superclass
        }
        return ""
    }

    private fun readObjectField(card: Any, fieldName: String): Any? {
        var current: Class<*>? = card.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { it.name == fieldName }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(card)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

    private fun readBooleanField(card: Any, fieldName: String): Boolean {
        var current: Class<*>? = card.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { it.name == fieldName }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.getBoolean(card)
                }.getOrDefault(false)
            }
            current = current.superclass
        }
        return false
    }
}
