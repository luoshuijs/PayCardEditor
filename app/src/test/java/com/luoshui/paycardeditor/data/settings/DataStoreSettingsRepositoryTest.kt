package com.luoshui.paycardeditor.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.luoshui.paycardeditor.app.theme.AppearanceSettings
import com.luoshui.paycardeditor.app.theme.ColorMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric coverage for [DataStoreSettingsRepository].
 *
 * Important test constraints:
 *   * `Context.preferencesDataStore` allows only one delegate instance per file name
 *     in a process, so each case reuses the same DataStore and resets state by
 *     deleting the backing file in [tearDown].
 *   * `stateIn(SharingStarted.Eagerly)` keeps a hot DataStore subscription on
 *     [storeScope]. Binding it to the `runTest` TestScope would leave unfinished
 *     child coroutines at test exit, so this test owns [storeScope] and cancels it
 *     explicitly in [tearDown].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataStoreSettingsRepositoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var dispatcher: TestDispatcher
    private lateinit var storeScope: CoroutineScope

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        storeScope = TestScope(dispatcher)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        // Delete the DataStore file so test cases remain independent.
        val file = File(context.filesDir, "datastore/${PreferenceKeys.DATA_STORE_NAME}.preferences_pb")
        file.delete()
    }

    @Test
    fun `default appearance returns AppearanceSettings_Default before any write`() = runTest(dispatcher) {
        val repo = DataStoreSettingsRepository(context, storeScope)
        advanceUntilIdle()
        val state = repo.appearance.first()
        assertEquals(AppearanceSettings.Default, state)
    }

    @Test
    fun `setColorMode persists for subsequent reads`() = runTest(dispatcher) {
        val repo = DataStoreSettingsRepository(context, storeScope)
        repo.setColorMode(ColorMode.DARK_AMOLED)
        advanceUntilIdle()
        assertEquals(ColorMode.DARK_AMOLED, repo.appearance.first().colorMode)
    }

    @Test
    fun `setKeyColor persists`() = runTest(dispatcher) {
        val repo = DataStoreSettingsRepository(context, storeScope)
        repo.setKeyColor(0xFFC9A227.toInt())
        advanceUntilIdle()
        assertEquals(0xFFC9A227.toInt(), repo.appearance.first().keyColorArgb)
    }

    @Test
    fun `setPaletteStyleName and setColorSpecName persist`() = runTest(dispatcher) {
        val repo = DataStoreSettingsRepository(context, storeScope)
        repo.setPaletteStyleName("Vibrant")
        repo.setColorSpecName("SPEC_2021")
        advanceUntilIdle()
        val state = repo.appearance.first()
        assertEquals("Vibrant", state.paletteStyleName)
        assertEquals("SPEC_2021", state.colorSpecName)
    }

    @Test
    fun `unknown stored colorMode int falls back to SYSTEM via ColorMode_fromValue`() = runTest(dispatcher) {
        val repo = DataStoreSettingsRepository(context, storeScope)
        // Corrupt DataStore directly to simulate an unknown legacy enum entry.
        repo.edit { it[PreferenceKeys.COLOR_MODE] = 99 }
        advanceUntilIdle()
        assertEquals(ColorMode.SYSTEM, repo.appearance.first().colorMode)
    }

    @Test
    fun `concurrent writes converge to last-write-wins`() = runTest(dispatcher) {
        val repo = DataStoreSettingsRepository(context, storeScope)
        // DataStore serializes edit calls; the final value is the last submitted write.
        repo.setColorMode(ColorMode.LIGHT)
        repo.setColorMode(ColorMode.DARK)
        repo.setColorMode(ColorMode.MONET_LIGHT)
        advanceUntilIdle()
        assertEquals(ColorMode.MONET_LIGHT, repo.appearance.first().colorMode)
    }
}
