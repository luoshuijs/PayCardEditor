package com.luoshui.paycardeditor.hook;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.core.HookEnvironment;
import com.luoshui.paycardeditor.model.CardSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * Owns the {@code lookupKey → CacheReplacementTarget} map and the 5-second TTL refresh
 * loop that keeps it in sync with the rule provider. Kept separate from the hook
 * installers so that the install-and-forget surface stays readable and so that future
 * registrars can share the same in-memory snapshot without duplicating reload logic.
 *
 * <p>Why TTL instead of push: the host process can't easily observe rule edits made in
 * our own UI process, and the snapshot provider is cheap. A 5 s ceiling bounds the
 * rule-change → take-effect delay without putting any RPC on the disk-cache hot path.
 */
final class ReplacementMapStore {

    private static final String TAG = "PayCardEditorHook";
    private static final long REFRESH_INTERVAL_MS = 5_000L;

    private final XposedModule mModule;
    private volatile long mLoadedAt = 0L;
    private volatile Map<String, CacheReplacementTarget> mMap = Collections.emptyMap();

    ReplacementMapStore(@NonNull XposedModule module) {
        mModule = module;
    }

    /**
     * Returns the most recently loaded map without forcing a refresh. Hot-path callers
     * (memory-cache hook) use this so they never block on a provider RPC. Returns an
     * empty map if {@link #getOrLoad(Context)} has not run yet.
     */
    @NonNull
    Map<String, CacheReplacementTarget> snapshot() {
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context == null) {
            return mMap;
        }
        return getOrLoad(context);
    }

    @NonNull
    Map<String, CacheReplacementTarget> getOrLoad(@NonNull Context context) {
        long now = System.currentTimeMillis();
        Map<String, CacheReplacementTarget> cached = mMap;
        if (now - mLoadedAt < REFRESH_INTERVAL_MS) {
            return cached;
        }
        synchronized (this) {
            if (now - mLoadedAt < REFRESH_INTERVAL_MS) {
                return mMap;
            }
            Map<String, CacheReplacementTarget> loaded = loadFromProvider(context);
            mMap = loaded;
            mLoadedAt = now;
            return loaded;
        }
    }

    /**
     * Looks up a replacement target by remote-URL-like input. First tries a normalized
     * exact match, then falls back to substring containment so that hot-path callers
     * that only saw a partial URL (e.g. a path segment lifted from a Glide cache key)
     * still resolve.
     */
    CacheReplacementTarget findForRawInput(@NonNull Context context, @NonNull String rawInput) {
        String normalizedInput = GlideCacheKeyParser.normalizeRemoteUrl(rawInput);
        Map<String, CacheReplacementTarget> map = getOrLoad(context);
        CacheReplacementTarget direct = map.get(normalizedInput);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, CacheReplacementTarget> entry : map.entrySet()) {
            String lookupKey = entry.getKey();
            if (lookupKey == null || lookupKey.isEmpty()) {
                continue;
            }
            if (normalizedInput.equals(lookupKey) || normalizedInput.contains(lookupKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, CacheReplacementTarget> loadFromProvider(@NonNull Context context) {
        try {
            Bundle ruleBundle = context.getContentResolver().call(
                    Uri.parse("content://" + HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY),
                    HookEnvironment.METHOD_GET_BANK_RULES,
                    null,
                    Bundle.EMPTY
            );
            Bundle snapshotBundle = context.getContentResolver().call(
                    Uri.parse("content://" + HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY),
                    HookEnvironment.METHOD_GET_CARD_SNAPSHOTS,
                    null,
                    Bundle.EMPTY
            );
            String rulesJson = ruleBundle != null ? ruleBundle.getString(HookEnvironment.EXTRA_RULES_JSON, "[]") : "[]";
            String snapshotsJson = snapshotBundle != null ? snapshotBundle.getString(HookEnvironment.EXTRA_SNAPSHOTS_JSON, "[]") : "[]";
            List<BankCardRule> rules = BankCardRule.Companion.parseList(rulesJson == null ? "[]" : rulesJson);
            JSONArray snapshotsArray = new JSONArray(snapshotsJson == null || snapshotsJson.isBlank() ? "[]" : snapshotsJson);
            Map<String, CacheReplacementTarget> result = new HashMap<>();
            for (int index = 0; index < snapshotsArray.length(); index++) {
                JSONObject item = snapshotsArray.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                CardSnapshot snapshot = CardSnapshot.Companion.fromJson(item);
                if (!snapshot.getSupportsCustomCardArt()) {
                    continue;
                }
                BankCardRule matchedRule = null;
                for (BankCardRule rule : rules) {
                    if (rule.matches(snapshot)) {
                        matchedRule = rule;
                        break;
                    }
                }
                if (matchedRule == null || matchedRule.getReplaceCardArt().isBlank()) {
                    continue;
                }
                String assetUriText = matchedRule.getReplaceCardArt();
                if (!GlideCacheKeyParser.isLocalAssetUri(assetUriText)) {
                    continue;
                }
                Uri assetUri = Uri.parse(assetUriText);
                List<String> pathSegments = assetUri.getPathSegments();
                if (pathSegments.size() != 2 || !HookEnvironment.PATH_CARD_ASSETS.equals(pathSegments.get(0))) {
                    continue;
                }
                String assetId = pathSegments.get(1);
                if (assetId == null || assetId.isEmpty()) {
                    continue;
                }
                String remoteUrl = firstNonBlankReplacementKey(snapshot);
                if (remoteUrl.isEmpty()) {
                    continue;
                }
                CacheReplacementTarget replacementTarget = new CacheReplacementTarget(remoteUrl, assetId, assetUri);
                addReplacementLookupKeys(result, snapshot, replacementTarget);
            }
            mModule.log(Log.INFO, TAG, "Loaded image cache replacement map entries=" + result.size());
            return result;
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "load cache replacement map failed: " + Log.getStackTraceString(throwable));
            return Collections.emptyMap();
        }
    }

    private static void addReplacementLookupKeys(
            @NonNull Map<String, CacheReplacementTarget> result,
            @NonNull CardSnapshot snapshot,
            @NonNull CacheReplacementTarget replacementTarget
    ) {
        LinkedHashSet<String> lookupKeys = collectLookupKeyVariants(snapshot);
        for (String lookupKey : lookupKeys) {
            result.put(lookupKey, replacementTarget);
        }
    }

    private static String firstNonBlankReplacementKey(@NonNull CardSnapshot snapshot) {
        LinkedHashSet<String> lookupKeys = collectLookupKeyVariants(snapshot);
        return lookupKeys.isEmpty() ? "" : lookupKeys.getFirst();
    }

    private static LinkedHashSet<String> collectLookupKeyVariants(@NonNull CardSnapshot snapshot) {
        LinkedHashSet<String> lookupKeys = new LinkedHashSet<>();
        addLookupKeyVariants(lookupKeys, snapshot.getCardArt());
        addLookupKeyVariants(lookupKeys, snapshot.getPersonalCardFace());
        addLookupKeyVariants(lookupKeys, snapshot.getIssuedListBgHd());
        addLookupKeyVariants(lookupKeys, snapshot.getIssuedListBg());
        addLookupKeyVariants(lookupKeys, snapshot.getLogo());
        addLookupKeyVariants(lookupKeys, snapshot.getLogoWithName());
        addLookupKeyVariants(lookupKeys, snapshot.getProductId());
        addLookupKeyVariants(lookupKeys, snapshot.getCid());
        addLookupKeyVariants(lookupKeys, snapshot.getVcUid());
        addLookupKeyVariants(lookupKeys, snapshot.getAid());
        return lookupKeys;
    }

    private static void addLookupKeyVariants(@NonNull Set<String> lookupKeys, String rawValue) {
        String normalized = GlideCacheKeyParser.normalizeRemoteUrl(rawValue);
        if (normalized.isEmpty() || GlideCacheKeyParser.isLocalAssetUri(normalized)) {
            return;
        }
        lookupKeys.add(normalized);
        if (normalized.startsWith("http://")) {
            lookupKeys.add("https://" + normalized.substring("http://".length()));
        } else if (normalized.startsWith("https://")) {
            lookupKeys.add("http://" + normalized.substring("https://".length()));
        }
        String lastSegment = GlideCacheKeyParser.extractLastPathSegment(normalized);
        if (!lastSegment.isEmpty()) {
            lookupKeys.add(lastSegment);
            int extensionIndex = lastSegment.lastIndexOf('.');
            if (extensionIndex > 0) {
                lookupKeys.add(lastSegment.substring(0, extensionIndex));
            }
        }
    }
}
