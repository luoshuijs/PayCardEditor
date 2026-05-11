package com.luoshui.paycardeditor.hook.card;

import android.content.Context;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.hook.HookCatalog;
import com.luoshui.paycardeditor.hook.HookInstallerSupport;
import com.luoshui.paycardeditor.hook.HookProcessContext;
import com.luoshui.paycardeditor.hook.HookReflectionUtils;
import com.luoshui.paycardeditor.hook.dexkit.DexKitHookTargets;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

public final class CardDataHookRegistrar {

    private final XposedModule mModule;
    private final RemoteCardSnapshotStore mSnapshotStore;
    private final BankCardReplacer mBankCardReplacer;
    private final HookInstallerSupport mSupport;

    public CardDataHookRegistrar(
            @NonNull XposedModule module,
            @NonNull RemoteCardSnapshotStore snapshotStore,
            @NonNull BankCardReplacer bankCardReplacer,
            @NonNull HookInstallerSupport support
    ) {
        mModule = module;
        mSnapshotStore = snapshotStore;
        mBankCardReplacer = bankCardReplacer;
        mSupport = support;
    }

    public void installCardInfoHooks(@NonNull Class<?> cardInfoClass) throws NoSuchMethodException {
        Method updateInfo = cardInfoClass.getDeclaredMethod("updateInfo", cardInfoClass);
        mSupport.prepareMethod(updateInfo, "CardInfo.updateInfo");
        mModule.hook(updateInfo)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    mSupport.runHookSideEffect("CardInfo.updateInfo", () -> {
                        mBankCardReplacer.apply(chain.getThisObject(), HookProcessContext.INSTANCE.resolve());
                        mSnapshotStore.mergeCard(chain.getThisObject(), "CardInfo.updateInfo");
                    });
                    return result;
                });
        mSupport.recordInstalledHook(HookCatalog.CARD_INFO_UPDATE_INFO, updateInfo);
    }

    public void installManagerHooks(
            @NonNull Class<?> cardInfoClass,
            @NonNull Class<?> cardInfoManagerClass,
            @NonNull Class<?> cacheLauncherClass
    ) throws NoSuchMethodException {
        Method putSingle = HookReflectionUtils.findOverload(cardInfoManagerClass, "put", parameterTypes ->
                parameterTypes.length == 1 && cardInfoClass.getName().equals(parameterTypes[0].getName()));
        mSupport.prepareMethod(putSingle, "CardInfoManager.put(CardInfo)");
        mModule.hook(putSingle)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    mSupport.runHookSideEffect("CardInfoManager.put(CardInfo)", () -> {
                        mBankCardReplacer.apply(chain.getArg(0), HookProcessContext.INSTANCE.resolve());
                        mSnapshotStore.mergeCard(chain.getArg(0), "CardInfoManager.put(CardInfo)");
                    });
                    return chain.proceed();
                });
        mSupport.recordInstalledHook(HookCatalog.CARD_MANAGER_PUT_SINGLE, putSingle);

        Method putList = HookReflectionUtils.findOverload(cardInfoManagerClass, "put", parameterTypes ->
                parameterTypes.length == 1 && List.class.isAssignableFrom(parameterTypes[0]));
        mSupport.prepareMethod(putList, "CardInfoManager.put(List)");
        mModule.hook(putList)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object list = chain.getArg(0);
                    mSupport.runHookSideEffect("CardInfoManager.put(List)", () -> {
                        if (list instanceof Collection<?>) {
                            List<?> cards = mSupport.copyCards((Collection<?>) list);
                            applyRules(cards);
                            mSnapshotStore.mergeCards(cards, "CardInfoManager.put(List)");
                        }
                    });
                    return chain.proceed();
                });
        mSupport.recordInstalledHook(HookCatalog.CARD_MANAGER_PUT_LIST, putList);

        installManagerCollectionHook(cardInfoManagerClass, cacheLauncherClass, "getAll", true, HookCatalog.CARD_MANAGER_GET_ALL);
        installManagerCollectionHook(cardInfoManagerClass, cacheLauncherClass, "getBankCards", false, HookCatalog.CARD_MANAGER_GET_BANK);
        installManagerCollectionHook(cardInfoManagerClass, cacheLauncherClass, "getIssuedTransCards", false, HookCatalog.CARD_MANAGER_GET_ISSUED);
        installManagerCollectionHook(cardInfoManagerClass, cacheLauncherClass, "getMifareCards", false, HookCatalog.CARD_MANAGER_GET_MIFARE);
    }

    public void installBankHooks(@NonNull DexKitHookTargets dexKitTargets) {
        Method mergeVirtualCardInfo = dexKitTargets.getBankVirtualCardMerge();
        if (mergeVirtualCardInfo == null) {
            throw new IllegalStateException("DexKit did not resolve bank virtual card merge method");
        }
        mSupport.prepareMethod(mergeVirtualCardInfo, "BankCardInfo.mergeVirtualCardInfo");
        mModule.hook(mergeVirtualCardInfo)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    mSupport.runHookSideEffect("i6.e.O", () -> patchAndMergeCard(chain.getArg(0), "i6.e.O"));
                    return result;
                });
        mSupport.recordInstalledHook(HookCatalog.BANK_MERGE_VIRTUAL, mergeVirtualCardInfo);

        Method mergeQueryPanInfo = dexKitTargets.getBankQueryPanMerge();
        if (mergeQueryPanInfo == null) {
            throw new IllegalStateException("DexKit did not resolve bank query pan merge method");
        }
        mSupport.prepareMethod(mergeQueryPanInfo, "BankCardInfo.mergeQueryPanInfo");
        mModule.hook(mergeQueryPanInfo)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    mSupport.runHookSideEffect("i6.e.N", () -> patchAndMergeCard(chain.getArg(0), "i6.e.N"));
                    return result;
                });
        mSupport.recordInstalledHook(HookCatalog.BANK_MERGE_QUERY_PAN, mergeQueryPanInfo);
    }

    public void installTransitHooks(
            @NonNull Class<?> cardInfoClass,
            @NonNull DexKitHookTargets dexKitTargets
    ) throws NoSuchMethodException {
        Method updateBackground = dexKitTargets.getTransitUpdateBackground() != null
                ? dexKitTargets.getTransitUpdateBackground()
                : cardInfoClass.getDeclaredMethod("updateBackground", Context.class);
        mSupport.prepareMethod(updateBackground, "CardInfo.updateBackground");
        mModule.hook(updateBackground)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    mSupport.runHookSideEffect("CardInfo.updateBackground", () -> patchAndMergeCard(chain.getThisObject(), "CardInfo.updateBackground"));
                    return result;
                });
        mSupport.recordInstalledHook(HookCatalog.CARD_INFO_UPDATE_BACKGROUND, updateBackground);
    }

    public void installMifareHooks(@NonNull DexKitHookTargets dexKitTargets) {
        Method mifareQuery = dexKitTargets.getMifareQuery();
        if (mifareQuery == null) {
            throw new IllegalStateException("DexKit did not resolve mifare query method");
        }
        mSupport.prepareMethod(mifareQuery, "MifareModel.queryDoorCardInfo");
        mModule.hook(mifareQuery)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    mSupport.runHookSideEffect("g6.c.o#arg", () -> patchAndMergeCard(chain.getArg(1), "g6.c.o#arg"));
                    mSupport.runHookSideEffect("g6.c.o#result", () -> mergeUnknownPayload(result, "g6.c.o#result"));
                    return result;
                });
        mSupport.recordInstalledHook(HookCatalog.MIFARE_QUERY_DOOR, mifareQuery);
    }

    private void installManagerCollectionHook(
            @NonNull Class<?> cardInfoManagerClass,
            @NonNull Class<?> cacheLauncherClass,
            @NonNull String methodName,
            boolean replace,
            @NonNull HookCatalog entry
    ) throws NoSuchMethodException {
        String label = entry.getLabel();
        Method method = cardInfoManagerClass.getDeclaredMethod(methodName, cacheLauncherClass);
        mSupport.prepareMethod(method, label);
        mModule.hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    mSupport.runHookSideEffect(label, () -> {
                        if (result instanceof Collection<?>) {
                            List<?> cards = mSupport.copyCards((Collection<?>) result);
                            applyRules(cards);
                            if (replace) {
                                mSnapshotStore.replaceCards(cards, label);
                            } else {
                                mSnapshotStore.mergeCards(cards, label);
                            }
                        }
                    });
                    return result;
                });
        mSupport.recordInstalledHook(entry, method);
    }

    private void applyRules(@NonNull Collection<?> cards) {
        Context context = HookProcessContext.INSTANCE.resolve();
        for (Object card : cards) {
            mBankCardReplacer.apply(card, context);
        }
    }

    private void patchAndMergeCard(Object card, @NonNull String source) {
        Context context = HookProcessContext.INSTANCE.resolve();
        mBankCardReplacer.apply(card, context);
        mSnapshotStore.mergeCard(card, source);
    }

    private void mergeUnknownPayload(Object payload, @NonNull String source) {
        if (payload == null) {
            return;
        }
        if (payload instanceof Collection<?> collection) {
            applyRules(collection);
            mSnapshotStore.mergeCards(collection, source);
            return;
        }
        Class<?> payloadClass = payload.getClass();
        if (payloadClass.isArray()) {
            int length = Array.getLength(payload);
            List<Object> items = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                items.add(Array.get(payload, index));
            }
            applyRules(items);
            mSnapshotStore.mergeCards(items, source);
            return;
        }
        Object[] responsePayload = readObjectArrayField(payload, "f11026c");
        if (responsePayload != null) {
            for (Object item : responsePayload) {
                mergeUnknownPayload(item, source);
            }
            return;
        }
        patchAndMergeCard(payload, source);
    }

    private Object[] readObjectArrayField(Object target, @NonNull String fieldName) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                return value instanceof Object[] ? (Object[]) value : null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
