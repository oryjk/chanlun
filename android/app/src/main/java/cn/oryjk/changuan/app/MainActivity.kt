package cn.oryjk.changuan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
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
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> viewModel.activate(true)
                        Lifecycle.Event.ON_STOP -> viewModel.activate(false)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            ChanGuanTheme {
                RootScreen(viewModel = viewModel)
            }
        }
    }
}
