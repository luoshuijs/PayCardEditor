package com.luoshui.paycardeditor.feature.home

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.CardSnapshotFormatter
import com.luoshui.paycardeditor.model.HomeState
import kotlinx.coroutines.launch

/** Renders snapshot details and owns the clipboard side effect for the dialog. */
@Composable
internal fun HomeSnapshotDetailsDialog(
    state: HomeState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val title = stringResource(R.string.dialog_card_list_title)
    val details = CardSnapshotFormatter.buildDialogText(state)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = details,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText(title, details)),
                        )
                        Toast.makeText(
                            context,
                            R.string.copy_card_info_done,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Text(stringResource(R.string.copy_card_info))
            }
        },
    )
}
