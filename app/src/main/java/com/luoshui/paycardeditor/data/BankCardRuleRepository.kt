package com.luoshui.paycardeditor.data

import com.luoshui.paycardeditor.app.App
import com.luoshui.paycardeditor.core.HookEnvironment
import com.luoshui.paycardeditor.model.CardSnapshot


import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.luoshui.paycardeditor.hook.card.BankCardRule
import org.json.JSONArray

internal object BankCardRuleRepository {

    fun loadRules(): List<BankCardRule> = BankCardRule.parseList(
        prefs().getString(HookEnvironment.PREF_KEY_BANK_RULES, "[]").orEmpty()
    )

    fun findRule(snapshot: CardSnapshot): BankCardRule? = loadRules().firstOrNull { it.matches(snapshot) }

    fun upsertRule(snapshot: CardSnapshot, asset: CardAsset): BankCardRule {
        require(snapshot.supportsCustomCardArt) { "Unsupported card type" }
        val newRule = BankCardRule(
            enabled = true,
            matchCardType = snapshot.cardType,
            matchAid = snapshot.aid,
            matchPanLastDigits = snapshot.panLastDigits,
            matchProductId = snapshot.productId,
            matchCardName = snapshot.cardName,
            matchCid = snapshot.cid,
            matchVcUid = snapshot.vcUid,
            replaceCardArt = asset.contentUri().toString(),
        )
        val updated = loadRules().toMutableList().apply {
            removeAll { it.matches(snapshot) }
            add(newRule)
        }
        saveRules(updated)
        return newRule
    }

    fun removeRule(snapshot: CardSnapshot): Boolean {
        val updated = loadRules().toMutableList()
        val removed = updated.removeAll { it.matches(snapshot) }
        if (removed) {
            saveRules(updated)
        }
        return removed
    }

    fun removeRulesForAsset(assetId: String) {
        val suffix = "/${HookEnvironment.PATH_CARD_ASSETS}/$assetId"
        val updated = loadRules().filterNot { it.replaceCardArt.endsWith(suffix) }
        saveRules(updated)
    }

    fun assignmentCount(asset: CardAsset): Int = loadRules().count { it.replaceCardArt == asset.contentUri().toString() }

    private fun saveRules(rules: List<BankCardRule>) {
        val array = JSONArray().apply { rules.forEach { put(it.toJson()) } }
        prefs().edit { putString(HookEnvironment.PREF_KEY_BANK_RULES, array.toString()) }
        // Wake up the hook process so it drops its 5s TTL cache immediately instead of
        // letting the next bank-card image render slip into the stale window. Routed
        // through provider.call so other triggers (adb tooling, future host-side refresh
        // buttons) can share the same notification path without duplicating the URI.
        runCatching {
            App.appContext?.contentResolver?.call(
                Uri.parse("content://${HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY}"),
                HookEnvironment.METHOD_NOTIFY_RULES_INVALIDATED,
                null,
                null,
            )
        }
    }

    private fun prefs() = checkNotNull(App.appContext)
        .getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
}
