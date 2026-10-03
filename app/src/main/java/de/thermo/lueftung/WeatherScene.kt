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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import java.time.LocalTime
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
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
fun AnimatedHouseScene(condition: String, modifier: Modifier = Modifier, animationsEnabled: Boolean = true, hourOverride: Int? = null, effects: WeatherEffects = WeatherEffects.demo(condition)) {
    var hour by remember { mutableIntStateOf(LocalTime.now(java.time.ZoneId.of("Europe/Berlin")).hour) }
    LaunchedEffect(Unit) { while (true) { delay(60000); hour=LocalTime.now(java.time.ZoneId.of("Europe/Berlin")).hour } }
    val lighting=weatherLighting(condition,hourOverride ?: hour)
    val night=lighting==WeatherLighting.NIGHT
    val dusk=lighting==WeatherLighting.DUSK
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var visible by remember { mutableStateOf(true) }
    val sceneBitmap = ImageBitmap.imageResource(weatherImage(if(night && condition=="Sonnig") "Nacht" else condition))
    val view = LocalView.current
    BoxWithConstraints(modifier.clipToBounds().onGloballyPositioned {
        val bounds = it.boundsInWindow()
        visible = bounds.width > 0 && bounds.height > 0 && bounds.bottom > 0 &&
            bounds.top < view.height && bounds.right > 0 && bounds.left < view.width
    }) {
        val contentWidth = minOf(maxWidth, maxHeight * .6f)
        Box(Modifier.width(contentWidth).height(contentWidth / .6f).align(Alignment.Center).clipToBounds()) {
        Image(
            sceneBitmap,
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
        if (animationsEnabled && lifecycle == Lifecycle.State.RESUMED && visible) WeatherOverlay(condition, night, dusk, effects, sceneBitmap)
        }
    }
}

@Composable
private fun WeatherOverlay(condition: String, night: Boolean, dusk: Boolean, effects: WeatherEffects, sceneBitmap: ImageBitmap) {
    val context = LocalContext.current
    val lowMemory = remember(context) {
        context.getSystemService(ActivityManager::class.java).isLowRamDevice
    }
    val cloudTexture = ImageBitmap.imageResource(R.drawable.cloud_wisps)
    val layerPaint = remember { Paint() }
    val smallClouds = remember {
        val random = Random(1123)
        List(8) { floatArrayOf(random.nextFloat(), .018f+random.nextFloat()*.115f,
            .16f+random.nextFloat()*.15f, .006f+random.nextFloat()*.006f,
            .65f+random.nextFloat()*.3f) }
    }
    // Seeded independent coordinates avoid lattice-like rows and stay stable across frames.
    val stars = remember { val random = Random(74597); List(44) {
        floatArrayOf(.29f + random.nextFloat() * .67f, .012f + random.nextFloat() * .19f,
            random.nextFloat() * 6.28f, .5f + random.nextFloat(), .55f + random.nextFloat() * .65f)
    } }
    val canopy = remember { listOf(.06f to .045f, .19f to .03f, .1f to .12f,
        .2f to .18f, .07f to .27f, .06f to .38f, .045f to .47f) }
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
                phase.floatValue = elapsed / 1000f
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
        val seconds = phase.value
        val w = size.width
        val h = size.height
        val stroke = (w / 360f).coerceAtLeast(1f)
        // Move the photographed water/reflections themselves, within the pond only.
        // The exposed water just above the glass cards is included in the mask.
        val pond = Path().apply {
            moveTo(w*.23f,h*.675f)
            cubicTo(w*.36f,h*.65f,w*.52f,h*.662f,w*.68f,h*.687f)
            lineTo(w*.98f,h*.684f); lineTo(w,h*.75f); lineTo(w,h*.95f)
            cubicTo(w*.8f,h*.99f,w*.48f,h*.985f,w*.32f,h*.98f)
            cubicTo(w*.25f,h*.94f,w*.22f,h*.83f,w*.17f,h*.75f)
            close()
        }
        clipPath(pond) {
            val bands=if(lowMemory) 30 else 48
            repeat(bands) { i ->
                val top=.65f+i*.35f/bands
                val bottom=.65f+(i+1)*.35f/bands
                val depth=(top-.65f)/.35f
                val dx=w*(.003f+.003f*depth)*sin(seconds*1.15f+i*.42f)
                val dy=h*.0012f*sin(seconds*.8f+i*.29f)
                clipRect(top=h*top,bottom=h*bottom) {
                    translate(dx,dy) { drawImage(sceneBitmap,dstSize=IntSize(w.toInt(),h.toInt())) }
                }
            }
            if(night && condition!="Nacht" && condition!="Sonnig") drawRect(Color(0xFF03152E).copy(alpha=.48f))
            else if(dusk && condition!="Nacht") drawRect(Color(0xFFEFAC75).copy(alpha=.12f))
            // Short broken glints follow ripples, including the unobscured upper pond.
            repeat(if(lowMemory) 20 else 36) { i ->
                val y=h*(.67f+((i*.173f)%1f)*.30f)
                val x=w*(.25f+((i*.618034f)%1f)*.74f)+w*.015f*sin(seconds*.7f+i)
                val shimmer=(1f+sin(seconds*1.6f+i*1.91f))/2f
                val color=if(night) Color(0xFFFFC56C) else Color(0xFFFFF0BE)
                drawLine(color.copy(alpha=(if(night) .28f else .32f)*shimmer),
                    Offset(x,y),Offset(x+w*(.018f+.018f*shimmer),y+h*.001f*sin(seconds+i)),
                    stroke*(.65f+shimmer),cap=StrokeCap.Round)
            }
            if(night) repeat(3) { lamp ->
                val x=w*(.3f+lamp*.28f)
                repeat(10) { i ->
                    val shimmer=(1f+sin(seconds*1.4f+i*.9f+lamp))/2f
                    val y=h*(.675f+i*.028f)
                    val shift=w*.012f*sin(seconds*.9f+i+lamp)
                    drawLine(Color(0xFFFFBB55).copy(alpha=.08f+.18f*shimmer),
                        Offset(x+shift-w*.018f,y),Offset(x+shift+w*.026f,y),stroke)
                }
            }
        }
        if(condition == "Sonnig" || condition == "Nacht") {
            canopy.take(if(lowMemory) 4 else 7).forEachIndexed { i, point ->
                val centre=Offset(w*point.first,h*point.second)
                val radius=w*.065f
                val bounds=Rect(centre.x-radius,centre.y-radius,centre.x+radius,centre.y+radius)
                // Feather every patch so moving foliage has no oval cutout edge.
                clipRect(bounds.left,bounds.top,bounds.right,bounds.bottom) {
                    drawContext.canvas.saveLayer(bounds,layerPaint)
                    translate(w*.007f*sin(seconds*.95f+i),h*.002f*sin(seconds*.7f+i)) {
                        drawImage(sceneBitmap,dstSize=IntSize(w.toInt(),h.toInt()))
                    }
                    if(dusk && condition!="Nacht") drawRect(Color(0xFFEFAC75).copy(alpha=.12f))
                    drawRect(Brush.radialGradient(0f to Color.White,.5f to Color.White,
                        1f to Color.Transparent,center=centre,radius=radius),
                        topLeft=bounds.topLeft,size=bounds.size,blendMode=BlendMode.DstIn)
                    drawContext.canvas.restore()
                }
                if(!night) {
                    val glint=Offset(centre.x+w*.02f*sin(seconds*.6f+i),centre.y+h*.006f*sin(seconds*.8f+i))
                    val glow=.08f+.17f*(1f+sin(seconds*1.1f+i))/2f
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE58B).copy(alpha=glow),Color.Transparent),glint,w*.055f),w*.055f,glint)
                }
            }
        }
        // Independent small cloud sprites move in one direction. Never redraw the
        // photographed sky: blending a shifted copy caused ghosted original clouds.
        if(effects.clouds>0f) {
            val cover=effects.clouds.coerceIn(0f,1f)
            val skyBounds=Rect(0f,0f,w,h*.25f)
            clipRect(0f,0f,w,h*.25f) {
                drawContext.canvas.saveLayer(skyBounds,layerPaint)
                val count=(3+(cover*5).toInt()).coerceAtMost(if(lowMemory) 4 else 8)
                repeat(count) { i ->
                    val cloud=smallClouds[i]
                    val span=1f+cloud[2]
                    // Wrap only while the entire sprite is outside the image.
                    val progress=(cloud[0]+seconds*cloud[3]/span)%1f
                    val x=w*(-cloud[2]+progress*span)
                    val width=(w*cloud[2]).toInt().coerceAtLeast(1)
                    val opacity=(if(night) .10f*cover else .50f+.30f*cover)*cloud[4]
                    translate(x,h*cloud[1]) {
                        drawImage(cloudTexture,
                            dstSize=IntSize(width,(width.toFloat()*cloudTexture.height/cloudTexture.width).toInt().coerceAtLeast(1)),
                            alpha=opacity)
                    }
                }
                // Fade around the foreground foliage and before the forest skyline.
                drawRect(Brush.verticalGradient(0f to Color.White,.65f to Color.White,
                    1f to Color.Transparent,startY=0f,endY=h*.25f),
                    size=skyBounds.size,blendMode=BlendMode.DstIn)
                drawRect(Brush.horizontalGradient(0f to Color.Transparent,.23f to Color.Transparent,
                    .36f to Color.White,1f to Color.White,startX=0f,endX=w),
                    size=skyBounds.size,blendMode=BlendMode.DstIn)
                drawContext.canvas.restore()
            }
        }
        if(!night && condition in listOf("Sonnig","Bewölkt")) {
            val centre=Offset(w*.67f,h*.27f)
            val strength=(.045f+.09f*(1f+sin(seconds*.24f))/2f)*(1f-effects.clouds*.65f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9AF).copy(alpha=strength),Color.Transparent),centre,w*.5f),w*.5f,centre)
        }
        if(night) {
            listOf(.86f to .61f,.695f to .536f,.098f to .53f,.433f to .534f).forEachIndexed { i, lamp ->
                val centre=Offset(w*lamp.first,h*lamp.second)
                val flicker=.07f+.018f*sin(seconds*2.1f+i)+.012f*sin(seconds*3.7f+i*2)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFBC54).copy(alpha=flicker),Color.Transparent),centre,w*.045f),w*.045f,centre)
            }
        }
        when (condition) {
            "Sonnig" -> Unit // Photographed reflections and glints move in the pond mask.
            "Bewölkt" -> Unit // Soft clouds and filtered sunlight are drawn below.
            "Regen", "Gewitter" -> {
                repeat(effects.rainCount(lowMemory)) { i ->
                    val depth = (i % 5) / 4f
                    val x = (((i * .618034f) % 1f) * w +
                        sin(seconds * .22f + i) * w * .008f)
                    val y = ((seconds * (36 + i % 20) / 60f + i * .137f) % 1f) * h
                    val length = h * (.008f + depth * .02f)
                    drawLine(Color(0xFFCAE5F5).copy(alpha = .16f + depth * .22f), Offset(x, y),
                        Offset(x - length * .22f, y + length), stroke * (.45f + depth * .55f),
                        cap = StrokeCap.Round)
                }
                repeat((effects.rainCount(lowMemory) / 6).coerceAtMost(12)) { i ->
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
            "Schnee" -> repeat(effects.snowCount(lowMemory)) { i ->
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
                stars.take(if(lowMemory) 24 else 44).forEach { star ->
                    val x=w*star[0]; val y=h*star[1]
                    val brightness=.18f+.8f*((1f+sin(seconds*star[3]+star[2]))/2f)
                    val radius=stroke*star[4]
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFD1E7FF).copy(alpha=brightness*.3f),Color.Transparent),
                        Offset(x,y),radius*4),radius*4,Offset(x,y))
                    drawCircle(Color(0xFFE8F1FF).copy(alpha=brightness),radius,Offset(x,y))
                    if(brightness>.78f) {
                        drawLine(Color.White.copy(alpha=(brightness-.78f)*3),Offset(x-radius*3,y),Offset(x+radius*3,y),stroke*.5f)
                        drawLine(Color.White.copy(alpha=(brightness-.78f)*3),Offset(x,y-radius*3),Offset(x,y+radius*3),stroke*.5f)
                    }
                }
                repeat(3) { i ->
                    val start=4f+i*18f
                    val cycleSeconds=seconds % 60f
                    if(cycleSeconds in start..start+1.8f) {
                        val p=(cycleSeconds-start)/1.8f
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
