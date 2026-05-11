package com.luoshui.paycardeditor.hook.image;

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * Immutable description of a single image-replacement target.
 *
 * <p>Each instance links three identifiers that together let the disk-cache hook decide
 * whether a given Glide cache key points at an image we want to swap, and where to source
 * the replacement bytes from:
 * <ul>
 *     <li>{@link #remoteUrl} — the original image URL Mi Pay tried to download. Used as
 *         the primary lookup key in the replacement map.</li>
 *     <li>{@link #assetId} — UUID of the user-supplied asset stored in
 *         {@code PayCardEditor/files/card_assets/&lt;assetId&gt;.png}.</li>
 *     <li>{@link #assetUri} — content:// URI exported by {@code SnapshotSyncProvider} so
 *         the host process can read the asset bytes without direct filesystem access.</li>
 * </ul>
 *
 * <p>Previously nested inside {@code ImageCacheHookRegistrar} as a package-private static
 * class. Promoted to a top-level type so {@code MemoryCacheHookRegistrar} and the
 * upcoming {@code ReplacementMapStore} / {@code DiskLruCacheReflector} split don't have
 * to share an enclosing class just to reuse this carrier.
 */
final class CacheReplacementTarget {
    final String remoteUrl;
    final String assetId;
    final Uri assetUri;

    CacheReplacementTarget(@NonNull String remoteUrl, @NonNull String assetId, @NonNull Uri assetUri) {
        this.remoteUrl = remoteUrl;
        this.assetId = assetId;
        this.assetUri = assetUri;
    }
}
