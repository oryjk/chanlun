package cn.oryjk.changuan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.oryjk.changuan.presentation.AppViewModel
import cn.oryjk.changuan.presentation.ChanGuanTheme
import cn.oryjk.changuan.presentation.screens.RootScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        (application as ChanGuanApplication).container.viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            val state by viewModel.state.collectAsState()
            val darkTheme = state.themeMode.resolveDark(isSystemInDarkTheme())

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> viewModel.activate(true)
                        Lifecycle.Event.ON_RESUME -> viewModel.refreshInstallerState()
                        Lifecycle.Event.ON_STOP -> viewModel.activate(false)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            ChanGuanTheme(darkTheme = darkTheme) {
                RootScreen(viewModel = viewModel)
            }
        }
    }
}
