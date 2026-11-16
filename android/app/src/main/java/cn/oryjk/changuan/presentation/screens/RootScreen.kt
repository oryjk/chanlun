package cn.oryjk.changuan.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.presentation.AppViewModel
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.MainBottomBar
import cn.oryjk.changuan.presentation.designsystem.MainSideRail

@Composable
fun RootScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showGlossary by rememberSaveable { mutableStateOf(false) }
    var showEnvironmentDialog by rememberSaveable { mutableStateOf(false) }
    var showInstallerSettings by rememberSaveable { mutableStateOf(false) }
    var showReleaseHistory by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearSnackbar()
    }

    val snackbarHost: @Composable () -> Unit = {
        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(
                snackbarData = data,
                shape = ClarityRadius.tile,
                containerColor = palette.inkPrimary,
                contentColor = palette.backgroundCanvas,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 840.dp

            @Composable
            fun ActiveTabContent(innerModifier: Modifier) {
                when (state.tab) {
                    RootTab.MARKET -> MarketScreen(
                        state = state,
                        onSelectTimeframe = viewModel::changeTimeframe,
                        onToggleLayer = viewModel::toggleLayer,
                        onRefresh = { viewModel.reload(refresh = true) },
                        onOpenSearch = {
                            viewModel.searchStocks("")
                            showSearch = true
                        },
                        onOpenGlossary = { showGlossary = true },
                        modifier = innerModifier,
                    )

                    RootTab.RANKING -> RankingScreen(
                        state = state,
                        onRefresh = { viewModel.reload(refresh = true) },
                        onSelectStock = { stock ->
                            viewModel.selectStock(stock, forceDaily = true)
                            viewModel.changeTab(RootTab.MARKET)
                        },
                        modifier = innerModifier,
                    )

                    RootTab.ACCOUNT -> AccountScreen(
                        state = state,
                        onOpenAuth = { mode -> viewModel.openAuthDialog(mode) },
                        onLogout = { viewModel.logout() },
                        onDeleteAccount = { viewModel.deleteAccount() },
                        onSelectThemeMode = viewModel::setThemeMode,
                        onOpenEnvironmentDialog = { showEnvironmentDialog = true },
                        onOpenInstallerSettings = {
                            viewModel.refreshInstallerState()
                            showInstallerSettings = true
                        },
                        onCheckUpdate = viewModel::checkUpdateNow,
                        onOpenReleaseHistory = {
                            viewModel.loadReleaseHistory(force = true)
                            showReleaseHistory = true
                        },
                        modifier = innerModifier,
                    )
                }
            }

            if (useRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    MainSideRail(
                        currentTab = state.tab,
                        onSelect = viewModel::changeTab,
                    )
                    Scaffold(
                        containerColor = palette.backgroundCanvas,
                        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Right),
                        snackbarHost = snackbarHost,
                    ) { innerPadding ->
                        ActiveTabContent(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        )
                    }
                }
            } else {
                Scaffold(
                    containerColor = palette.backgroundCanvas,
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    snackbarHost = snackbarHost,
                    bottomBar = {
                        MainBottomBar(
                            currentTab = state.tab,
                            onSelect = viewModel::changeTab,
                        )
                    },
                ) { innerPadding ->
                    ActiveTabContent(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BackHandler(enabled = showSearch) {
                showSearch = false
            }
            StockSearchScreen(
                currentSecid = state.stock.secid,
                results = state.searchResults,
                loading = state.searchLoading,
                error = state.searchError,
                onQueryChange = viewModel::searchStocks,
                onSelectStock = { stock ->
                    showSearch = false
                    viewModel.selectStock(stock)
                },
                onBack = { showSearch = false },
            )
        }
    }

    if (showGlossary) {
        TermInfoSheet(onDismiss = { showGlossary = false })
    }

    if (showEnvironmentDialog) {
        BackendEnvironmentDialog(
            current = state.environment,
            onSelect = { env ->
                viewModel.selectEnvironment(env)
                showEnvironmentDialog = false
            },
            onDismiss = { showEnvironmentDialog = false },
        )
    }

    if (showInstallerSettings) {
        InstallerSettingsDialog(
            state = state.shizukuState,
            onSetEnabled = viewModel::setShizukuEnabled,
            onRefresh = viewModel::refreshInstallerState,
            onRequestPermission = viewModel::requestShizukuPermission,
            onOpenShizuku = viewModel::openShizuku,
            onDismiss = { showInstallerSettings = false },
        )
    }

    if (showReleaseHistory) {
        ReleaseHistorySheet(
            items = state.releaseHistoryItems,
            loading = state.releaseHistoryLoading,
            error = state.releaseHistoryError,
            downloadPageUrl = "https://ai.oryjk.cn:82/" + BuildConfig.UPDATE_DIRECTORY,
            onRetry = { viewModel.loadReleaseHistory(force = true) },
            onDismiss = { showReleaseHistory = false },
        )
    }

    if (state.showAuthDialog) {
        AuthDialog(
            mode = state.authMode,
            busy = state.accountBusy,
            sendingCode = state.sendingCode,
            cooldownSeconds = state.codeCooldownSeconds,
            error = state.accountError,
            message = state.accountMessage,
            onSwitchMode = viewModel::switchAuthMode,
            onSendCode = viewModel::sendEmailCode,
            onRegister = viewModel::registerWithEmail,
            onLogin = viewModel::loginWithEmail,
            onDismiss = viewModel::closeAuthDialog,
        )
    }

    state.pendingUpdate?.let { update ->
        AppUpdateDialog(
            update = update,
            downloadState = state.updateDownloadState,
            installState = state.installState,
            shizukuState = state.shizukuState,
            onStartDownload = viewModel::startUpdateDownload,
            onInstallUpdate = viewModel::installDownloadedUpdate,
            onInstallSystem = viewModel::installWithSystem,
            onOpenInstallerSettings = {
                viewModel.refreshInstallerState()
                showInstallerSettings = true
            },
            onDismiss = viewModel::dismissUpdate,
        )
    }
}
