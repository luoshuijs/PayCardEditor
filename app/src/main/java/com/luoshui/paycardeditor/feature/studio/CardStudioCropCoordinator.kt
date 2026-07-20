package com.luoshui.paycardeditor.feature.studio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.data.CardAsset
import com.luoshui.paycardeditor.data.CardAssetRepository
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI actions backed by the image picker and uCrop ActivityResult workflow. */
internal class CardStudioCropActions(
    val pickNew: () -> Unit,
    val edit: (CardAsset) -> Unit,
    val replace: (CardAsset) -> Unit,
)

/**
 * Owns the ActivityResult, temporary-file, and image-processing workflow for Card Studio.
 *
 * Keeping this integration outside [CardStudioScreen] leaves the screen responsible only for
 * presentation and event dispatch.
 */
@Composable
internal fun rememberCardStudioCropActions(
    onEvent: (CardStudioEvent) -> Unit,
    showMessage: (CharSequence) -> Unit,
): CardStudioCropActions {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cropUnavailableMessage = stringResource(R.string.asset_crop_unavailable_message)
    val defaultAssetName = stringResource(R.string.asset_default_name)
    val assetMissingMessage = stringResource(R.string.asset_missing_message)
    var pendingCrop by androidx.compose.runtime.remember { mutableStateOf<PendingCrop?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val request = pendingCrop
        if (request == null || result.resultCode != Activity.RESULT_OK) {
            pendingCrop = null
            return@rememberLauncherForActivityResult
        }
        val data = result.data
        if (data == null) {
            pendingCrop = null
            return@rememberLauncherForActivityResult
        }
        val outputUri = UCrop.getOutput(data)
        val outputPath = outputUri?.path ?: request.outputFile?.absolutePath
        if (outputPath == null) {
            pendingCrop = null
            showMessage(cropUnavailableMessage)
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val roundedFile = try {
                withContext(Dispatchers.IO) {
                    val rounded = CardAssetRepository.createTempFile("rounded_")
                    CardImageProcessor.makeRoundedPng(File(outputPath), rounded)
                    rounded
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingCrop = null
                showMessage(cropUnavailableMessage)
                return@launch
            }
            onEvent(
                CardStudioEvent.CropResult(
                    croppedFile = roundedFile,
                    displayName = request.displayName,
                    existingAssetId = request.assetId,
                ),
            )
            pendingCrop = null
        }
    }

    val launchCrop: suspend (Uri, PendingCrop) -> Unit = { sourceUri, request ->
        val result = try {
            withContext(Dispatchers.IO) { buildUCropIntent(context, sourceUri, request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (result == null) {
            pendingCrop = null
            showMessage(cropUnavailableMessage)
        } else {
            pendingCrop = result.second
            cropLauncher.launch(result.first)
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val request = pendingCrop
        val sourceUri = result.data?.data
        if (request == null || result.resultCode != Activity.RESULT_OK || sourceUri == null) {
            pendingCrop = null
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch { launchCrop(sourceUri, request) }
    }

    val launchPick: (PendingCrop) -> Unit = { request ->
        pendingCrop = request
        pickerLauncher.launch(Intent(Intent.ACTION_PICK).apply { type = "image/*" })
    }

    return CardStudioCropActions(
        pickNew = {
            launchPick(
                PendingCrop(
                    displayName = defaultAssetName,
                ),
            )
        },
        edit = { asset ->
            coroutineScope.launch {
                val sourceFile = File(asset.absolutePath)
                if (!sourceFile.exists()) {
                    onEvent(CardStudioEvent.Refresh)
                    showMessage(assetMissingMessage)
                    return@launch
                }
                launchCrop(
                    Uri.fromFile(sourceFile),
                    PendingCrop(assetId = asset.id, displayName = asset.displayName),
                )
            }
        },
        replace = { asset ->
            launchPick(PendingCrop(assetId = asset.id, displayName = asset.displayName))
        },
    )
}

private data class PendingCrop(
    val assetId: String? = null,
    val displayName: String = "",
    val outputFile: File? = null,
)

private fun buildUCropIntent(
    context: Context,
    sourceUri: Uri,
    request: PendingCrop,
): Pair<Intent, PendingCrop>? {
    val sourceFile = if (sourceUri.scheme == "content") {
        CardImageProcessor.copyUriToTemp(context, sourceUri)
    } else {
        File(checkNotNull(sourceUri.path))
    }
    val defaultName = context.getString(R.string.asset_default_name)
    val displayName = if (request.displayName == defaultName) {
        CardAssetRepository.inferDisplayName(sourceFile.name)
    } else {
        request.displayName
    }
    val outputFile = CardAssetRepository.createTempFile("ucrop_")
    val updated = request.copy(displayName = displayName, outputFile = outputFile)
    val options = UCrop.Options().apply {
        setCompressionFormat(android.graphics.Bitmap.CompressFormat.PNG)
        setHideBottomControls(false)
        setFreeStyleCropEnabled(false)
        setShowCropGrid(true)
        setShowCropFrame(true)
    }
    val cropConfig = CropConfig.load(context)
    val intent = UCrop.of(Uri.fromFile(sourceFile), Uri.fromFile(outputFile))
        .withAspectRatio(cropConfig.aspectX.toFloat(), cropConfig.aspectY.toFloat())
        .withMaxResultSize(cropConfig.maxWidth, cropConfig.maxHeight)
        .withOptions(options)
        .getIntent(context)
    return intent.takeIf { it.resolveActivity(context.packageManager) != null }?.let { it to updated }
}
