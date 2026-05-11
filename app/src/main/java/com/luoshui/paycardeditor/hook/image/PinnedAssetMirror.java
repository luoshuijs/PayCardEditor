package com.luoshui.paycardeditor.hook.image;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.hook.HookProcessContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Materializes a user-supplied card art asset, served through {@code SnapshotSyncProvider}
 * as a {@code content://} URI, into a local file inside the host's cache directory so the
 * disk-cache hook can hand it to Glide as a regular {@link File}.
 *
 * <p>This is required because Glide's {@code DiskLruCacheWrapper.get(Key)} contract returns
 * a {@code File} that the caller will read directly via {@code FileInputStream}. A content
 * URI can't satisfy that contract, so we mirror the provider stream into
 * {@code &lt;hostCacheDir&gt;/paycardeditor_pinned_assets/&lt;assetId&gt;.png} on first
 * hit and reuse the file thereafter.
 *
 * <p>Write strategy: stream to a {@code .tmp} sidecar, then {@code renameTo} the canonical
 * filename so concurrent readers never observe a partially-written file. This mirrors the
 * commit-style protocol Glide's own {@code DiskLruCache.Editor} uses.
 *
 * <p>Stateless helper (no fields) — pulled out of {@code ImageCacheHookRegistrar} so the
 * registrar can focus on hook installation. The host {@link Context} is resolved lazily
 * via {@link HookProcessContext} rather than injected, matching the pattern used elsewhere
 * for cross-thread context access.
 */
final class PinnedAssetMirror {

    private static final String MIRROR_DIR = "paycardeditor_pinned_assets";

    private PinnedAssetMirror() {
    }

    /**
     * Ensures the asset identified by {@code assetId} is materialized on disk and returns
     * the mirror {@link File}. Re-mirrors on every call so subsequent edits in PayCardEditor
     * propagate without needing a process restart — the file is small (typically &lt; 1 MB)
     * and disk-cache hits don't trigger this path often.
     *
     * @throws IllegalStateException if the host context isn't available, the provider
     *         returned a null stream, or the rename-into-place step fails.
     * @throws Throwable for any IO failure during stream copy.
     */
    static @NonNull File ensure(@NonNull String assetId, @NonNull Uri uri) throws Throwable {
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context == null) {
            throw new IllegalStateException("Host context unavailable for pinned asset mirror: " + assetId);
        }
        File mirrorDirectory = new File(context.getCacheDir(), MIRROR_DIR);
        if (!mirrorDirectory.exists() && !mirrorDirectory.mkdirs() && !mirrorDirectory.isDirectory()) {
            throw new IllegalStateException("Unable to create pinned asset mirror directory: " + mirrorDirectory.getAbsolutePath());
        }
        File mirrorFile = new File(mirrorDirectory, assetId + ".png");
        File tempFile = new File(mirrorDirectory, assetId + ".tmp");
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IllegalStateException("Provider returned null stream for " + uri);
            }
            try (OutputStream outputStream = new FileOutputStream(tempFile, false)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = inputStream.read(buffer)) >= 0) {
                    if (count > 0) {
                        outputStream.write(buffer, 0, count);
                    }
                }
                outputStream.flush();
            }
        }
        if (mirrorFile.exists() && !mirrorFile.delete()) {
            throw new IllegalStateException("Unable to replace pinned asset mirror: " + mirrorFile.getAbsolutePath());
        }
        if (!tempFile.renameTo(mirrorFile)) {
            throw new IllegalStateException("Unable to finalize pinned asset mirror: " + mirrorFile.getAbsolutePath());
        }
        return mirrorFile;
    }
}
