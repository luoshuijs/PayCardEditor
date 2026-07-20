package com.luoshui.paycardeditor.feature.preview

import com.luoshui.paycardeditor.model.CardSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure function tests for [CardPreviewImageResolver].
 *
 * Robolectric is required because [CardPreviewImageResolver.resolve] calls
 * `String.toUri()`, which delegates to `android.net.Uri.parse(...)` to inspect the
 * scheme. Plain JVM tests would fail with `RuntimeException: Stub!`.
 *
 * Coverage:
 *  1. Empty snapshot face and empty replacement returns null.
 *  2. Empty snapshot face with a non-blank replacement returns the trimmed replacement.
 *  3. Primary faces with https, file, or content schemes pass through unchanged.
 *  4. Relative primary faces are joined with the download base URL.
 *  5. Leading slashes are stripped before joining relative paths.
 *  6. Non-blank replacements override the primary face without scheme/base handling.
 *  7. Whitespace-only replacements are treated as empty and fall back to primary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardPreviewImageResolverTest {

    private val emptySnapshot = CardSnapshot()

    private fun snapshotWithFace(face: String): CardSnapshot =
        CardSnapshot(personalCardFace = face)

    @Test
    fun `empty primary and empty replacement returns null`() {
        val result = CardPreviewImageResolver.resolve(emptySnapshot, replacementFace = "")
        assertNull(result)
    }

    @Test
    fun `empty primary with non-blank replacement returns replacement as-is`() {
        val replacement = "https://example.test/replacement.png"
        val result = CardPreviewImageResolver.resolve(
            emptySnapshot,
            replacementFace = replacement,
        )
        assertEquals(replacement, result)
    }

    @Test
    fun `primary with https scheme passes through unchanged`() {
        val url = "https://example.com/card.png"
        val result = CardPreviewImageResolver.resolve(snapshotWithFace(url))
        assertEquals(url, result)
    }

    @Test
    fun `primary with file scheme passes through unchanged`() {
        val url = "file:///data/local/tmp/face.png"
        val result = CardPreviewImageResolver.resolve(snapshotWithFace(url))
        assertEquals(url, result)
    }

    @Test
    fun `primary with content scheme passes through unchanged`() {
        val url = "content://media/external/images/media/42"
        val result = CardPreviewImageResolver.resolve(snapshotWithFace(url))
        assertEquals(url, result)
    }

    @Test
    fun `primary relative path is prefixed with mi pay download base`() {
        val result = CardPreviewImageResolver.resolve(snapshotWithFace("bank/icbc.png"))
        assertEquals(
            "https://did-cdn.pay.xiaomi.com/mfc/download/bank/icbc.png",
            result,
        )
    }

    @Test
    fun `primary relative path with leading slash strips slash when joining base`() {
        val result = CardPreviewImageResolver.resolve(snapshotWithFace("/bank/icbc.png"))
        assertEquals(
            "https://did-cdn.pay.xiaomi.com/mfc/download/bank/icbc.png",
            result,
        )
    }

    @Test
    fun `non-blank replacement overrides primary`() {
        val replacement = "https://override.test/r.png"
        val result = CardPreviewImageResolver.resolve(
            snapshotWithFace("https://primary.test/p.png"),
            replacementFace = replacement,
        )
        assertEquals(replacement, result)
    }

    @Test
    fun `whitespace-only replacement falls back to primary`() {
        val result = CardPreviewImageResolver.resolve(
            snapshotWithFace("https://primary.test/p.png"),
            replacementFace = "   \t  ",
        )
        assertEquals("https://primary.test/p.png", result)
    }
}
