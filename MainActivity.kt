package com.aisignal.otc.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aisignal.otc.capture.MediaProjectionService
import com.aisignal.otc.overlay.FloatingOverlayService

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasCapturePermission by mutableStateOf(false)

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            hasCapturePermission = true
            val serviceIntent = Intent(this, MediaProjectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Screen capture permission granted.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            AISignalOTCTheme {
                MainScreen(
                    hasOverlay = hasOverlayPermission,
                    hasCapture = hasCapturePermission,
                    onRequestOverlay = { requestOverlayPermission() },
                    onRequestCapture = { requestCapturePermission() },
                    onStartFloatingOverlay = { startFloatingOverlay() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:\$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestCapturePermission() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capturePermissionLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun startFloatingOverlay() {
        if (!hasOverlayPermission) {
            Toast.makeText(this, "Please grant Overlay Permission first.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, FloatingOverlayService::class.java)
        startService(intent)
        Toast.makeText(this, "Floating overlay started! You can now open Quotex.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MainScreen(
    hasOverlay: Boolean,
    hasCapture: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestCapture: () -> Unit,
    onStartFloatingOverlay: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI SIGNAL OTC",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676)
            )
            Text(
                text = "Quotex Running Market Chart Decision Engine",
                fontSize = 12.sp,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Floating Overlay Permission",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F6FC)
                    )
                    Text(
                        text = "Allows AI Signal OTC to hover directly over Quotex without switching apps.",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = onRequestOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasOverlay) Color(0xFF238636) else Color(0xFF30363D)
                        )
                    ) {
                        Text(if (hasOverlay) "Permission Granted ✓" else "Grant Overlay Permission")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Running Chart Capture (MediaProjection)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F6FC)
                    )
                    Text(
                        text = "Allows real-time fresh frame grabbing of Quotex candles when you press SCAN.",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = onRequestCapture,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasCapture) Color(0xFF238636) else Color(0xFF30363D)
                        )
                    ) {
                        Text(if (hasCapture) "Capture Ready ✓" else "Initialize Screen Capture")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStartFloatingOverlay,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
            ) {
                Text(
                    text = "START FLOATING OVERLAY",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun AISignalOTCTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0D1117),
            surface = Color(0xFF161B22),
            primary = Color(0xFF00E676)
        ),
        content = content
    )
}
