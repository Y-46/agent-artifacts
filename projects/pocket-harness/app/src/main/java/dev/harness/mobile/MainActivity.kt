package dev.harness.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.harness.mobile.ui.AppRoot
import dev.harness.mobile.ui.theme.PocketHarnessTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HarnessApp).container
        setContent {
            PocketHarnessTheme {
                AppRoot(container)
            }
        }
        askNotificationPermission()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}