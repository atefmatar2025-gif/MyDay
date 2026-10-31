package com.myday.app.proof

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * معاينة كاميرا تقرأ أول رمز تراه. ترجع النص عبر [onCode].
 * إن رجعت [onCode] false (رمز غير مطابق) يستمر المسح.
 */
@Composable
fun CodeScanner(
    modifier: Modifier = Modifier,
    hint: String = "وجّه الكاميرا نحو الرمز",
    onCode: (String) -> Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        if (!granted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("نحتاج إذن الكاميرا لمسح الرمز", color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("منح الإذن") }
            }
            return@Box
        }
        val currentOnCode by rememberUpdatedState(onCode)
        val executor = remember { Executors.newSingleThreadExecutor() }
        val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        val analyzer = remember {
            lateinit var a: CodeAnalyzer
            a = CodeAnalyzer { text ->
                ContextCompat.getMainExecutor(context).execute {
                    if (!currentOnCode(text)) a.reset()
                }
            }
            a
        }
        DisposableEffect(lifecycleOwner) {
            val future = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    @Suppress("DEPRECATION")
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, analyzer) }
                    p.unbindAll()
                    p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    // لا كاميرا خلفية أو فشل التهيئة
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                runCatching { provider?.unbindAll() }
                executor.shutdown()
            }
        }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.size(220.dp).border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
        )
        Text(
            hint, color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f)).padding(10.dp),
            textAlign = TextAlign.Center
        )
    }
}
