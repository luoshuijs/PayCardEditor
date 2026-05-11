package com.luoshui.paycardeditor.hook;

import androidx.annotation.NonNull;

/**
 * Single source of truth for the hook list shown on the troubleshoot page.
 *
 * <p>The declaration order here is the display order — keep it in sync with the
 * install order in {@link PayCardHookInstaller}. Every call to
 * {@link HookInstallerSupport#recordInstalledHook(HookCatalog, java.lang.reflect.Method)}
 * must reference an entry from this enum, which means the installer and the
 * troubleshoot publisher can never drift apart again.</p>
 *
 * <p>The string in {@link #getLabel()} is what the user sees on the troubleshoot
 * page. It is also the persisted key in {@link HookInstallerSupport}'s record
 * map, so the same value must be used in every install site for a given hook.</p>
 */
public enum HookCatalog {
    CARD_INFO_UPDATE_INFO("CardInfo.updateInfo"),
    CARD_MANAGER_PUT_SINGLE("CardInfoManager.put(CardInfo)"),
    CARD_MANAGER_PUT_LIST("CardInfoManager.put(List)"),
    CARD_MANAGER_GET_ALL("CardInfoManager.getAll"),
    CARD_MANAGER_GET_BANK("CardInfoManager.getBankCards"),
    CARD_MANAGER_GET_ISSUED("CardInfoManager.getIssuedTransCards"),
    CARD_MANAGER_GET_MIFARE("CardInfoManager.getMifareCards"),
    BANK_MERGE_VIRTUAL("BankCardInfo.mergeVirtualCardInfo"),
    BANK_MERGE_QUERY_PAN("BankCardInfo.mergeQueryPanInfo"),
    CARD_INFO_UPDATE_BACKGROUND("CardInfo.updateBackground"),
    GLIDE_TOKEN_GENERATE("Md5FileNameGenerator.generate"),
    GLIDE_DISK_GET("DiskLruCacheWrapper.get"),
    GLIDE_DISK_PUT("DiskLruCacheWrapper.put"),
    GLIDE_DISK_REMOVE("DiskLruCache.remove"),
    MIFARE_QUERY_DOOR("MifareModel.queryDoorCardInfo"),
    GLIDE_ENGINE_LOAD_FROM_CACHE("Engine.loadFromCache");

    private final String mLabel;

    HookCatalog(@NonNull String label) {
        mLabel = label;
    }

    @NonNull
    public String getLabel() {
        return mLabel;
    }
}
