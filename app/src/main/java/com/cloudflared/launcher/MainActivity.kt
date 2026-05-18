package com.cloudflared.launcher

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.cloudflared.launcher.data.TunnelRepository
import com.cloudflared.launcher.termux.TermuxCommandRunner
import com.cloudflared.launcher.termux.TermuxConstants
import com.cloudflared.launcher.ui.CloudflaredLauncherApp
import com.cloudflared.launcher.ui.CloudflaredLauncherTheme

class MainActivity : ComponentActivity() {
    private val runCommandPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestTermuxRunCommandPermission()
        setContent {
            val appContext = LocalContext.current.applicationContext
            val repository = remember { TunnelRepository(appContext) }
            val runner = remember { TermuxCommandRunner(appContext) }
            CloudflaredLauncherTheme {
                CloudflaredLauncherApp(repository = repository, runner = runner)
            }
        }
    }

    private fun requestTermuxRunCommandPermission() {
        if (checkSelfPermission(TermuxConstants.PERMISSION_RUN_COMMAND) != PackageManager.PERMISSION_GRANTED) {
            runCommandPermissionLauncher.launch(TermuxConstants.PERMISSION_RUN_COMMAND)
        }
    }
}
