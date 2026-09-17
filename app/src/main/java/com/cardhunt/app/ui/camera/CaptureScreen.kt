package com.cardhunt.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage

@Composable
fun CaptureScreen(nav: NavController, vm: CaptureViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasPermission = it
        }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            // Camera preview (hidden during confirmation)
            if (state.phase != CapturePhase.CONFIRMING) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val imageCapture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                vm.bindImageCapture(imageCapture)
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                CardFrameOverlay(vm.cardName)

                // Bottom controls
                Box(Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                    when (state.phase) {
                        CapturePhase.IDLE, CapturePhase.CAPTURING ->
                            IconButton(
                                onClick = { vm.takePhoto(context) },
                                enabled = state.phase == CapturePhase.IDLE,
                                modifier = Modifier.size(76.dp).background(
                                    MaterialTheme.colorScheme.primary, CircleShape),
                            ) { Icon(Icons.Filled.Check, "Capture", tint = Color.White) }
                        else -> {}
                    }
                }
            } else {
                // Photo confirmation view
                state.capturedPhoto?.let { file ->
                    AsyncImage(
                        model = file,
                        contentDescription = "Captured photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Confirmation buttons
                    Box(Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { vm.retakePhoto() },
                                modifier = Modifier.size(64.dp).background(
                                    MaterialTheme.colorScheme.error, CircleShape),
                            ) { Icon(Icons.Filled.Close, "Retake", tint = Color.White) }

                            IconButton(
                                onClick = { vm.confirmPhoto() },
                                modifier = Modifier.size(64.dp).background(
                                    MaterialTheme.colorScheme.primary, CircleShape),
                            ) { Icon(Icons.Filled.Check, "Confirm", tint = Color.White) }
                        }
                    }
                }
            }

            // Processing / success / failure overlays
            when (state.phase) {
                CapturePhase.PROCESSING -> OverlayCard {
                    CircularProgressIndicator()
                    Text("Stylizing your shot in the cloud…")
                    Text("Card already marked as collected — syncing.",
                        style = MaterialTheme.typography.bodySmall)
                }
                CapturePhase.COLLECTED -> OverlayCard {
                    Text("🎉 ${vm.cardName} collected!",
                        style = MaterialTheme.typography.titleLarge)
                    Text("+${state.result?.xpEarned ?: 0} XP · Original Shoot badge earned")
                    state.result?.newAchievements?.forEach {
                        Text("🏆 Achievement unlocked: ${it.name}")
                    }
                    Button(onClick = { nav.popBackStack() }) { Text("Back to map") }
                }
                CapturePhase.FAILED -> OverlayCard {
                    Text("Collection failed", style = MaterialTheme.typography.titleLarge)
                    Text(state.error ?: "Unknown error")
                    Text("Your card was rolled back.",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.retryFromFailure() }) { Text("Retry") }
                        OutlinedButton(onClick = { nav.popBackStack() }) { Text("Back") }
                    }
                }
                else -> {}
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required to collect cards.")
            }
        }
    }
}

@Composable
private fun OverlayCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)),
        contentAlignment = Alignment.Center) {
        Surface(tonalElevation = 6.dp, shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(32.dp)) {
            Column(Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content)
        }
    }
}

@Composable
private fun CardFrameOverlay(cardName: String) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width * 0.78f
        val h = w * 1.25f
        val rect = Rect(Offset((size.width - w) / 2, (size.height - h) / 2 - size.height * 0.05f),
            Size(w, h))
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, rect.top))
        drawRect(dim, topLeft = Offset(0f, rect.bottom),
            size = Size(size.width, size.height - rect.bottom))
        drawRect(dim, topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
        drawRect(dim, topLeft = Offset(rect.right, rect.top),
            size = Size(size.width - rect.right, rect.height))
        drawRoundRect(Color(0xFFF5C518), topLeft = rect.topLeft, size = rect.size,
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Text("Frame \"$cardName\" inside the card", color = Color.White,
        style = MaterialTheme.typography.bodyMedium)
    }
}