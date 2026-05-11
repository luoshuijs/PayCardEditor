package com.luoshui.paycardeditor.hook.card

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.luoshui.paycardeditor.core.HookEnvironment
import com.luoshui.paycardeditor.hook.HookProcessContext
import com.luoshui.paycardeditor.model.CardSnapshot
import com.luoshui.paycardeditor.model.firstNonBlank
import io.github.libxposed.api.XposedModule
import org.json.JSONArray

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
    private val providerUri = "content://${HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY}".toUri()
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

    fun updateTroubleshootState(debugStatus: String, hookMethods: String) {
        safelySync("troubleshoot") {
            callProvider(
                HookEnvironment.METHOD_UPDATE_TROUBLESHOOT_STATE,
                Bundle().apply {
                    putString(HookEnvironment.EXTRA_DEBUG_STATUS, debugStatus)
                    putString(HookEnvironment.EXTRA_HOOK_METHODS, hookMethods)
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

    private fun invokeProvider(context: Context, method: String, extras: Bundle) {
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

    private fun flushPendingCalls(context: Context) {
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
        val accessors = CardClassAccessors.of(card.javaClass)
        
        if (!shouldIncludeCard(card, accessors)) {
            return null
        }
        
        // Try to read serialized JSON first (lazy fallback for UI fields)
        val json = accessors.readSerializedJson(card)
        val cardUiInfo = json?.optJSONObject("card_ui_info")
        val cardUiInfoObject = accessors.readObjectField(card, accessors.mCardUIInfoField)
        
        val aid = firstNonBlank(
            CardSnapshot.normalize(json?.optString("aid")),
            accessors.invokeString(card, accessors.getAidMethod),
        )
        val cardType = firstNonBlank(
            CardSnapshot.normalize(json?.optString("cardType")),
            accessors.invokeString(card, accessors.getCardTypeMethod),
        )
        val cardName = firstNonBlank(
            CardSnapshot.normalize(json?.optString("title")),
            accessors.invokeString(card, accessors.getCardNameMethod),
            accessors.invokeString(card, accessors.getProductNameMethod),
            accessors.readStringField(card, accessors.mCardProductNameField),
            accessors.readStringField(card, accessors.mProductNameField),
            accessors.readStringField(card, accessors.mBankNameField),
        )
        
        if (aid.isBlank() && cardType.isBlank() && cardName.isBlank()) {
            return null
        }
        
        return CardSnapshot(
            aid = aid,
            cardType = cardType,
            cardName = cardName,
            cardNo = firstNonBlank(
                accessors.invokeString(card, accessors.getCardNoMethod),
                accessors.readStringField(card, accessors.mCardNoField)
            ),
            realCardNo = accessors.readStringField(card, accessors.mRealCardNoField),
            issuerName = firstNonBlank(
                accessors.readStringField(card, accessors.mIssuerNameField),
                accessors.readStringField(card, accessors.mBankNameField)
            ),
            productId = firstNonBlank(
                accessors.invokeString(card, accessors.getProductIdMethod),
                accessors.readStringField(card, accessors.mProductIdField),
                accessors.readStringField(card, accessors.mCardProductTypeIdField)
            ),
            cid = firstNonBlank(
                accessors.invokeString(card, accessors.getCidMethod),
                accessors.readStringField(card, accessors.mCIdField)
            ),
            vcUid = firstNonBlank(
                accessors.invokeString(card, accessors.getVcUidMethod),
                accessors.readStringField(card, accessors.mVcUidField)
            ),
            panLastDigits = accessors.readStringField(card, accessors.mPanLastDigitsField),
            cardArt = firstNonBlank(
                CardSnapshot.normalize(json?.optString("cardArt")),
                accessors.invokeString(card, accessors.getCardArtMethod),
                accessors.readStringField(card, accessors.mCardArtField),
            ),
            cardFrontColor = accessors.readStringField(card, accessors.mCardFrontColorField),
            personalCardFace = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("personalCardFace")),
                accessors.getUIInfoField(cardUiInfoObject, "mPersonalCardFace"),
            ),
            issuedListBgHd = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("issuedListBgHd")),
                accessors.getUIInfoField(cardUiInfoObject, "mCardIssuedListBgHdUrl"),
            ),
            issuedListBg = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("issuedListBg")),
                accessors.getUIInfoField(cardUiInfoObject, "mCardIssuedListBgUrl"),
            ),
            logo = firstNonBlank(
                CardSnapshot.normalize(cardUiInfo?.optString("logo")),
                accessors.getUIInfoField(cardUiInfoObject, "mCardLogoUrl"),
                accessors.readStringField(card, accessors.mBankLogoUrlField),
            ),
            logoWithName = accessors.readStringField(card, accessors.mBankLogoWithNameUrlField),
            isBankCard = accessors.invokeBoolean(card, accessors.isBankCardMethod),
            isTransCard = accessors.invokeBoolean(card, accessors.isTransCardMethod),
            isMifareCard = accessors.invokeBoolean(card, accessors.isMiFareCardMethod),
            isCarKeyCard = accessors.invokeBoolean(card, accessors.isTraditionalCarKeyCardMethod)
        )
    }

    private fun shouldIncludeCard(card: Any, accessors: CardClassAccessors): Boolean {
        val isActive = accessors.invokeBoolean(card, accessors.isCardActiveMethod)
        val isServiceIssued = accessors.invokeBoolean(card, accessors.isServiceStatusIssuedMethod)
        val hasIssue = accessors.readBooleanField(card, accessors.mHasIssueField)
        val mifareIssued = accessors.invokeBoolean(card, accessors.isMiFareCardMethod) && 
                          !accessors.invokeBoolean(card, accessors.isDummyMethod)
        val carKeyIssued = accessors.invokeBoolean(card, accessors.isTraditionalCarKeyCardMethod)
        return isActive || isServiceIssued || hasIssue || mifareIssued || carKeyIssued
    }
}
