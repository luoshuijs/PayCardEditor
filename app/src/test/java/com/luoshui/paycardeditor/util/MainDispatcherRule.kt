package com.luoshui.paycardeditor.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit @get:Rule wrapper that installs [TestDispatcher] as `Dispatchers.Main`
 * before each test and restores the previous dispatcher afterward.
 *
 * ViewModels use [androidx.lifecycle.viewModelScope], whose default dispatcher is
 * `Dispatchers.Main.immediate`. Plain JVM tests do not initialize `Dispatchers.Main`,
 * so launching ViewModel work without this rule would throw
 * `IllegalStateException("Module with the Main dispatcher had failed to initialize")`.
 *
 * This rule installs a controllable [TestDispatcher], allowing
 * `viewModelScope.launch { ... }` work to run on the test thread and be advanced by
 * `runTest { advanceUntilIdle() }`.
 *
 * Usage:
 * ```kotlin
 * class XxxViewModelTest {
 *     @get:Rule val mainDispatcherRule = MainDispatcherRule()
 *
 *     @Test fun `case` () = runTest {
 *         val vm = XxxViewModel(reader = { ... })  // No scope parameter.
 *         advanceUntilIdle()
 *         // ... assertions
 *     }
 * }
 * ```
 *
 * Defaults to [StandardTestDispatcher], which requires manual advancement. Pass
 * `UnconfinedTestDispatcher()` when a test needs launch blocks to start immediately.
 *
 * @property testDispatcher dispatcher installed as `Dispatchers.Main`; exposed so
 * tests can access its scheduler when needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
