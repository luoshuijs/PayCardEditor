package com.luoshui.paycardeditor.hook;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.luoshui.paycardeditor.core.HookEnvironment;

/**
 * Stateless helpers that parse Glide cache keys and image URLs into the canonical strings
 * we use as lookup keys in the replacement map.
 *
 * <p>Glide hands us cache keys as opaque objects whose {@code toString()} embeds different
 * fields depending on the key class — {@code DataCacheKey}, {@code ResourceCacheKey},
 * {@code EngineKey}. Rather than reflect on each variant we parse the string forms here,
 * which is cheap and robust to R8 renames since the keys' {@code toString} format is part
 * of Glide's stable surface.
 *
 * <p>Originally inlined as private methods of {@code ImageCacheHookRegistrar}; extracted so
 * the parsing rules can be unit-targeted in isolation and so disk-cache hook installation
 * code is no longer tangled with string-munging logic.
 */
final class GlideCacheKeyParser {

    /**
     * Prefix of URIs exported by {@code SnapshotSyncProvider} that point at user-supplied
     * card art assets. Pre-computed from {@link HookEnvironment} constants so callers can
     * short-circuit replacement lookup when the source URL is already one of ours.
     */
    static final String LOCAL_ASSET_URI_PREFIX = "content://"
            + HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY
            + "/"
            + HookEnvironment.PATH_CARD_ASSETS
            + "/";

    private GlideCacheKeyParser() {
    }

    static boolean isLocalAssetUri(@NonNull String value) {
        return value.startsWith(LOCAL_ASSET_URI_PREFIX);
    }

    /**
     * Returns the last path segment of {@code value}, falling back to a manual split when
     * the value is not a well-formed URI. Used to derive a short lookup key from a full
     * image URL — e.g. {@code https://.../door_card_2.0/foo.png} → {@code foo.png}.
     */
    static @NonNull String extractLastPathSegment(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            Uri uri = Uri.parse(value);
            String segment = uri.getLastPathSegment();
            if (segment != null && !segment.isEmpty()) {
                return segment;
            }
        } catch (Throwable ignored) {
        }
        int queryIndex = value.indexOf('?');
        String trimmed = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        int slashIndex = trimmed.lastIndexOf('/');
        return slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : trimmed;
    }

    /**
     * Pulls the first http(s) URL out of an arbitrary {@code String.valueOf(key)} output.
     * Walks character-by-character so we don't have to know which Glide key class wrote the
     * string — DataCacheKey embeds the URL directly, ResourceCacheKey embeds it inside a
     * nested {@code sourceKey=...} field, etc.
     *
     * <p>Returns {@code null} when no URL is found. The returned URL is normalized via
     * {@link #normalizeRemoteUrl(String)} so trailing punctuation from the surrounding
     * key syntax (commas, closing braces, quotes) is stripped.
     */
    static @Nullable String extractRemoteUrl(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int httpsStart = text.indexOf("https://");
        int httpStart = text.indexOf("http://");
        int start = httpsStart >= 0 && (httpStart < 0 || httpsStart < httpStart) ? httpsStart : httpStart;
        if (start < 0) {
            return null;
        }
        int end = start;
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (Character.isWhitespace(ch) || ch == ',' || ch == '}' || ch == ')' || ch == ']') {
                break;
            }
            if (ch == '\'' || ch == '"') {
                break;
            }
            if (ch == '{' || ch == '[' || ch == '(') {
                end++;
                continue;
            }
            if (ch == '\\') {
                break;
            }
            if (Character.isISOControl(ch)) {
                break;
            }
            if (ch == '>' || ch == '<' || ch == ';' || ch == '#') {
                break;
            }
            if (ch == '&' && end + 1 < text.length() && text.charAt(end + 1) == '#') {
                break;
            }
            if (Character.isLetterOrDigit(ch) || ":/?&=._-%~+@!$,*".indexOf(ch) >= 0) {
                end++;
                continue;
            }
            break;
        }
        String candidate = text.substring(start, end);
        return normalizeRemoteUrl(candidate);
    }

    /**
     * Extracts the value of {@code sourceKey=...} from a Glide cache key's
     * {@code toString()}. Used for ResourceCacheKey / DataCacheKey where the source token
     * is the base36 hash produced by {@code Md5FileNameGenerator} rather than a URL.
     */
    static @Nullable String extractSourceKeyToken(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int start = text.indexOf("sourceKey=");
        if (start < 0) {
            return null;
        }
        start += "sourceKey=".length();
        int end = start;
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (ch == ',' || ch == '}') {
                break;
            }
            end++;
        }
        return normalizeCacheKeyToken(text.substring(start, end));
    }

    static @NonNull String normalizeCacheKeyToken(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    /**
     * Trims whitespace and trailing structural punctuation (commas, closing brackets,
     * quotes) that the surrounding Glide key syntax often leaves attached to a URL when
     * we extract it from a {@code toString()} dump.
     */
    static @NonNull String normalizeRemoteUrl(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (!normalized.isEmpty()) {
            char last = normalized.charAt(normalized.length() - 1);
            if (last == ',' || last == ')' || last == ']' || last == '}' || last == '"' || last == '\'') {
                normalized = normalized.substring(0, normalized.length() - 1);
                continue;
            }
            break;
        }
        return normalized;
    }
}
