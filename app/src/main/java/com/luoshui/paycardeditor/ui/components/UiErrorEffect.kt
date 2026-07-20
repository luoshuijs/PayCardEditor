package com.luoshui.paycardeditor.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.luoshui.paycardeditor.ui.UiText
import kotlinx.coroutines.flow.Flow

/** Collects recoverable UI errors and presents each one as a short Toast. */
@Composable
fun UiErrorEffect(errorEvents: Flow<UiText>) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(errorEvents) {
        errorEvents.collect { uiText ->
            val message = resources.getString(uiText.resId, *uiText.args.toTypedArray())
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
