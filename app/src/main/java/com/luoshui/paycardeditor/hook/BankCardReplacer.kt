package com.luoshui.paycardeditor.hook

import android.content.Context
import android.util.Log
import com.luoshui.paycardeditor.CardSnapshot
import io.github.libxposed.api.XposedModule

internal class BankCardReplacer(
    private val module: XposedModule,
) {
    companion object {
        private const val TAG = "PayCardEditorHook"
    }

    private val ruleResolver = BankCardRuleResolver(module)

    fun apply(card: Any?, context: Context?) {
        if (card == null) {
            return
        }
        val snapshot = CardReflectionReader.read(card) ?: return
        val rule = ruleResolver.getRules(context).firstOrNull { it.matches(snapshot) } ?: return
        val changedFields = linkedSetOf<String>()

        if (snapshot.isBankCard) {
            if (applyStringField(card, "mCardFrontColor", rule.replaceCardFrontColor)) {
                changedFields += "mCardFrontColor"
            }
        }

        if (changedFields.isNotEmpty()) {
            module.log(Log.INFO, TAG, "card rule applied to ${snapshot.title}: ${changedFields.joinToString()}")
        }
    }

    private fun applyStringField(card: Any, fieldName: String, replacement: String): Boolean {
        if (replacement.isBlank()) {
            return false
        }
        return applyObjectField(card, fieldName, replacement)
    }

    private fun applyObjectField(card: Any, fieldName: String, replacement: Any): Boolean {
        var current: Class<*>? = card.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { it.name == fieldName }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.set(card, replacement)
                    true
                }.getOrDefault(false)
            }
            current = current.superclass
        }
        return false
    }

}
