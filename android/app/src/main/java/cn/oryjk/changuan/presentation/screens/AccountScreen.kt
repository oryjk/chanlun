package cn.oryjk.changuan.presentation.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.domain.AppThemeMode
import cn.oryjk.changuan.domain.AppUser
import cn.oryjk.changuan.domain.ShizukuAvailability
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.AuthMode
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ActionPill
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.FixedHeaderPage
import cn.oryjk.changuan.presentation.designsystem.PageHeader
import cn.oryjk.changuan.presentation.designsystem.SegmentedControl
import cn.oryjk.changuan.presentation.designsystem.tabular
import kotlinx.coroutines.launch

private val ChanlunDownloadPageUrl = "https://ai.oryjk.cn:82/" + BuildConfig.UPDATE_DIRECTORY

@Composable
fun AccountScreen(
    state: AppUiState,
    onOpenAuth: (AuthMode) -> Unit,
    onLogout: suspend () -> Unit,
    onDeleteAccount: suspend () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onOpenEnvironmentDialog: () -> Unit,
    onOpenInstallerSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenReleaseHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGlossary: () -> Unit = {},
    applyStatusBarPadding: Boolean = true,
) {
    val palette = LocalChanPalette.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var showSystemConfigSheet by remember { mutableStateOf(false) }
    var confirmLogout by remember(state.user) { mutableStateOf(false) }
    var showDeleteConfirm by remember(state.user) { mutableStateOf(false) }

    val user = state.user
    val shizukuStatusText = when {
        state.shizukuState.useShizuku -> "Shizuku 已就绪"
        state.shizukuState.enabled &&
            state.shizukuState.availability == ShizukuAvailability.NeedsAuthorization -> "Shizuku 待授权"
        state.shizukuState.enabled -> "系统安装（Shizuku 未运行）"
        else -> "系统安装器"
    }

    FixedHeaderPage(
        modifier = modifier,
        applyStatusBarPadding = applyStatusBarPadding,
        header = {
            PageHeader(
                title = "菜单",
                subtitle = "账号、外观与设置",
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundCanvas),
            contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
        ) {
            item(key = "profile-x-header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileDefaultAvatar(user = user)
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = user?.nickname?.ifBlank { "同行者" } ?: "未登录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (user != null) {
                                    "个人账号已同步 · ${state.environment.shortLabel}"
                                } else {
                                    "登录后跨会话同步个人身份（${state.environment.shortLabel}）"
                                },
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = palette.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        if (user == null) {
                            ActionPill(
                                text = "登录 / 注册",
                                onClick = { onOpenAuth(AuthMode.LOGIN) },
                            )
                        } else {
                            Surface(
                                shape = ClarityRadius.pill,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, palette.cardBorder),
                                modifier = Modifier
                                    .clip(ClarityRadius.pill)
                                    .clickable(enabled = !state.accountBusy, role = Role.Button) {
                                        confirmLogout = true
                                    },
                            ) {
                                Text(
                                    text = "退出",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.inkPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Surface(
                        color = palette.subtleSurface,
                        shape = ClarityRadius.tile,
                        border = BorderStroke(0.5.dp, palette.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(ClarityRadius.tile)
                            .clickable(role = Role.Button, onClick = onOpenGlossary),
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            val compactBar = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.inkPrimary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                        contentDescription = null,
                                        tint = palette.backgroundCanvas,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                if (!compactBar) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "缠论结构术语",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.inkPrimary,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "三类买卖点、笔中枢与多级别联立释义",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.inkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "缠论结构术语",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "三类买卖点、笔中枢与多级别联立释义",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.inkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "查看",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.inkPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = palette.inkMuted,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "profile-divider-top") {
                ProfileEdgeDivider()
            }

            item(key = "profile-appearance-header") {
                ProfileXSectionTitle(title = "外观设置")
            }

            item(key = "profile-theme-mode-row") {
                val modes = AppThemeMode.entries
                val selectedThemeIndex = modes.indexOf(state.themeMode).coerceAtLeast(0)
                val onSelectModeIndex: (Int) -> Unit = { idx ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectThemeMode(modes[idx])
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 54.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    val fontScale = LocalDensity.current.fontScale
                    val compactLayout = maxWidth < 304.dp || fontScale > 1.15f
                    if (!compactLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DarkMode,
                                contentDescription = null,
                                tint = palette.inkSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "外观模式",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = palette.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "浅色、深色或跟随系统",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            SegmentedControl(
                                options = modes.map { it.label },
                                selectedIndex = selectedThemeIndex,
                                onSelect = onSelectModeIndex,
                                compact = true,
                                modifier = Modifier.width(156.dp),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DarkMode,
                                    contentDescription = null,
                                    tint = palette.inkSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "外观模式",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = palette.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "浅色、深色或跟随系统",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            SegmentedControl(
                                options = modes.map { it.label },
                                selectedIndex = selectedThemeIndex,
                                onSelect = onSelectModeIndex,
                                compact = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item(key = "profile-divider-mid") {
                ProfileEdgeDivider()
            }

            item(key = "profile-support-header") {
                ProfileXSectionTitle(title = "支持与系统")
            }

            item(key = "profile-check-update-row") {
                ProfileXSettingRow(
                    icon = Icons.Outlined.Refresh,
                    title = "检查更新",
                    subtitle = "最近巡检 ${state.lastUpdateCheckTime} · v${BuildConfig.VERSION_NAME}",
                    onClick = onCheckUpdate,
                )
            }

            item(key = "profile-release-history-row") {
                ProfileXSettingRow(
                    icon = Icons.Outlined.History,
                    title = "历史版本记录",
                    subtitle = "查看历次发版时间、版本号与更新内容",
                    trailingText = if (state.releaseHistoryItems.isNotEmpty()) {
                        "${state.releaseHistoryItems.size} 版"
                    } else {
                        "查看"
                    },
                    onClick = onOpenReleaseHistory,
                )
            }

            item(key = "profile-system-config-row") {
                ProfileXSettingRow(
                    icon = Icons.Outlined.Settings,
                    title = "环境与安装器",
                    subtitle = "服务线路切换与应用内安装权限",
                    trailingText = state.environment.label,
                    onClick = { showSystemConfigSheet = true },
                )
            }

            if (user != null) {
                item(key = "profile-delete-account-row") {
                    ProfileXSettingRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "注销缠观账号",
                        subtitle = "永久移除当前环境中的账号记录并销毁全部会话",
                        trailingText = "删除",
                        titleColor = palette.positiveRed,
                        iconTint = palette.positiveRed,
                        onClick = { if (!state.accountBusy) showDeleteConfirm = true },
                    )
                }
            }

            item(key = "profile-divider-bottom") {
                ProfileEdgeDivider()
            }

            item(key = "profile-calm-version-footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "缠观 · v${BuildConfig.VERSION_NAME} (#${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = palette.inkMuted,
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        text = "买卖点为规则化结构识别，仅供研究，不构成买卖建议",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }

    if (showSystemConfigSheet) {
        ProfileSystemConfigSheet(
            environmentLabel = state.environment.shortLabel,
            environmentBaseUrl = state.environment.baseUrl,
            installerSubtitle = shizukuStatusText,
            onDismiss = { showSystemConfigSheet = false },
            onOpenEnvironment = {
                showSystemConfigSheet = false
                onOpenEnvironmentDialog()
            },
            onOpenInstaller = {
                showSystemConfigSheet = false
                onOpenInstallerSettings()
            },
            onOpenDownloadPage = {
                showSystemConfigSheet = false
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, ChanlunDownloadPageUrl.toUri()))
                }
            },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            shape = ClarityRadius.card,
            containerColor = palette.cardSurface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "退出当前账号？",
                    color = palette.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Text(
                    text = "退出后将切换回访客看盘模式，全部行情与收盘榜单仍可继续使用。",
                    color = palette.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.accountBusy,
                    onClick = {
                        confirmLogout = false
                        scope.launch { onLogout() }
                    },
                ) {
                    Text(
                        text = "退出当前账号",
                        color = palette.inkPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(
                        text = "再想想",
                        color = palette.inkSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.accountBusy) showDeleteConfirm = false },
            shape = ClarityRadius.card,
            containerColor = palette.cardSurface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "确认注销缠观账号？",
                    color = palette.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Text(
                    text = "注销后将立即撤销全部会话并永久清除当前环境中的账号凭证，该操作不可恢复。",
                    color = palette.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.accountBusy,
                    onClick = {
                        scope.launch {
                            onDeleteAccount()
                            showDeleteConfirm = false
                        }
                    },
                ) {
                    Text(
                        text = "确认注销",
                        color = palette.positiveRed,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.accountBusy,
                    onClick = { showDeleteConfirm = false },
                ) {
                    Text(
                        text = "再想想",
                        color = palette.inkSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            },
        )
    }
}

@Composable
private fun ProfileDefaultAvatar(user: AppUser?) {
    val palette = LocalChanPalette.current
    val avatarInitial = remember(user) {
        if (user != null) {
            val trimmed = user.nickname.trim()
            if (trimmed.isNotEmpty()) {
                val cp = trimmed.codePointAt(0)
                String(Character.toChars(cp)).uppercase()
            } else {
                "观"
            }
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.subtleSurface)
            .border(0.8.dp, palette.cardBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarInitial != null) {
            Text(
                text = avatarInitial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.inkPrimary,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            val silhouetteColor = palette.inkSecondary.copy(alpha = if (palette.isDark) 0.72f else 0.52f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(
                    color = silhouetteColor,
                    radius = w * 0.165f,
                    center = Offset(w * 0.5f, h * 0.37f),
                )
                drawOval(
                    color = silhouetteColor,
                    topLeft = Offset(w * 0.21f, h * 0.61f),
                    size = Size(w * 0.58f, h * 0.46f),
                )
            }
        }
    }
}

@Composable
private fun ProfileEdgeDivider() {
    val palette = LocalChanPalette.current
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = palette.cardBorder,
    )
}

@Composable
private fun ProfileXSectionTitle(title: String) {
    val palette = LocalChanPalette.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = palette.inkPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProfileXSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    titleColor: Color? = null,
    iconTint: Color? = null,
) {
    val palette = LocalChanPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint ?: palette.inkSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: palette.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSystemConfigSheet(
    environmentLabel: String,
    environmentBaseUrl: String,
    installerSubtitle: String,
    onDismiss: () -> Unit,
    onOpenEnvironment: () -> Unit,
    onOpenInstaller: () -> Unit,
    onOpenDownloadPage: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        contentColor = palette.inkPrimary,
        tonalElevation = 0.dp,
        shape = ClarityRadius.sheet,
        contentWindowInsets = { WindowInsets.navigationBars },
        dragHandle = { ProfileSheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "系统与环境",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            ProfileEdgeDivider()
            ProfileXSettingRow(
                icon = Icons.Outlined.Settings,
                title = "后端环境",
                subtitle = environmentBaseUrl,
                trailingText = environmentLabel,
                onClick = onOpenEnvironment,
            )
            ProfileXSettingRow(
                icon = Icons.Outlined.SystemUpdate,
                title = "更新安装设置",
                subtitle = installerSubtitle,
                trailingText = "配置",
                onClick = onOpenInstaller,
            )
            ProfileXSettingRow(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                title = "应用下载页",
                subtitle = ChanlunDownloadPageUrl,
                trailingText = "打开",
                onClick = onOpenDownloadPage,
            )
        }
    }
}

@Composable
private fun ProfileSheetDragHandle() {
    val palette = LocalChanPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(ClarityRadius.pill)
                .background(palette.cardBorder),
        )
    }
}
