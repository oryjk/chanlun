package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.presentation.AppViewModel
import cn.oryjk.changuan.presentation.ChanTheme
import kotlinx.coroutines.launch

@Composable
fun RootScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ChanTheme.Sheet),
    ) {
        val useRail = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                stockName = state.stock.name,
                userNickname = state.user?.nickname,
                onOpenSearch = { showSearch = true },
                onRefresh = { viewModel.reload(refresh = true) },
            )
            HorizontalDivider(color = ChanTheme.Line)

            if (useRail) {
                Row(modifier = Modifier.weight(1f)) {
                    NavigationRail(
                        containerColor = ChanTheme.Sheet,
                    ) {
                        RootTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = state.tab == tab,
                                onClick = { viewModel.changeTab(tab) },
                                icon = { Text(tab.label.take(1), fontWeight = FontWeight.SemiBold) },
                                label = { Text(tab.label, fontSize = 12.sp) },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(ChanTheme.Line),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        TabContent(
                            viewModel = viewModel,
                            onOpenTerms = { showTerms = true },
                            scope = scope,
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    TabContent(
                        viewModel = viewModel,
                        onOpenTerms = { showTerms = true },
                        scope = scope,
                    )
                }
                HorizontalDivider(color = ChanTheme.Line)
                NavigationBar(
                    containerColor = ChanTheme.Sheet,
                    tonalElevation = 0.dp,
                ) {
                    RootTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = state.tab == tab,
                            onClick = { viewModel.changeTab(tab) },
                            icon = { Text(tab.label.take(1), fontWeight = FontWeight.SemiBold) },
                            label = { Text(tab.label, fontSize = 12.sp) },
                        )
                    }
                }
            }
        }
    }

    if (showSearch) {
        StockSearchDialog(
            state = state,
            onQueryChange = { viewModel.searchStocks(it) },
            onSelectStock = { viewModel.selectStock(it) },
            onDismiss = { showSearch = false },
        )
    }

    if (showTerms) {
        TermInfoDialog(onDismiss = { showTerms = false })
    }

    state.pendingUpdate?.let { updateInfo ->
        AppUpdateDialog(
            info = updateInfo,
            downloadState = state.updateDownloadState,
            canInstallPackages = { viewModel.canInstallPackages() },
            onOpenInstallPermissionSettings = { viewModel.openInstallPermissionSettings() },
            onInstallApk = { apkPath -> viewModel.installDownloadedUpdate(apkPath) },
            onStartDownload = { viewModel.startUpdateDownload(updateInfo) },
            onDismiss = { viewModel.dismissUpdate() },
        )
    }
}

@Composable
private fun TopBar(
    stockName: String,
    userNickname: String?,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(ChanTheme.Sheet)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "缠观",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = ChanTheme.Accent,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = userNickname ?: "游客",
            fontSize = 12.sp,
            color = ChanTheme.TextSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenSearch) {
            Text("搜索 ($stockName)", fontSize = 13.sp, color = ChanTheme.Accent)
        }
        TextButton(onClick = onRefresh) {
            Text("刷新", fontSize = 13.sp, color = ChanTheme.Accent)
        }
    }
}

@Composable
private fun TabContent(
    viewModel: AppViewModel,
    onOpenTerms: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val state by viewModel.state.collectAsState()
    when (state.tab) {
        RootTab.MARKET -> MarketScreen(
            state = state,
            onChangeTimeframe = { viewModel.changeTimeframe(it) },
            onToggleLayer = { viewModel.toggleLayer(it) },
            onRetry = { viewModel.reload(refresh = true) },
            onOpenTerms = onOpenTerms,
        )
        RootTab.RANKING -> RankingScreen(
            state = state,
            onSelectStock = { viewModel.selectStock(it, forceDaily = true) },
            onRetry = { viewModel.reload(refresh = true) },
        )
        RootTab.ACCOUNT -> AccountScreen(
            state = state,
            onSwitchAuthMode = { viewModel.switchAuthMode(it) },
            onLoginWithEmail = { email, pwd ->
                scope.launch { viewModel.loginWithEmail(email, pwd) }
            },
            onSendEmailCode = { email ->
                scope.launch { viewModel.sendEmailCode(email) }
            },
            onRegisterWithEmail = { email, code, pwd, nick ->
                scope.launch { viewModel.registerWithEmail(email, code, pwd, nick) }
            },
            onLogout = { scope.launch { viewModel.logout() } },
            onDeleteAccount = { scope.launch { viewModel.deleteAccount() } },
            onCheckUpdateNow = { viewModel.checkUpdateNow() },
            onOpenTerms = onOpenTerms,
        )
    }
}
