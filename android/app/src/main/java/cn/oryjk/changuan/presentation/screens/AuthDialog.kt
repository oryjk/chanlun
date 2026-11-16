package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.oryjk.changuan.presentation.AuthMode
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ClarityCard
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.SegmentedControl
import cn.oryjk.changuan.presentation.designsystem.tabular
import kotlinx.coroutines.launch

@Composable
fun AuthDialog(
    mode: AuthMode,
    busy: Boolean,
    sendingCode: Boolean,
    cooldownSeconds: Int,
    error: String,
    message: String,
    onSwitchMode: (AuthMode) -> Unit,
    onSendCode: suspend (String) -> Unit,
    onRegister: suspend (String, String, String, String?) -> Unit,
    onLogin: suspend (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val scope = rememberCoroutineScope()

    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.inkPrimary,
        unfocusedTextColor = palette.inkPrimary,
        focusedBorderColor = palette.inkPrimary,
        unfocusedBorderColor = palette.cardBorder,
        focusedLabelColor = palette.inkPrimary,
        unfocusedLabelColor = palette.inkSecondary,
        focusedContainerColor = palette.cardSurface,
        unfocusedContainerColor = palette.cardSurface,
        cursorColor = palette.inkPrimary,
    )

    val canSubmit = if (mode == AuthMode.LOGIN) {
        email.isNotBlank() && password.length >= 8 && !busy
    } else {
        email.isNotBlank() && code.trim().length == 6 && password.length >= 8 && !busy
    }

    fun submit() {
        if (!canSubmit) return
        scope.launch {
            if (mode == AuthMode.LOGIN) {
                onLogin(email, password)
            } else {
                onRegister(email, code, password, nickname.ifBlank { null })
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClarityCard(
                modifier = Modifier.widthIn(max = 440.dp),
                contentPadding = PaddingValues(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (mode == AuthMode.LOGIN) "登录缠观" else "邮箱注册",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.inkPrimary,
                        )
                        Text(
                            text = if (mode == AuthMode.LOGIN) {
                                "使用已注册邮箱与密码登录；不登录也可使用全部行情与榜单。"
                            } else {
                                "通过邮箱验证码创建缠观独立账号，并设置登录密码。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary,
                        )
                    }

                    SegmentedControl(
                        options = listOf("邮箱登录", "邮箱注册"),
                        selectedIndex = if (mode == AuthMode.LOGIN) 0 else 1,
                        onSelect = { idx ->
                            onSwitchMode(if (idx == 0) AuthMode.LOGIN else AuthMode.REGISTER)
                        },
                        compact = true,
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("邮箱") },
                        placeholder = { Text("name@example.com") },
                        singleLine = true,
                        enabled = !busy,
                        colors = fieldColors,
                        shape = ClarityRadius.field,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (mode == AuthMode.REGISTER) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { raw ->
                                code = raw.filter { it.isDigit() }.take(6)
                            },
                            label = { Text("6位邮箱验证码") },
                            singleLine = true,
                            enabled = !busy,
                            colors = fieldColors,
                            shape = ClarityRadius.field,
                            textStyle = MaterialTheme.typography.titleSmall.tabular(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedButton(
                            onClick = { scope.launch { onSendCode(email) } },
                            enabled = !busy && !sendingCode && cooldownSeconds <= 0 && email.isNotBlank(),
                            shape = ClarityRadius.pill,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.cardSurface,
                                contentColor = palette.inkPrimary,
                                disabledContentColor = palette.inkMuted,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                        ) {
                            if (sendingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.primaryBrand,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在发送验证码…", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Text(
                                    text = if (cooldownSeconds > 0) "${cooldownSeconds}s 后重发验证码" else "获取邮箱验证码",
                                    style = MaterialTheme.typography.labelLarge.tabular(),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it.take(24) },
                            label = { Text("昵称（选填）") },
                            singleLine = true,
                            enabled = !busy,
                            colors = fieldColors,
                            shape = ClarityRadius.field,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = {
                            Text(if (mode == AuthMode.REGISTER) "设置密码（至少 8 位）" else "密码")
                        },
                        singleLine = true,
                        enabled = !busy,
                        colors = fieldColors,
                        shape = ClarityRadius.field,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    tint = palette.inkSecondary,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (error.isNotBlank()) {
                        Surface(
                            color = palette.warmAmberSurface,
                            shape = ClarityRadius.tile,
                            border = BorderStroke(1.dp, palette.warmAmberBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.warmAmberText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }

                    if (message.isNotBlank()) {
                        Surface(
                            color = palette.primaryBrandSoft,
                            shape = ClarityRadius.tile,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.primaryBrand,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }

                    Button(
                        onClick = { submit() },
                        enabled = canSubmit,
                        shape = ClarityRadius.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.actionPrimary,
                            contentColor = palette.onActionPrimary,
                            disabledContainerColor = palette.subtleSurface,
                            disabledContentColor = palette.inkMuted,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = palette.onActionPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (mode == AuthMode.LOGIN) "登录" else "完成注册并登录",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !busy,
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                        ) {
                            Text(
                                text = "暂不登录",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.inkSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
