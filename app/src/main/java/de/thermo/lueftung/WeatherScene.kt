package de.thermo.lueftung

import android.os.SystemClock
import android.app.ActivityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import java.time.LocalTime
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sin
import kotlin.random.Random

// All assets share the same 3:5 composition. Only the selected bitmap is loaded.
fun weatherImage(condition: String): Int = when (condition) {
    "Bewölkt" -> R.drawable.weather_portrait_cloudy
    "Regen" -> R.drawable.weather_portrait_rain
    "Gewitter" -> R.drawable.weather_portrait_storm
    "Schnee" -> R.drawable.weather_portrait_snow
    "Nacht" -> R.drawable.weather_portrait_night
    else -> R.drawable.weather_portrait_sunny
}

@Composable
fun WeatherConditionIcon(condition: String, modifier: Modifier = Modifier) {
    if (condition == "Regen" || condition == "Gewitter") {
        Box(modifier) {
            Icon(Icons.Filled.Cloud, null, tint = Color(0xFFDAE9EE),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.72f).align(Alignment.TopCenter))
            Canvas(Modifier.fillMaxSize()) {
                repeat(3) { i ->
                    val x = size.width * (.28f + i * .21f)
                    drawLine(Color(0xFF2CC7E8), Offset(x, size.height * .7f),
                        Offset(x - size.width * .07f, size.height * .91f),
                        size.width * .065f, cap = StrokeCap.Round)
                }
            }
            if (condition == "Gewitter") Icon(Icons.Filled.Bolt, null,
                tint = Color(0xFFFFD34D), modifier = Modifier.size(17.dp).align(Alignment.Center))
        }
    } else {
        Icon(when (condition) {
            "Sonnig" -> Icons.Filled.WbSunny
            "Schnee" -> Icons.Filled.AcUnit
            "Nacht" -> Icons.Filled.Nightlight
            else -> Icons.Filled.Cloud
        }, null, modifier, tint = if (condition == "Sonnig" || condition == "Nacht")
            Color(0xFFFFD34D) else Color(0xFFDAE9EE))
    }
}

@Composable
fun AnimatedHouseScene(condition: String, modifier: Modifier = Modifier, animationsEnabled: Boolean = true, hourOverride: Int? = null) {
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) { while (true) { delay(60000); hour=LocalTime.now().hour } }
    val lighting=weatherLighting(condition,hourOverride ?: hour)
    val night=lighting==WeatherLighting.NIGHT
    val dusk=lighting==WeatherLighting.DUSK
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var visible by remember { mutableStateOf(true) }
    val view = LocalView.current
    Box(modifier.clipToBounds().onGloballyPositioned {
        val bounds = it.boundsInWindow()
        visible = bounds.width > 0 && bounds.height > 0 && bounds.bottom > 0 &&
            bounds.top < view.height && bounds.right > 0 && bounds.left < view.width
    }) {
        Image(
            painterResource(weatherImage(if(night && condition=="Sonnig") "Nacht" else condition)),
            contentDescription = "Waldlichtung · $condition",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Lighting is independent of animated effects and remains when motion is disabled.
        Canvas(Modifier.fillMaxSize()) {
            if (night && condition!="Nacht" && condition!="Sonnig") drawRect(Color(0xFF03152E).copy(alpha=.48f))
            else if(dusk && condition!="Nacht") drawRect(Color(0xFFEFAC75).copy(alpha=.12f))
            else if(condition=="Regen" || condition=="Gewitter") drawRect(Color(0xFFD5E8F5).copy(alpha=.06f))
        }
        if (animationsEnabled && lifecycle == Lifecycle.State.RESUMED && visible) WeatherOverlay(condition, night)
    }
}

@Composable
private fun WeatherOverlay(condition: String, night: Boolean) {
    val context = LocalContext.current
    val lowMemory = remember(context) {
        context.getSystemService(ActivityManager::class.java).isLowRamDevice
    }
    val cloud = if (condition == "Bewölkt") ImageBitmap.imageResource(R.drawable.cloud_wisps) else null
    // Read animation state only during drawing: the bitmap and cards do not recompose.
    val phase = remember(condition) { mutableFloatStateOf(0f) }
    LaunchedEffect(condition) {
        var last = SystemClock.uptimeMillis()
        var elapsed = 0f
        while (isActive) {
            val now = SystemClock.uptimeMillis()
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale > 0f) {
                elapsed += (now - last) / scale
                phase.floatValue = (elapsed % 60000f) / 60000f
            }
            last = now
            delay(if (scale > 0f) 34 else 250) // At most 30 fps; respect disabled system animation.
        }
    }
    val flash = remember(condition) { mutableFloatStateOf(0f) }
    LaunchedEffect(condition) {
        if (condition == "Gewitter") while (true) {
            delay(Random.nextLong(6000, 14000))
            if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) continue
            flash.floatValue = .25f
            delay(160)
            flash.floatValue = 0f
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (size.width < 1 || size.height < 1) return@Canvas
        val seconds = phase.value * 60f
        val w = size.width
        val h = size.height
        val stroke = (w / 360f).coerceAtLeast(1f)
        // Reflection highlights remain below the actual pond edge.
        repeat(7) { i ->
            val dx = sin(seconds * .4f + i) * w * .009f
            val y = h * (.72f + i * .035f)
            val color = if (condition == "Nacht") Color(0xFFFFCA7C) else Color.White
            val ripple = Path().apply {
                moveTo(w * .25f + dx, y)
                repeat(16) { j ->
                    lineTo(w * (.25f + (j + 1) * .03f) + dx,
                        y + sin(j * .7f + seconds * .9f + i) * h * .0009f)
                }
            }
            drawPath(ripple, color.copy(alpha = .025f +
                .025f * (1f + sin(seconds * .7f + i))), style = Stroke(stroke))
        }
        when (condition) {
            "Sonnig" -> {
                // Moving translucent light on the water/garden; no bitmap movement.
                val x=w*(.35f+.12f*sin(seconds*.28f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE4A0).copy(alpha=.14f),Color.Transparent),
                    center=Offset(x,h*.72f),radius=w*.36f),radius=w*.36f,center=Offset(x,h*.72f))
                repeat(5) { i ->
                    val y=h*(.74f+i*.036f)
                    drawLine(Color(0xFFFFE9B6).copy(alpha=.15f+.1f*sin(seconds*1.2f+i)),
                        Offset(x-w*.1f,y),Offset(x+w*.08f,y),stroke)
                }
            }
            "Bewölkt" -> clipRect(bottom = h * .31f) {
                if (cloud != null) repeat(2) { layer ->
                    val width=(w*(1.1f+layer*.3f)).toInt()
                    val travel=(1f+sin(seconds*(2f*Math.PI.toFloat()/60f)+layer*1.4f))/2f
                    drawImage(cloud,dstOffset=IntOffset((w*(-.6f+travel*.85f)).toInt(),(h*(.01f+layer*.055f)).toInt()),
                        dstSize=IntSize(width,(width.toFloat()*cloud.height/cloud.width).toInt()),
                        alpha=(.34f+layer*.08f)*(if(night) .5f else 1f))
                }
            }
            "Regen", "Gewitter" -> {
                repeat(if (lowMemory) 32 else if (condition == "Gewitter") 72 else 48) { i ->
                    val depth = (i % 5) / 4f
                    val x = (((i * .618034f) % 1f) * w +
                        sin(seconds * .22f + i) * w * .008f)
                    val y = ((seconds * (36 + i % 20) / 60f + i * .137f) % 1f) * h
                    val length = h * (.008f + depth * .02f)
                    drawLine(Color(0xFFCAE5F5).copy(alpha = .16f + depth * .22f), Offset(x, y),
                        Offset(x - length * .22f, y + length), stroke * (.45f + depth * .55f),
                        cap = StrokeCap.Round)
                }
                repeat(9) { i ->
                    val p = (seconds * .7f + i * .113f) % 1f
                    drawOval(Color.White.copy(alpha = (1f - p) * .14f),
                        Offset(w * (.23f + (i % 4) * .16f), h * (.73f + (i % 3) * .075f)),
                        Size((3f + p * 10f) * stroke, (1f + p * 3f) * stroke), style = Stroke(stroke))
                }
                if (flash.floatValue > 0f) {
                    drawRect(Color.White.copy(alpha = flash.floatValue))
                    val bolt = Path().apply {
                        moveTo(w * .76f, h * .03f)
                        lineTo(w * .73f, h * .065f)
                        lineTo(w * .745f, h * .083f)
                        lineTo(w * .705f, h * .12f)
                        lineTo(w * .72f, h * .145f)
                        lineTo(w * .68f, h * .18f)
                        lineTo(w * .665f, h * .23f)
                        moveTo(w * .705f, h * .12f)
                        lineTo(w * .66f, h * .14f)
                        lineTo(w * .64f, h * .18f)
                    }
                    drawPath(bolt, Color.White.copy(alpha = .85f), style = Stroke(stroke * 2))
                }
            }
            "Schnee" -> repeat(if (lowMemory) 28 else 48) { i ->
                val speed = (2 + i % 3) / 60f
                val x = ((i * .618034f) % 1f) * w + sin(seconds * .5f + i) * w * .018f +
                    sin(seconds * .2f + i * .7f) * w * .007f
                val y = ((seconds * speed + i * .137f) % 1f) * h
                drawCircle(Color.White.copy(alpha = .36f + (i % 4) * .11f),
                    (.65f + i % 3 * .5f) * stroke, Offset(x, y))
            }
            "Nacht" -> Unit
        }
        if(night && condition in listOf("Nacht","Sonnig")) {
            clipRect(left=w*.29f,right=w*.96f,bottom=h*.23f) {
                repeat(if(lowMemory) 18 else 32) { i ->
                    val x=w*(.3f+((i*.618034f)%1f)*.65f)
                    val y=h*(.015f+((i*.381966f)%1f)*.18f)
                    val brightness=.28f+.55f*((1f+sin(seconds*(.6f+i%4*.17f)+i))/2f)
                    drawCircle(Color(0xFFE8F1FF).copy(alpha=brightness),stroke*(.45f+i%3*.25f),Offset(x,y))
                }
                repeat(3) { i ->
                    val start=4f+i*18f
                    if(seconds in start..start+1.8f) {
                        val p=(seconds-start)/1.8f
                        val x=w*(.35f+p*.4f)
                        val y=h*(.03f+i*.025f+p*.075f)
                        drawLine(Brush.linearGradient(listOf(Color.Transparent,Color.White.copy(alpha=sin(p*Math.PI).toFloat()*.8f)),
                            Offset(x-w*.1f,y-h*.018f),Offset(x,y)),Offset(x-w*.1f,y-h*.018f),Offset(x,y),stroke)
                    }
                }
            }
        }
    }
}
