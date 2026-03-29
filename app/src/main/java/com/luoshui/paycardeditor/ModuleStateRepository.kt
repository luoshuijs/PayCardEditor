package com.luoshui.paycardeditor

import android.content.Context
import io.github.libxposed.service.XposedService
import org.json.JSONArray
import org.json.JSONObject

object ModuleStateRepository {

    fun loadHomeState(): HomeState {
        val moduleStatus = loadModuleStatus()
        var cardState = loadCardState()
        if (cardState.cards.isEmpty() && cardState.warning.isBlank()) {
            cardState = cardState.copy(
                warning = when (moduleStatus.level) {
                    ModuleStatusLevel.ACTIVE -> "模块已经接入 libxposed，但还没有收到卡片快照。先打开一次小米钱包卡片页，再回到这里刷新。"
                    ModuleStatusLevel.WAITING -> "等待 libxposed 服务连接后，才能读取远程卡片快照。"
                    ModuleStatusLevel.INACTIVE -> "先在框架里勾选 com.miui.tsmclient 作用域并重启目标进程，Hook 才会开始上报卡片信息。"
                }
            )
        }
        return HomeState(moduleStatus = moduleStatus, cardState = cardState)
    }

    private fun loadModuleStatus(): ModuleStatusState {
        val service = App.xposedService ?: return ModuleStatusState(
            level = ModuleStatusLevel.WAITING,
            title = "等待连接",
            detail = "尚未连接到 libxposed 服务。确认模块已经启用，然后重新进入此页。",
        )
        val frameworkLabel = readFrameworkLabel(service)
        val scope = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
        return if (HookEnvironment.TARGET_PACKAGE in scope) {
            ModuleStatusState(
                level = ModuleStatusLevel.ACTIVE,
                title = "模块已激活",
                detail = "$frameworkLabel\n作用域已覆盖 ${HookEnvironment.TARGET_PACKAGE}",
            )
        } else {
            val scopeSummary = if (scope.isEmpty()) "当前框架还没有返回作用域列表。" else "当前作用域: ${scope.joinToString()}"
            ModuleStatusState(
                level = ModuleStatusLevel.INACTIVE,
                title = "模块未激活",
                detail = "$frameworkLabel\n$scopeSummary",
            )
        }
    }

    private fun loadCardState(): CardSnapshotState {
        val context = App.appContext ?: return CardSnapshotState(warning = "模块进程尚未初始化，暂时无法读取本地快照。")
        val prefs = context.getSharedPreferences(HookEnvironment.LOCAL_STATE_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(HookEnvironment.PREF_KEY_CARD_SNAPSHOTS, "[]").orEmpty()
        val cards = parseCards(raw)
        val error = prefs.getString(HookEnvironment.PREF_KEY_LAST_ERROR, "").orEmpty()
        return CardSnapshotState(
            cards = cards,
            lastUpdated = prefs.getLong(HookEnvironment.PREF_KEY_LAST_UPDATED, 0L),
            lastSource = prefs.getString(HookEnvironment.PREF_KEY_LAST_SOURCE, "").orEmpty(),
            warning = error,
        )
    }

    private fun readFrameworkLabel(service: XposedService): String = runCatching {
        buildString {
            append(service.frameworkName)
            append(' ')
            append(service.frameworkVersion)
            append(" (build ")
            append(service.frameworkVersionCode)
            append(") · API ")
            append(service.apiVersion)
        }
    }.getOrElse { "libxposed 服务已连接" }

    private fun parseCards(raw: String): List<CardSnapshot> = runCatching {
        val jsonArray = JSONArray(if (raw.isBlank()) "[]" else raw)
        buildList(jsonArray.length()) {
            repeat(jsonArray.length()) { index ->
                val item = jsonArray.optJSONObject(index) ?: JSONObject()
                add(CardSnapshot.fromJson(item))
            }
        }.sortedWith(compareBy<CardSnapshot>({ it.cardName.isBlank() }, { it.title.lowercase() }, { it.cardType }))
    }.getOrElse { emptyList() }
}
