package cn.oryjk.changuan.presentation.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import cn.oryjk.changuan.domain.ApkInstallState
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.domain.DownloadedApk
import cn.oryjk.changuan.domain.Glossary
import cn.oryjk.changuan.domain.ReleaseHistoryItem
import cn.oryjk.changuan.domain.ShizukuAvailability
import cn.oryjk.changuan.domain.ShizukuChannelState
import cn.oryjk.changuan.domain.UpdateDownloadState
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ActionPill
import cn.oryjk.changuan.presentation.designsystem.ClarityCard
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.StarkBadgeTag
import cn.oryjk.changuan.presentation.designsystem.tabular
import java.util.Locale

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    installState: ApkInstallState,
    shizukuState: ShizukuChannelState,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onInstallUpdate: (DownloadedApk, AppUpdateInfo) -> Unit,
    onInstallSystem: (DownloadedApk) -> Unit,
    onOpenInstallerSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val context = LocalContext.current
    val dismissible = installState != ApkInstallState.Installing

    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClarityCard(
                modifier = Modifier.widthIn(max = 440.dp),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "发现新版本 v${update.versionName}",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.inkPrimary,
                    )
                    StarkBadgeTag(text = "build ${update.versionCode}")
                }

                val metaLine = listOfNotNull(
                    update.publishedAt?.takeIf { it.isNotBlank() },
                    update.gitCommit?.take(7)?.takeIf { it.isNotBlank() },
                    update.fileSizeBytes?.let { formatMegabytes(it) },
                ).joinToString("  ·  ")
                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = palette.inkMuted,
                    )
                }

                if (update.releaseNotes.isNotEmpty()) {
                    Surface(
                        color = palette.subtleSurface,
                        shape = ClarityRadius.tile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            update.releaseNotes.forEach { note ->
                                Text(
                                    text = "• $note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkSecondary,
                                )
                            }
                        }
                    }
                }

                when (downloadState) {
                    UpdateDownloadState.Idle -> {
                        Button(
                            onClick = { onStartDownload(update) },
                            shape = ClarityRadius.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.actionPrimary,
                                contentColor = palette.onActionPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text("立即下载更新", style = MaterialTheme.typography.labelLarge)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, update.downloadPageUrl.toUri()),
                                        )
                                    }
                                },
                            ) {
                                Text("打开下载页", color = palette.primaryBrand)
                            }
                            TextButton(onClick = onDismiss) {
                                Text("稍后提醒", color = palette.inkSecondary)
                            }
                        }
                    }

                    is UpdateDownloadState.Downloading -> {
                        val total = downloadState.total
                        val progress = if (total != null && total > 0L) {
                            (downloadState.downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = palette.primaryBrand,
                                    trackColor = palette.subtleSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(ClarityRadius.pill),
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = palette.primaryBrand,
                                    trackColor = palette.subtleSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(ClarityRadius.pill),
                                )
                            }
                            val statusText = if (total != null && total > 0L) {
                                "${formatMegabytes(downloadState.downloaded)} / ${formatMegabytes(total)} (${(progress!! * 100).toInt()}%)"
                            } else {
                                "已下载 ${formatMegabytes(downloadState.downloaded)}…"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = palette.inkSecondary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("取消下载", color = palette.inkSecondary)
                            }
                        }
                    }

                    is UpdateDownloadState.Ready -> {
                        val apk = downloadState.apk
                        when (installState) {
                            ApkInstallState.Installing -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = palette.primaryBrand,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "正在校验签名并提交安装…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.inkSecondary,
                                    )
                                }
                            }

                            ApkInstallState.Submitted -> {
                                Surface(
                                    color = palette.primaryBrandSoft,
                                    shape = ClarityRadius.tile,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "已提交安装请求，请按系统提示完成更新。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.primaryBrand,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = onDismiss) {
                                        Text("知道了", color = palette.inkPrimary)
                                    }
                                }
                            }

                            ApkInstallState.NeedsSystemPermission -> {
                                Surface(
                                    color = palette.warmAmberSurface,
                                    shape = ClarityRadius.tile,
                                    border = BorderStroke(1.dp, palette.warmAmberBorder),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "需要先允许缠观「安装未知应用」权限。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.warmAmberText,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                                Button(
                                    onClick = { onInstallSystem(apk) },
                                    shape = ClarityRadius.pill,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.actionPrimary,
                                        contentColor = palette.onActionPrimary,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp),
                                ) {
                                    Text("去授权并继续安装", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            is ApkInstallState.Failed -> {
                                Surface(
                                    color = palette.warmAmberSurface,
                                    shape = ClarityRadius.tile,
                                    border = BorderStroke(1.dp, palette.warmAmberBorder),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = installState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.warmAmberText,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { onInstallSystem(apk) },
                                        shape = ClarityRadius.pill,
                                        border = BorderStroke(1.dp, palette.cardBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 46.dp),
                                    ) {
                                        Text("系统安装", color = palette.inkPrimary)
                                    }
                                    Button(
                                        onClick = { onInstallUpdate(apk, update) },
                                        shape = ClarityRadius.pill,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.actionPrimary,
                                            contentColor = palette.onActionPrimary,
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 46.dp),
                                    ) {
                                        Text("重试安装")
                                    }
                                }
                            }

                            ApkInstallState.Idle -> {
                                Button(
                                    onClick = { onInstallUpdate(apk, update) },
                                    shape = ClarityRadius.pill,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.actionPrimary,
                                        contentColor = palette.onActionPrimary,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp),
                                ) {
                                    Text(
                                        text = if (shizukuState.useShizuku) "通过 Shizuku 安装" else "立即安装",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    if (shizukuState.useShizuku) {
                                        TextButton(onClick = { onInstallSystem(apk) }) {
                                            Text("改用系统安装", color = palette.inkSecondary)
                                        }
                                    } else {
                                        TextButton(onClick = onOpenInstallerSettings) {
                                            Text("安装方式", color = palette.primaryBrand)
                                        }
                                    }
                                    TextButton(onClick = onDismiss) {
                                        Text("稍后", color = palette.inkSecondary)
                                    }
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Failed -> {
                        Surface(
                            color = palette.warmAmberSurface,
                            shape = ClarityRadius.tile,
                            border = BorderStroke(1.dp, palette.warmAmberBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = downloadState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.warmAmberText,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        Button(
                            onClick = { onStartDownload(update) },
                            shape = ClarityRadius.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.actionPrimary,
                                contentColor = palette.onActionPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text("重试下载", style = MaterialTheme.typography.labelLarge)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, update.downloadPageUrl.toUri()),
                                        )
                                    }
                                },
                            ) {
                                Text("浏览器下载", color = palette.primaryBrand)
                            }
                            TextButton(onClick = onDismiss) {
                                Text("关闭", color = palette.inkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstallerSettingsDialog(
    state: ShizukuChannelState,
    onSetEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val statusText = when (state.availability) {
        ShizukuAvailability.Ready -> "Shizuku 已就绪"
        ShizukuAvailability.NeedsAuthorization -> "等待 Shizuku 授权"
        ShizukuAvailability.PermissionDenied -> "Shizuku 授权已拒绝"
        ShizukuAvailability.NotRunning -> "Shizuku 服务未运行"
        ShizukuAvailability.NotInstalled -> "未安装 Shizuku"
        ShizukuAvailability.Unsupported -> "当前系统不支持"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClarityCard(
                modifier = Modifier.widthIn(max = 440.dp),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "应用更新安装方式",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.inkPrimary,
                    )
                    StarkBadgeTag(
                        text = statusText,
                        inverted = state.useShizuku,
                    )
                }

                Surface(
                    color = palette.subtleSurface,
                    shape = ClarityRadius.tile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "优先使用 Shizuku 安装",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.inkPrimary,
                            )
                            Text(
                                text = "校验 SHA-256 与签名一致后直接提交安装",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary,
                            )
                        }
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = onSetEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.onActionPrimary,
                                checkedTrackColor = palette.primaryBrand,
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionPill(
                        text = "刷新状态",
                        outlined = true,
                        onClick = onRefresh,
                    )
                    if (state.availability == ShizukuAvailability.NeedsAuthorization ||
                        state.availability == ShizukuAvailability.PermissionDenied
                    ) {
                        ActionPill(
                            text = "请求授权",
                            onClick = onRequestPermission,
                        )
                    }
                    if (state.availability != ShizukuAvailability.NotInstalled &&
                        state.availability != ShizukuAvailability.Unsupported
                    ) {
                        ActionPill(
                            text = "打开 Shizuku",
                            outlined = true,
                            onClick = onOpenShizuku,
                        )
                    }
                }

                Text(
                    text = "若 Shizuku 未运行或未授权，应用将自动使用系统标准包安装器完成更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("完成", color = palette.inkPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun BackendEnvironmentDialog(
    current: BackendEnvironment,
    onSelect: (BackendEnvironment) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChanPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClarityCard(
                modifier = Modifier.widthIn(max = 440.dp),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "API 服务环境",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.inkPrimary,
                )
                Text(
                    text = "不同环境的登录会话各自独立保存，切换后立即刷新行情与登录态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackendEnvironment.entries.forEach { env ->
                        val selected = env == current
                        Surface(
                            color = if (selected) palette.primaryBrandSoft else palette.cardSurface,
                            shape = ClarityRadius.tile,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) palette.primaryBrand else palette.cardBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ClarityRadius.tile)
                                .clickable(role = Role.RadioButton) { onSelect(env) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelect(env) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = palette.primaryBrand,
                                        unselectedColor = palette.inkMuted,
                                    ),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = env.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.inkPrimary,
                                    )
                                    Text(
                                        text = env.baseUrl,
                                        style = MaterialTheme.typography.labelSmall.tabular(),
                                        color = palette.inkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = palette.inkPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseHistorySheet(
    items: List<ReleaseHistoryItem>,
    loading: Boolean,
    error: String?,
    downloadPageUrl: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ClarityRadius.sheet,
        containerColor = palette.cardSurface,
        contentColor = palette.inkPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "版本更新记录",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.inkPrimary,
                )
                ActionPill(
                    text = "下载页",
                    outlined = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = palette.inkPrimary,
                        )
                    },
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, downloadPageUrl.toUri()))
                        }
                    },
                )
            }

            when {
                loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = palette.primaryBrand,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在读取发布记录…", color = palette.inkSecondary)
                    }
                }

                error != null -> {
                    Surface(
                        color = palette.warmAmberSurface,
                        shape = ClarityRadius.tile,
                        border = BorderStroke(1.dp, palette.warmAmberBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.warmAmberText,
                            )
                            ActionPill(text = "重试", onClick = onRetry)
                        }
                    }
                }

                items.isEmpty() -> {
                    Text(
                        text = "暂无历史发布记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        itemsIndexed(items) { idx, rel ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "v${rel.versionName}",
                                            style = MaterialTheme.typography.titleMedium.tabular(),
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.inkPrimary,
                                        )
                                        StarkBadgeTag(text = "#${rel.versionCode}")
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (rel.apkAvailable) {
                                                Icons.Outlined.CheckCircle
                                            } else {
                                                Icons.Outlined.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = if (rel.apkAvailable) palette.primaryBrand else palette.inkMuted,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = if (rel.apkAvailable) "可下载" else "已归档",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (rel.apkAvailable) palette.primaryBrand else palette.inkMuted,
                                        )
                                    }
                                }
                                val sub = listOfNotNull(
                                    rel.publishedAt?.takeIf { it.isNotBlank() },
                                    rel.gitCommit?.take(7)?.takeIf { it.isNotBlank() },
                                ).joinToString("  ·  ")
                                if (sub.isNotEmpty()) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall.tabular(),
                                        color = palette.inkMuted,
                                    )
                                }
                                rel.releaseNotes.forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.inkSecondary,
                                    )
                                }
                            }
                            if (idx < items.lastIndex) {
                                LedgerHeaderRule()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermInfoSheet(
    onDismiss: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ClarityRadius.sheet,
        containerColor = palette.cardSurface,
        contentColor = palette.inkPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "缠论结构与术语说明",
                style = MaterialTheme.typography.titleLarge,
                color = palette.inkPrimary,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
            ) {
                itemsIndexed(Glossary.entries) { idx, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = entry.term,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkPrimary,
                        )
                        Text(
                            text = entry.meaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary,
                        )
                        if (entry.usage.isNotBlank()) {
                            Text(
                                text = "观察用法：${entry.usage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                            )
                        }
                    }
                    if (idx < Glossary.entries.lastIndex) {
                        LedgerHeaderRule()
                    }
                }
            }
        }
    }
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
