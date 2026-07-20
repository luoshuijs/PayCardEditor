package com.luoshui.paycardeditor.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.app.theme.PayCardThemedContent
import com.luoshui.paycardeditor.core.MiPayNavigator
import com.luoshui.paycardeditor.data.BankCardRuleRepository
import com.luoshui.paycardeditor.data.CardAssetRepository
import com.luoshui.paycardeditor.data.ModuleStateRepository
import com.luoshui.paycardeditor.feature.home.HomeScreen
import com.luoshui.paycardeditor.feature.home.HomeViewModel
import com.luoshui.paycardeditor.feature.preview.CardPreviewImageResolver
import com.luoshui.paycardeditor.feature.preview.CardPreviewRuleInfo
import com.luoshui.paycardeditor.feature.preview.CardPreviewScreen
import com.luoshui.paycardeditor.feature.preview.CardPreviewStateProjection
import com.luoshui.paycardeditor.feature.preview.CardPreviewViewModel
import com.luoshui.paycardeditor.feature.settings.SettingsActivity
import com.luoshui.paycardeditor.feature.studio.CardStudioScreen
import com.luoshui.paycardeditor.feature.studio.CardStudioViewModel
import com.luoshui.paycardeditor.feature.troubleshoot.TroubleshootActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ROUTE_HOME = "home"
private const val ROUTE_STUDIO = "studio"
private const val ROUTE_PREVIEW = "preview"

/**
 * Main app shell: [Scaffold], [TopAppBar], [NavigationBar], and [NavHost].
 *
 * [PayCardThemedContent] owns the app theme and edge-to-edge setup; Activities do not call
 * `enableEdgeToEdge()` directly.
 *
 * [ComponentActivity] is enough because the UI is Compose-only and does not need Fragment or
 * AppCompat APIs. Route ViewModels are created with `viewModel(factory = ...)`, so the
 * NavBackStackEntry ViewModelStore retains state across configuration changes.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PayCardThemedContent {
                val context = LocalContext.current
                MainShell(
                    openSettings = {
                        startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    openTroubleshoot = {
                        startActivity(Intent(context, TroubleshootActivity::class.java))
                    },
                    openMiPay = { MiPayNavigator.open(context) },
                    showMessage = ::showMessage,
                )
            }
        }
    }

    /**
     * Compatibility entry point for screen-level success feedback passed through [MainShell].
     *
     * Error feedback flows through ViewModel `errorEvents`; this method remains for success and
     * workflow messages produced by studio and preview actions.
     */
    fun showMessage(message: CharSequence) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    openSettings: () -> Unit,
    openTroubleshoot: () -> Unit,
    openMiPay: () -> Unit,
    showMessage: (CharSequence) -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // The tab list is stable across recomposition and does not need remember.
    val tabs = listOf(
        TabSpec(ROUTE_HOME, R.string.tab_home, Icons.Default.Home),
        TabSpec(ROUTE_STUDIO, R.string.tab_studio, Icons.Default.Palette),
        TabSpec(ROUTE_PREVIEW, R.string.tab_preview, Icons.Default.CreditCard),
    )

    Scaffold(
        topBar = {
            val titleRes = when (currentRoute) {
                ROUTE_STUDIO -> R.string.tab_studio
                ROUTE_PREVIEW -> R.string.tab_preview
                else -> R.string.tab_home
            }
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                actions = {
                    IconButton(onClick = openSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_menu_action),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route ||
                        backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                // Return to the start destination without stacking duplicate tabs.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(ROUTE_HOME) {
                HomeRoute(
                    openTroubleshoot = openTroubleshoot,
                    openMiPay = openMiPay,
                )
            }
            composable(ROUTE_STUDIO) {
                CardStudioRoute(showMessage = showMessage)
            }
            composable(ROUTE_PREVIEW) {
                CardPreviewRoute()
            }
        }
    }
}

@Composable
private fun HomeRoute(
    openTroubleshoot: () -> Unit,
    openMiPay: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    // The NavBackStackEntry ViewModelStore keeps route state across configuration changes.
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    homeStateLoader = {
                        // loadHomeState() performs synchronous SharedPreferences IO.
                        withContext(Dispatchers.IO) { ModuleStateRepository.loadHomeState() }
                    },
                    syncTrigger = {
                        // SyncSnapshots reloads paycardeditor_state after this trigger returns.
                        // The hook process may update that file asynchronously, so no direct work is needed here.
                    },
                    serviceConnectedFlow = app.xposedServiceConnected,
                )
            }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                com.luoshui.paycardeditor.feature.home.HomeEvent.OpenTroubleshoot -> openTroubleshoot()
                com.luoshui.paycardeditor.feature.home.HomeEvent.OpenMiPay -> openMiPay()
                else -> viewModel.handleEvent(event)
            }
        },
        errorEvents = viewModel.errorEvents,
    )
}

/**
 * [CardStudioScreen] route with reader and writer lambdas backed by the production repositories.
 *
 * Repository calls that touch SharedPreferences or files run on [Dispatchers.IO]. [showMessage]
 * handles success Toasts; error paths flow through ViewModel `errorEvents`.
 */
@Composable
private fun CardStudioRoute(
    showMessage: (CharSequence) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE") // Keep context available for future route-level dependency injection.
    val context = LocalContext.current
    val viewModel: CardStudioViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CardStudioViewModel(
                    assetReader = {
                        withContext(Dispatchers.IO) { CardAssetRepository.listAssets() }
                    },
                    assignmentCounts = { assets ->
                        // Load rules once and compute counts in memory instead of reading per asset.
                        withContext(Dispatchers.IO) {
                            val rules = BankCardRuleRepository.loadRules()
                            val countByReplaceArt = rules
                                .groupingBy { it.replaceCardArt }
                                .eachCount()
                            assets.associate { asset ->
                                asset.id to (countByReplaceArt[asset.contentUri().toString()] ?: 0)
                            }
                        }
                    },
                    snapshotsReader = {
                        withContext(Dispatchers.IO) {
                            ModuleStateRepository.loadHomeState().cardState.cards.filter { it.supportsCustomCardArt }
                        }
                    },
                    assetSaver = { croppedFile, displayName, existingAssetId ->
                        // The screen's uCrop callback already creates the rounded PNG.
                        withContext(Dispatchers.IO) {
                            CardAssetRepository.saveAsset(
                                sourceFile = croppedFile,
                                displayName = displayName,
                                existingAssetId = existingAssetId,
                            )
                        }
                    },
                    assetDeleter = { assetId ->
                        withContext(Dispatchers.IO) { CardAssetRepository.deleteAsset(assetId) }
                    },
                    rulesForAssetRemover = { assetId ->
                        withContext(Dispatchers.IO) { BankCardRuleRepository.removeRulesForAsset(assetId) }
                    },
                    ruleWriter = { snapshot, asset ->
                        withContext(Dispatchers.IO) { BankCardRuleRepository.upsertRule(snapshot, asset) }
                    },
                    customizedReader = { snapshot ->
                        // Preload the custom-rule flag so dialog rows do not issue repeated lookups.
                        withContext(Dispatchers.IO) {
                            BankCardRuleRepository.findRule(snapshot) != null
                        }
                    },
                )
            }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CardStudioScreen(
        uiState = uiState,
        onEvent = viewModel::handleEvent,
        showMessage = showMessage,
        errorEvents = viewModel.errorEvents,
    )
}

/**
 * [CardPreviewScreen] route with repository-backed readers and the default image resolver.
 *
 * Rule matching loads [BankCardRuleRepository.loadRules] once and associates each snapshot in the
 * IO coroutine. Restore success and failure feedback are emitted by the ViewModel channels and
 * handled by the screen.
 */
@Composable
private fun CardPreviewRoute() {
    val viewModel: CardPreviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CardPreviewViewModel(
                    cardStateReader = {
                        withContext(Dispatchers.IO) {
                            val state = ModuleStateRepository.loadHomeState()
                            CardPreviewStateProjection(
                                cards = state.cardState.cards,
                                warning = state.cardState.warning,
                            )
                        }
                    },
                    ruleLookup = { snapshots ->
                        // Load rules once and match snapshots in memory to avoid repeated
                        // SharedPreferences reads and JSON parsing per snapshot.
                        withContext(Dispatchers.IO) {
                            val rules = BankCardRuleRepository.loadRules()
                            snapshots.associate { snap ->
                                val match = rules.firstOrNull { it.matches(snap) }
                                snap.key to if (match != null) {
                                    CardPreviewRuleInfo(
                                        applied = true,
                                        replacementFace = match.replaceCardArt,
                                    )
                                } else {
                                    CardPreviewRuleInfo.EMPTY
                                }
                            }
                        }
                    },
                    ruleRemover = { snapshot ->
                        withContext(Dispatchers.IO) {
                            BankCardRuleRepository.removeRule(snapshot)
                        }
                    },
                    imageResolver = CardPreviewImageResolver::resolve,
                )
            }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CardPreviewScreen(
        uiState = uiState,
        onEvent = viewModel::handleEvent,
        errorEvents = viewModel.errorEvents,
        effects = viewModel.effects,
    )
}

private data class TabSpec(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)
