package com.myday.app.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myday.app.settings.StopMethod
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

/** تحدّي إيقاف المنبه حسب الطريقة المختارة. يستدعي [onSolved] مرة واحدة عند النجاح. */
@Composable
fun StopChallenge(method: StopMethod, mathLevel: Int, onSolved: () -> Unit) {
    var solved by remember { mutableStateOf(false) }
    val done: () -> Unit = { if (!solved) { solved = true; onSolved() } }
    when (method) {
        StopMethod.BUTTON -> {}
        StopMethod.SLIDE -> SlideToStop(done)
        StopMethod.HOLD -> HoldToStop(done)
        StopMethod.MATH -> MathToStop(mathLevel, done)
        StopMethod.SHAKE -> ShakeToStop(done)
    }
}

@Composable
private fun SlideToStop(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var v by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(cs.errorContainer),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("← اسحب للإيقاف", style = MaterialTheme.typography.titleMedium, color = cs.onErrorContainer,
            modifier = Modifier.fillMaxWidth().background(cs.errorContainer), textAlign = TextAlign.Center)
        Slider(
            value = v,
            onValueChange = { v = it; if (it >= 0.97f) onDone() },
            onValueChangeFinished = { if (v < 0.97f) v = 0f },
            colors = SliderDefaults.colors(thumbColor = cs.error, activeTrackColor = cs.error),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        )
    }
}

@Composable
private fun HoldToStop(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onDone)
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(cs.errorContainer)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(((1f - progress.value) * 3000).toInt(), easing = LinearEasing))
                        latest()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(300)) }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).align(Alignment.CenterStart).background(cs.error))
        Text(
            if (progress.value > 0f) "استمر… ${(3 - progress.value * 3).toInt() + 1}" else "اضغط مطوّلاً للإيقاف",
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = if (progress.value > 0.5f) cs.onError else cs.onErrorContainer
        )
    }
}

private data class MathProblem(val text: String, val answer: Int)

private fun newProblem(level: Int): MathProblem {
    val r = Random.Default
    return when (level) {
        1 -> { val a = r.nextInt(3, 10); val b = r.nextInt(10, 40); MathProblem("$b + $a", a + b) }
        2 -> { val a = r.nextInt(12, 60); val b = r.nextInt(12, 60); MathProblem("$a + $b", a + b) }
        else -> { val a = r.nextInt(12, 30); val b = r.nextInt(3, 10); val c = r.nextInt(5, 40); MathProblem("$a × $b + $c", a * b + c) }
    }
}

@Composable
private fun MathToStop(level: Int, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var problem by remember { mutableStateOf(newProblem(level)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Text("حلّ لإيقاف المنبه", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        // الأرقام تُعرض من اليسار لليمين كما تُكتب المعادلات
        Text("⁦${problem.text} = ?⁩", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = input,
            onValueChange = { t ->
                val digits = t.filter { it.isDigit() || it in '٠'..'٩' }
                    .map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("").take(5)
                input = digits
                wrong = false
                val n = digits.toIntOrNull()
                if (n == problem.answer) onDone()
                else if (digits.length >= problem.answer.toString().length) wrong = true
            },
            isError = wrong,
            supportingText = if (wrong) ({ Text("إجابة غير صحيحة") }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
        )
        TextButton(onClick = { problem = newProblem(level); input = ""; wrong = false }) { Text("مسألة أخرى") }
    }
}

@Composable
private fun ShakeToStop(onDone: () -> Unit, target: Int = 15) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    val latest by rememberUpdatedState(onDone)
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember { sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    DisposableEffect(sm, sensor) {
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (g > 2.0f && now - last > 250) {
                    last = now
                    count++
                    if (count >= target) latest()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        if (sensor == null) {
            // لا يوجد حسّاس حركة: نستخدم الضغط المطوّل بديلاً
            Text("هذا الهاتف بلا حسّاس حركة", color = cs.error)
            HoldToStop(onDone)
        } else {
            Text("هزّ الهاتف للإيقاف", style = MaterialTheme.typography.titleMedium)
            Text("${count.coerceAtMost(target)} / $target", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = cs.error)
            LinearProgressIndicator(
                progress = { (count.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.8f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = cs.error,
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}
