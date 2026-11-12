package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.Glossary
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.UpdateDownloadState
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.ChanTheme
import java.util.Locale

@Composable
fun StockSearchDialog(
    state: AppUiState,
    onQueryChange: (String) -> Unit,
    onSelectStock: (Stock) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = ChanTheme.Sheet,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 520.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "搜索股票或 ETF",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChanTheme.TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = ChanTheme.Accent)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    placeholder = { Text("输入代码、名称或拼音，如 600519、510300", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.searchLoading) {
                    Text(
                        text = "正在搜索...",
                        fontSize = 13.sp,
                        color = ChanTheme.TextSecondary,
                    )
                }
                if (state.searchError.isNotEmpty()) {
                    Text(
                        text = state.searchError,
                        fontSize = 13.sp,
                        color = ChanTheme.Up,
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(state.searchResults, key = { it.secid }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectStock(item)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ChanTheme.TextPrimary,
                                )
                                Text(
                                    text = "${item.symbol}  ${item.market}",
                                    fontSize = 12.sp,
                                    color = ChanTheme.TextSecondary,
                                )
                            }
                            Text(
                                text = "查看",
                                fontSize = 13.sp,
                                color = ChanTheme.Accent,
                            )
                        }
                        HorizontalDivider(color = ChanTheme.Line)
                    }
                }
            }
        }
    }
}

@Composable
fun TermInfoDialog(
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = ChanTheme.Sheet,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 580.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "术语说明",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChanTheme.TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = ChanTheme.Accent)
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(Glossary.entries, key = { it.key }) { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = item.term,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChanTheme.TextPrimary,
                            )
                            Text(
                                text = item.meaning,
                                fontSize = 13.sp,
                                color = ChanTheme.TextPrimary,
                            )
                            Text(
                                text = item.usage,
                                fontSize = 12.sp,
                                color = ChanTheme.TextSecondary,
                            )
                            HorizontalDivider(
                                color = ChanTheme.Line,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    canInstallPackages: () -> Boolean,
    onOpenInstallPermissionSettings: () -> Unit,
    onInstallApk: (String) -> Unit,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var needPermission by remember { mutableStateOf(false) }
    val canInAppUpdate = info.apkUrl != null

    AlertDialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) onDismiss()
        },
        containerColor = ChanTheme.Sheet,
        title = {
            Text(
                text = "发现新版本 v${info.versionName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "缠观 Android 发布了新版本（每 30 秒自动检测更新）：",
                    fontSize = 13.sp,
                    color = ChanTheme.TextSecondary,
                )
                info.releaseNotes.forEach { note ->
                    Text(
                        text = "• $note",
                        fontSize = 13.sp,
                        color = ChanTheme.TextPrimary,
                    )
                }
                if (!info.gitCommit.isNullOrBlank()) {
                    Text(
                        text = "构建版本 #${info.versionCode} (${info.gitCommit.take(8)})",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ChanTheme.TextSecondary,
                    )
                }

                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        val total = downloadState.total
                        Spacer(modifier = Modifier.height(6.dp))
                        if (total != null && total > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (downloadState.downloaded.toFloat() / total).coerceIn(0f, 1f)
                                },
                                color = ChanTheme.Accent,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(
                                color = ChanTheme.Accent,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = "正在下载安装包：${formatProgress(downloadState.downloaded, total)}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ChanTheme.Accent,
                        )
                    }
                    is UpdateDownloadState.Failed -> {
                        Text(
                            text = "下载失败：${downloadState.message}",
                            fontSize = 13.sp,
                            color = ChanTheme.Up,
                        )
                    }
                    is UpdateDownloadState.Ready -> {
                        Text(
                            text = "安装包已下载并通过 SHA-256 校验，点击下方按钮立即安装。",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChanTheme.Accent,
                        )
                    }
                    UpdateDownloadState.Idle -> Unit
                }

                if (needPermission) {
                    Text(
                        text = "请先在系统设置中开启「允许安装未知应用」，返回后再次点击「立即安装」。",
                        fontSize = 12.sp,
                        color = ChanTheme.Gold,
                    )
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is UpdateDownloadState.Downloading -> Unit
                is UpdateDownloadState.Ready -> Button(
                    onClick = {
                        if (canInstallPackages()) {
                            needPermission = false
                            onInstallApk(downloadState.apkPath)
                        } else {
                            needPermission = true
                            onOpenInstallPermissionSettings()
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChanTheme.Accent),
                ) {
                    Text("立即安装", color = Color.White)
                }
                else -> Button(
                    onClick = {
                        if (canInAppUpdate) {
                            onStartDownload()
                        } else {
                            uriHandler.openUri(info.downloadPageUrl)
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChanTheme.Accent),
                ) {
                    Text(
                        text = when {
                            !canInAppUpdate -> "浏览器下载"
                            downloadState is UpdateDownloadState.Failed -> "重试下载"
                            else -> "立即下载更新"
                        },
                        color = Color.White,
                    )
                }
            }
        },
        dismissButton = {
            if (downloadState is UpdateDownloadState.Failed && canInAppUpdate) {
                TextButton(
                    onClick = {
                        uriHandler.openUri(info.downloadPageUrl)
                        onDismiss()
                    },
                ) {
                    Text("浏览器下载", color = ChanTheme.Accent)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = if (downloadState is UpdateDownloadState.Downloading) "取消下载" else "稍后提醒",
                        color = ChanTheme.TextSecondary,
                    )
                }
            }
        },
    )
}

private fun formatProgress(downloaded: Long, total: Long?): String {
    fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    return if (total != null && total > 0) "${mb(downloaded)} / ${mb(total)}" else mb(downloaded)
}
