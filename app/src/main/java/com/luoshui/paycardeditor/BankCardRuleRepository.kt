package com.luoshui.paycardeditor

import android.content.Context
import androidx.core.content.edit
import com.luoshui.paycardeditor.hook.BankCardRule
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
    }

    private fun prefs() = checkNotNull(App.appContext)
        .getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
}
