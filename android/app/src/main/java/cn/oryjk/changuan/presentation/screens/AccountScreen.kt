package cn.oryjk.changuan.presentation.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.domain.AppThemeMode
import cn.oryjk.changuan.domain.ShizukuAvailability
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.AuthMode
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ActionPill
import cn.oryjk.changuan.presentation.designsystem.ClarityCard
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.FixedHeaderPage
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.PageHeader
import cn.oryjk.changuan.presentation.designsystem.SegmentedControl
import cn.oryjk.changuan.presentation.designsystem.StarkBadgeTag
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
    applyStatusBarPadding: Boolean = true,
) {
    val palette = LocalChanPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val shizukuStatusText = when {
        state.shizukuState.useShizuku -> "Shizuku 已就绪"
        state.shizukuState.enabled && state.shizukuState.availability == ShizukuAvailability.NeedsAuthorization -> "Shizuku 待授权"
        state.shizukuState.enabled -> "Shizuku 未运行（自动回退系统安装）"
        else -> "系统安装器"
    }

    FixedHeaderPage(
        modifier = modifier,
        applyStatusBarPadding = applyStatusBarPadding,
        header = {
            PageHeader(
                title = "菜单",
                subtitle = "账号会话、界面外观、网络环境与更新通道",
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundCanvas)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Identity Card
            ClarityCard(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(palette.subtleSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = null,
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val user = state.user
                        Text(
                            text = user?.nickname?.takeIf { it.isNotBlank() } ?: "游客看盘中",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (user != null) {
                                "账号 ID ${user.userId.take(8)}（${state.environment.shortLabel}）"
                            } else {
                                "登录后可跨会话保留个人身份（${state.environment.shortLabel}）"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (state.user == null) {
                        ActionPill(
                            text = "登录 / 注册",
                            onClick = { onOpenAuth(AuthMode.LOGIN) },
                        )
                    } else {
                        ActionPill(
                            text = "退出",
                            outlined = true,
                            enabled = !state.accountBusy,
                            onClick = { scope.launch { onLogout() } },
                        )
                    }
                }

                if (state.user == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StarkBadgeTag(text = "邮箱密码登录")
                        StarkBadgeTag(text = "邮箱验证码注册")
                    }
                }
            }

            // Appearance Card
            ClarityCard(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "界面外观",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.inkPrimary,
                    )
                    Text(
                        text = state.themeMode.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.inkSecondary,
                    )
                }
                val modes = AppThemeMode.entries
                SegmentedControl(
                    options = modes.map { it.label },
                    selectedIndex = modes.indexOf(state.themeMode).coerceAtLeast(0),
                    onSelect = { idx -> onSelectThemeMode(modes[idx]) },
                    compact = true,
                )
            }

            // Network & Update Group Card
            ClarityCard(contentPadding = PaddingValues(0.dp)) {
                MenuActionRow(
                    icon = Icons.Outlined.CloudQueue,
                    title = "API 服务环境",
                    subtitle = state.environment.baseUrl,
                    trailingTag = state.environment.shortLabel,
                    onClick = onOpenEnvironmentDialog,
                )
                LedgerHeaderRule()
                MenuActionRow(
                    icon = Icons.Outlined.Tune,
                    title = "更新安装方式",
                    subtitle = shizukuStatusText,
                    onClick = onOpenInstallerSettings,
                )
                LedgerHeaderRule()
                MenuActionRow(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "检测新版本",
                    subtitle = "上次检测 ${state.lastUpdateCheckTime}",
                    trailingTag = "v${BuildConfig.VERSION_NAME}",
                    onClick = onCheckUpdate,
                )
                LedgerHeaderRule()
                MenuActionRow(
                    icon = Icons.Outlined.History,
                    title = "版本更新记录",
                    subtitle = "查看各版本发布说明与安装包状态",
                    onClick = onOpenReleaseHistory,
                )
                LedgerHeaderRule()
                MenuActionRow(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    title = "应用下载页",
                    subtitle = ChanlunDownloadPageUrl,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, ChanlunDownloadPageUrl.toUri()),
                            )
                        }
                    },
                )
            }

            // Danger Action when signed in
            if (state.user != null) {
                ClarityCard(contentPadding = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.accountBusy, role = Role.Button) {
                                showDeleteConfirm = true
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = palette.positiveRed,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "删除缠观账号",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.positiveRed,
                            )
                            Text(
                                text = "永久移除当前环境中的账号记录并销毁全部登录会话",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                            )
                        }
                    }
                }
            }

            Text(
                text = "缠观 Android v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})  " +
                    "买卖点为规则化结构识别，仅供研究，不构成买卖建议",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = palette.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }
    }

    if (showDeleteConfirm) {
        Dialog(
            onDismissRequest = { if (!state.accountBusy) showDeleteConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ClarityCard(
                    modifier = Modifier.widthIn(max = 420.dp),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "确认删除缠观账号？",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.inkPrimary,
                    )
                    Text(
                        text = "删除后将立即撤销所有设备会话并清理账号凭证，该操作不可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = false },
                            enabled = !state.accountBusy,
                            shape = ClarityRadius.pill,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("取消", color = palette.inkPrimary)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    onDeleteAccount()
                                    showDeleteConfirm = false
                                }
                            },
                            enabled = !state.accountBusy,
                            shape = ClarityRadius.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.positiveRed,
                                contentColor = palette.onActionPrimary,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("确认删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingTag: String? = null,
) {
    val palette = LocalChanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.inkSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingTag != null) {
            StarkBadgeTag(text = trailingTag)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}
