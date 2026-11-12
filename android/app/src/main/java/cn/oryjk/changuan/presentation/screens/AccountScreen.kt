package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.AuthMode
import cn.oryjk.changuan.presentation.ChanTheme

@Composable
fun AccountScreen(
    state: AppUiState,
    onSwitchAuthMode: (AuthMode) -> Unit,
    onLoginWithEmail: (String, String) -> Unit,
    onSendEmailCode: (String) -> Unit,
    onRegisterWithEmail: (String, String, String, String?) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onCheckUpdateNow: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(ChanTheme.Sheet),
    ) {
        if (state.user != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "当前账号",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChanTheme.TextPrimary,
                )
                Text(
                    text = state.user.nickname,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChanTheme.TextPrimary,
                )
                Text(
                    text = "邮箱密码账号已登录",
                    fontSize = 13.sp,
                    color = ChanTheme.TextSecondary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onLogout,
                        enabled = !state.accountBusy,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text("退出登录", color = ChanTheme.Accent)
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !state.accountBusy,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text("删除账号", color = ChanTheme.Up)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "账号与登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChanTheme.TextPrimary,
                )
                Text(
                    text = "游客可使用全部行情、结构与收盘榜单功能",
                    fontSize = 13.sp,
                    color = ChanTheme.TextSecondary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    AuthModeTab(
                        title = "账号密码登录",
                        selected = state.authMode == AuthMode.LOGIN,
                        onClick = { onSwitchAuthMode(AuthMode.LOGIN) },
                    )
                    AuthModeTab(
                        title = "邮箱验证码注册",
                        selected = state.authMode == AuthMode.REGISTER,
                        onClick = { onSwitchAuthMode(AuthMode.REGISTER) },
                    )
                }
                HorizontalDivider(color = ChanTheme.Line)

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱账号") },
                    placeholder = { Text("例如 trader@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.authMode == AuthMode.REGISTER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(6) },
                            label = { Text("6 位邮箱验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { onSendEmailCode(email) },
                            enabled = !state.accountBusy && state.codeCooldownSeconds == 0 && email.isNotBlank(),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = if (state.codeCooldownSeconds > 0) {
                                    "${state.codeCooldownSeconds} 秒后重发"
                                } else {
                                    "获取验证码"
                                },
                                fontSize = 13.sp,
                                color = ChanTheme.Accent,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("昵称（选填）") },
                        placeholder = { Text("默认使用邮箱前缀") },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (state.authMode == AuthMode.LOGIN) "登录密码" else "设置密码（至少 8 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        if (state.authMode == AuthMode.LOGIN) {
                            onLoginWithEmail(email, password)
                        } else {
                            onRegisterWithEmail(email, code, password, nickname)
                        }
                    },
                    enabled = !state.accountBusy && email.isNotBlank() && password.isNotBlank() &&
                        (state.authMode == AuthMode.LOGIN || code.length == 6),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChanTheme.Accent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (state.authMode == AuthMode.LOGIN) "登录缠观" else "验证邮箱并注册",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (state.accountBusy) {
            StatusBanner(text = "正在处理账号请求，请稍候...")
        }
        if (state.accountMessage.isNotEmpty()) {
            Text(
                text = state.accountMessage,
                fontSize = 13.sp,
                color = ChanTheme.Accent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (state.accountError.isNotEmpty()) {
            Text(
                text = state.accountError,
                fontSize = 13.sp,
                color = ChanTheme.Up,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        SectionBreak()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "关于缠观",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
            Text(
                text = "股票与 ETF 行情、量价及缠论结构分析工具",
                fontSize = 14.sp,
                color = ChanTheme.TextPrimary,
            )
            Text(
                text = "买卖点为规则化结构识别，不构成买卖建议",
                fontSize = 13.sp,
                color = ChanTheme.TextSecondary,
            )
            Text(
                text = "当前版本 v${BuildConfig.VERSION_NAME} (#${BuildConfig.VERSION_CODE})  最近自动巡检 ${state.lastUpdateCheckTime}（每 30 秒自动检测）",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onCheckUpdateNow,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("立即检查更新", fontSize = 14.sp, color = ChanTheme.Accent)
                }
                TextButton(
                    onClick = onOpenTerms,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("查看缠论与指标术语说明", fontSize = 14.sp, color = ChanTheme.Accent)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除缠观账号？") },
            text = {
                Text("账号与全部登录会话将立即删除。删除后您仍可作为游客使用全部行情与榜单功能。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteAccount()
                    },
                ) {
                    Text("确认删除账号", color = ChanTheme.Up)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", color = ChanTheme.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun AuthModeTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(top = 6.dp),
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ChanTheme.Accent else ChanTheme.TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(2.dp)
                .background(if (selected) ChanTheme.Accent else Color.Transparent),
        )
    }
}
