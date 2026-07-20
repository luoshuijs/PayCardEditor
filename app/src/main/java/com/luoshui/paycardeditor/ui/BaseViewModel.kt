package com.luoshui.paycardeditor.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * UI text token shared by ViewModels and screens.
 *
 * ViewModels keep error text as resource ids and optional format args instead of resolving
 * strings directly. Screens resolve the token with `context.getString(resId, *args)`, keeping
 * ViewModels independent from Android [android.content.Context].
 *
 * @property resId string resource id
 * @property args format arguments for placeholders such as `%1$s` or `%1$d`
 */
data class UiText(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList(),
)

/**
 * Shared base class for module ViewModels.
 *
 * The class extends [ViewModel] so route instances are owned by a ViewModelStore and keep their
 * state across configuration changes. It also centralizes one-shot [errorEvents] for IO failures
 * and other recoverable errors.
 *
 * `extraBufferCapacity = 4` lets short bursts of errors enqueue without blocking common paths,
 * while `replay = 0` prevents a new screen subscription from replaying already-dismissed errors.
 * Subclasses should call [emitError] from [viewModelScope] or another coroutine scope.
 */
abstract class BaseViewModel : ViewModel() {

    private val _errorEvents = MutableSharedFlow<UiText>(extraBufferCapacity = 4)

    /**
     * One-shot error message stream. Typical screen collection:
     * ```kotlin
     * LaunchedEffect(errorEvents) {
     *     errorEvents.collect { uiText ->
     *         val message = context.getString(uiText.resId, *uiText.args.toTypedArray())
     *         Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
     *     }
     * }
     * ```
     */
    val errorEvents: SharedFlow<UiText> = _errorEvents.asSharedFlow()

    /**
     * Emits an error token from a coroutine scope, usually [viewModelScope].
     */
    protected suspend fun emitError(text: UiText) {
        _errorEvents.emit(text)
    }
}

/**
 * Shared empty `errorEvents` flow for screens rendered without a ViewModel.
 *
 * This keeps UI tests and previews simple: collecting from the flow is a no-op when no emitter
 * exists. The `internal` visibility keeps the singleton scoped to the app module.
 */
internal val EmptyErrorEvents: SharedFlow<UiText> =
    MutableSharedFlow<UiText>().asSharedFlow()
