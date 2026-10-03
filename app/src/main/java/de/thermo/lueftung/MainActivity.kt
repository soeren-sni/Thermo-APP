
package de.thermo.lueftung

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.sin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clipToBounds

private val Navy = Color(0xFF062F45)
private val Navy2 = Color(0xFF073D58)
private val Cyan = Color(0xFF2CC7E8)
private val Sky = Color(0xFF4EA9F4)
private val Glass = Color(0xFF0A4561).copy(alpha = 0.62f)
private val GlassLight = Color.White.copy(alpha = 0.16f)
private val GlassLine = Color.White.copy(alpha = 0.28f)
private val TextSoft = Color.White.copy(alpha = 0.72f)
private val Good = Color(0xFF61D39A)

data class Room(
    val id: String,
    val name: String,
    val floor: String,
    val temp: Float,
    val humidity: Int,
    val dewPoint: Float,
    val absHumidity: Float,
    val image: Int,
    val measuredAtMillis: Long? = null,
    val isDemo: Boolean = true
)

private val rooms = listOf(
    Room("child1","Kinderzimmer 1","Dachgeschoss",20.4f,48,9.1f,8.7f,R.drawable.room_child1_clean),
    Room("child2","Bühne (Kinderzimmer 2)","Dachgeschoss",0f,0,0f,0f,R.drawable.room_child2_clean),
    Room("office","Büro","Erdgeschoss",21.0f,50,10.2f,9.1f,R.drawable.room_office_clean),
    Room("bedroom","Schlafzimmer","Erdgeschoss",20.3f,52,10.4f,9.9f,R.drawable.room_bedroom_clean),
    Room("living","Wohnzimmer","Erdgeschoss",21.5f,49,10.4f,9.3f,R.drawable.room_living_clean),
    Room("dining","Esszimmer","Erdgeschoss",21.4f,47,9.9f,8.9f,R.drawable.room_dining_clean),
    Room("kitchen","Küche","Erdgeschoss",21.2f,49,10.4f,9.3f,R.drawable.room_kitchen_clean),
    Room("pantry","Speis","Erdgeschoss",19.8f,52,9.7f,9.0f,R.drawable.room_pantry_clean),
    Room("bath","Bad","Erdgeschoss",22.1f,61,14.2f,13.2f,R.drawable.room_bath_default),
    Room("wc","WC","Erdgeschoss",21.0f,55,11.5f,10.4f,R.drawable.room_wc_clean),
    Room("workshop","Werkstatt","Keller",16.8f,55,7.8f,7.0f,R.drawable.room_workshop_clean),
    Room("fitness","Hobby / Fitness","Keller",17.0f,51,7.0f,6.6f,R.drawable.room_fitness_clean),
    Room("laundry","Wäschekeller","Keller",16.5f,58,8.0f,7.3f,R.drawable.room_laundry_clean)
).map { room ->
    if (room.humidity==0) room else room.copy(
        dewPoint=(kotlin.math.round(ClimateMath.dewPoint(room.temp.toDouble(),room.humidity.toDouble())!! * 10) / 10).toFloat(),
        absHumidity=(kotlin.math.round(ClimateMath.absoluteHumidity(room.temp.toDouble(),room.humidity.toDouble()) * 10) / 10).toFloat()
    )
}

fun thermoRooms(): List<Room> = rooms

fun dehumidifierLocationName(id: String?): String = rooms.firstOrNull { it.id==id }?.name ?: "Nicht zugeordnet"

class ThermoViewModel(application: Application) : AndroidViewModel(application) {
    val climate = ThermoRuntime.climate
    val wallTemperatures = mutableStateMapOf<String, WallTemperatureReading>()
    var page by mutableIntStateOf(0)
    var selectedRoom by mutableStateOf<Room?>(null)
    var historyRoomId by mutableStateOf("child1")
    var timerSelectedRoomId by mutableStateOf<String?>(null)
    var weatherTest by mutableStateOf<String?>(null)
    var weatherData by mutableStateOf<WeatherData?>(null)
    val weather: String get() = weatherTest ?: weatherData?.condition ?: "Sonnig"
    val weatherEffects get() = if(weatherTest != null) WeatherEffects.demo(weather) else weatherData?.effects ?: WeatherEffects.demo(weather)
    val weatherSource get() = when {
        weatherTest != null -> "Demo · Wettertest"
        weatherData != null -> "Open-Meteo · Modellwetter"
        else -> "Demo · Wetterdaten fehlen"
    }
    var sceneHourOverride by mutableStateOf<Int?>(null)
    var weatherAnimations by mutableStateOf(true)
    var dehumidifierPlacement by mutableStateOf(ManualDehumidifierPlacement())
        private set
    val dehumidifierRoomId get() = dehumidifierPlacement.roomId
    val dehumidifierOperation get() = dehumidifierPlacement.operation
    var dryingEvaluatedAtMillis by mutableLongStateOf(System.currentTimeMillis())
    private var imageRevision by mutableIntStateOf(0)
    var timerSnapshot by mutableStateOf<Map<String,ActiveVentilationTimer>>(emptyMap())
        private set
    var timerNow by mutableLongStateOf(System.currentTimeMillis())
        private set
    val timerRoom get() = timerSnapshot.keys.firstOrNull()
    val timerSeconds get() = timerRoom?.let { timerSnapshot[it]?.remaining(timerNow,SystemClock.elapsedRealtime(),ThermoRuntime.bootCount()) } ?: 0
    val timerRunning get() = timerSnapshot.isNotEmpty()
    init {
        viewModelScope.launch { ThermoRuntime.timers.collect { timerSnapshot=it } }
        viewModelScope.launch { while(true) { delay(1000); if(timerSnapshot.isNotEmpty()) timerNow=System.currentTimeMillis() } }
    }

    fun loadDehumidifier(context: Context) {
        val prefs=context.getSharedPreferences("devices", Context.MODE_PRIVATE)
        val id=prefs.getString("dehumidifier_room", null)?.takeIf { value -> rooms.any { it.id==value } }
        val operation=if(id==null) ManualDehumidifierOperation.UNKNOWN else
            ManualDehumidifierOperation.entries.firstOrNull { it.name==prefs.getString("dehumidifier_operation",null) }
                ?: ManualDehumidifierOperation.UNKNOWN
        dehumidifierPlacement=ManualDehumidifierPlacement(id,operation)
    }

    private fun saveDehumidifier(context: Context) {
        context.getSharedPreferences("devices", Context.MODE_PRIVATE).edit()
            .putString("dehumidifier_room", dehumidifierRoomId)
            .putString("dehumidifier_operation", dehumidifierOperation.name).apply()
    }

    fun moveDehumidifier(context: Context, roomId: String?) {
        require(roomId == null || rooms.any { it.id==roomId })
        dehumidifierPlacement=dehumidifierPlacement.movedTo(roomId)
        saveDehumidifier(context)
    }

    fun reportDehumidifier(context: Context, operation: ManualDehumidifierOperation) {
        dehumidifierPlacement=dehumidifierPlacement.reported(operation)
        saveDehumidifier(context)
    }

    fun openRoom(room: Room) { selectedRoom = room; page = 2 }
    fun startTimer(room: Room, minutes: Int = 5) {
        if(room.isDemo && room.humidity>0 && climate.readings.value[room.id]==null) {
            climate.accept(room.id,RoomClimateReading(room.temp,room.humidity,room.dewPoint,room.absHumidity,System.currentTimeMillis(),isDemo=true))
        }
        ThermoRuntime.startTimer(room.id,minutes,"Manuell")
    }
    fun stopTimer(roomId:String?=timerRoom) { roomId?.let { ThermoRuntime.stopTimer(it) } }

    fun savedUri(context: Context, roomId: String): Uri? {
        imageRevision // Observe photo changes without reloading unrelated sensor/timer state.
        return context.getSharedPreferences("room_images", Context.MODE_PRIVATE)
            .getString(roomId, null)?.let(Uri::parse)
    }

    fun saveUri(context: Context, roomId: String, uri: Uri) {
        context.getSharedPreferences("room_images", Context.MODE_PRIVATE)
            .edit().putString(roomId, uri.toString()).apply()
        imageRevision++
    }

    fun resetUri(context: Context, roomId: String) {
        context.getSharedPreferences("room_images", Context.MODE_PRIVATE)
            .edit().remove(roomId).apply()
        imageRevision++
    }
}

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val vm=ViewModelProvider(this)[ThermoViewModel::class.java]
        if(intent.getBooleanExtra("show_timer",false)) vm.apply { selectedRoom=null;page=6;timerSelectedRoomId=intent.getStringExtra("timer_room") }
        intent.getStringExtra("room_advice")?.let { id -> rooms.firstOrNull { it.id==id }?.let(vm::openRoom) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThermoRuntime.resumeTimers()
        enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(6,47,69))
        )
        if(intent.getBooleanExtra("show_timer",false)) ViewModelProvider(this)[ThermoViewModel::class.java].apply { page=6;timerSelectedRoomId=intent.getStringExtra("timer_room") }
        intent.getStringExtra("room_advice")?.let { id -> rooms.firstOrNull { it.id==id }?.let { ViewModelProvider(this)[ThermoViewModel::class.java].openRoom(it) } }
        setContent { ThermoApp() }
    }
}

@Composable
fun ThermoApp(vm: ThermoViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.weatherAnimations = context.getSharedPreferences("display", Context.MODE_PRIVATE)
            .getBoolean("weather_animations", true)
        vm.loadDehumidifier(context)
    }
    LaunchedEffect(vm) {
        while (true) {
            try { vm.weatherData = WeatherRepository.fetch();ThermoRuntime.weather=vm.weatherData }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { if(vm.weatherData?.fresh() != true) vm.weatherData = null;ThermoRuntime.weather=vm.weatherData }
            delay(900000)
        }
    }
    LaunchedEffect(vm) {
        while (true) { delay(30000); vm.dryingEvaluatedAtMillis=System.currentTimeMillis();ThermoRuntime.walls.putAll(vm.wallTemperatures) }
    }

    MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,secondary=Sky,background=Navy,surface=Navy2,
        onSurface=Color.White,onBackground=Color.White,onPrimary=Navy,onSecondary=Navy,
        secondaryContainer=Cyan.copy(alpha=.20f),onSecondaryContainer=Color.White,
        primaryContainer=Navy2,onPrimaryContainer=Color.White,outline=GlassLine)) {
        Surface(Modifier.fillMaxSize(), color = Navy) {
            when {
                vm.selectedRoom != null -> RoomDetailScreen(vm, vm.selectedRoom!!)
                else -> {
                    when (vm.page) {
                        0 -> HomeScreen(vm)
                        1 -> RoomsScreen(vm)
                        3 -> HistoryScreen(vm)
                        4 -> DevicesScreen(vm)
                        5 -> SettingsScreen(vm)
                        6 -> TimerScreen(vm)
                        else -> HomeScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    alpha: Float = .62f,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .shadow(8.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0A4561).copy(alpha = alpha))
            .border(1.dp, GlassLine, RoundedCornerShape(22.dp))
            .padding(padding),
        content = content
    )
}

@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    active: Int,
    onBack: (() -> Unit)? = null,
    vm: ThermoViewModel,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(Navy).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                subtitle?.let { Text(it, color = TextSoft, fontSize = 12.sp) }
            }
            Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f).then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier), content = content)
        BottomNav(active, vm)
    }
}

@Composable
fun BottomNav(active: Int, vm: ThermoViewModel) {
    val items = listOf(
        Triple(Icons.Filled.Home,"Start",0),
        Triple(Icons.Filled.Apartment,"Räume",1),
        Triple(Icons.Filled.ShowChart,"Historie",3),
        Triple(Icons.Filled.Devices,"Geräte",4),
        Triple(Icons.Filled.Settings,"Einstellungen",5)
    )
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .background(Navy2.copy(alpha=.94f))
            .border(1.dp, Color.White.copy(alpha=.08f)),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { (icon,label,target) ->
            val selected = active == target
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable {
                    vm.selectedRoom = null
                    vm.page = target
                }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Cyan.copy(alpha=.20f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (selected) Cyan else Color.White.copy(alpha=.72f))
                }
                Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = if (selected) Cyan else Color.White.copy(alpha=.72f))
            }
        }
    }
}

@Composable
fun HomeScreen(vm: ThermoViewModel) {
    Column(Modifier.fillMaxSize().background(Navy).safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1080f / 1800f)
                .clip(RoundedCornerShape(bottomStart=28.dp,bottomEnd=28.dp))
        ) {
            AnimatedHouseScene(vm.weather, Modifier.fillMaxSize(), vm.weatherAnimations, vm.sceneHourOverride, vm.weatherEffects)
            Row(
                Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.LocationOn, null, tint=Color.White, modifier=Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mein Zuhause", color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)
                    Text(WeatherRepository.LOCATION, color=TextSoft, fontSize=12.sp)
                }
                IconButton(onClick={ vm.page=5 }) { Icon(Icons.Filled.Settings,null,tint=Color.White) }
            }
            WeatherGlassCard(vm, Modifier.align(Alignment.TopEnd).padding(top=60.dp,end=12.dp))
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                SmallGlass("Garten · Demo","17,9 °C","72 % RH",Good,Modifier.weight(1f))
                SmallGlass("Außenluft · Demo","12,0 °C","86 % RH",Cyan,Modifier.weight(1f))
                SmallGlass("Luftqualität · Demo","AQI 28","Gut",Good,Modifier.weight(1f))
            }
            GlassCard(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=116.dp).clickable { vm.page=1 },
                alpha=.70f, padding=PaddingValues(horizontal=16.dp,vertical=13.dp)
            ) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Filled.Eco,null,tint=Good)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Lüften nach Raum prüfen",color=Color.White,fontWeight=FontWeight.Bold)
                        Text("Außenluft · Demo · 12,0 °C · 86 % RH",color=TextSoft,fontSize=10.sp)
                    }
                    Icon(Icons.Filled.ChevronRight,null,tint=Color.White)
                }
            }
        }
        GlassCard(
            Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=4.dp),
            alpha=.74f, padding=PaddingValues(horizontal=14.dp, vertical=8.dp)
        ) {
            Text("Wettervorhersage · Demo",color=Color.White,fontSize=16.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                listOf(Triple("11:00","Sonnig","18°"),Triple("12:00","Sonnig","20°"),Triple("13:00","Bewölkt","21°"),Triple("14:00","Regen","19°"),Triple("15:00","Regen","17°")).forEach {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(it.first,color=TextSoft,fontSize=9.sp,lineHeight=12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            WeatherConditionIcon(it.second, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(it.third,color=Color.White,fontSize=12.sp,lineHeight=16.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text("48-Stunden-Vorhersage  ›",color=Cyan,fontSize=11.sp,lineHeight=14.sp)
        }
        }
        BottomNav(0,vm)
    }
}

@Composable
fun WeatherGlassCard(vm: ThermoViewModel, modifier: Modifier) {
    val condition = vm.weather
    val data = vm.weatherData.takeIf { vm.weatherTest == null }
    fun value(number: Float) = String.format(java.util.Locale.GERMAN, "%.1f", number)
    GlassCard(modifier.width(160.dp),alpha=.64f,padding=PaddingValues(14.dp)) {
        Row(verticalAlignment=Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("AUSSENWETTER",color=TextSoft,fontSize=9.sp)
                Text("${value(data?.temperature ?: 12f)}°C",color=Color.White,fontSize=30.sp)
                Text(condition,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold)
            }
            WeatherConditionIcon(condition, Modifier.size(32.dp))
        }
        Text(vm.weatherSource,color=TextSoft,fontSize=9.sp)
        Spacer(Modifier.height(8.dp))
        Text("Außenfeuchte    ${data?.humidity ?: 86} %",color=Color.White,fontSize=10.sp)
        Text("Taupunkt        ${value(ClimateMath.dewPoint((data?.temperature ?: 12f).toDouble(), (data?.humidity ?: 86).toDouble())?.toFloat() ?: 9.7f)} °C",color=TextSoft,fontSize=10.sp)
        Text("Abs. Feuchte    ${value(ClimateMath.absoluteHumidity((data?.temperature ?: 12f).toDouble(), (data?.humidity ?: 86).toDouble()).toFloat())} g/m³",color=TextSoft,fontSize=10.sp)
    }
}

@Composable
fun SmallGlass(title:String,value:String,sub:String,accent:Color,modifier:Modifier) {
    GlassCard(modifier,alpha=.60f,padding=PaddingValues(12.dp)) {
        Text(title,color=TextSoft,fontSize=10.sp)
        Text(value,color=accent,fontSize=16.sp,fontWeight=FontWeight.Bold)
        Text(sub,color=Color.White.copy(alpha=.75f),fontSize=10.sp)
    }
}


fun isAtticVentilationRoom(room: Room): Boolean = room.id=="child1" || room.id=="child2"
fun atticVentilationHint(room: Room): String =
    if(isAtticVentilationRoom(room)) "Lüftungszone Dach: beide Fenster öffnen" else ""

@Composable
fun FloorRegister(selected:String,onSelect:(String)->Unit) {
    val tabs=listOf("Gesamt","Dachgeschoss","Erdgeschoss","Keller")
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        tabs.forEach { key ->
            val active=selected==key
            Column(
                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(18.dp))
                    .background(if(active) Cyan.copy(alpha=.22f) else Color.White.copy(alpha=.055f))
                    .border(1.dp,if(active) Cyan.copy(alpha=.72f) else Color.White.copy(alpha=.14f),RoundedCornerShape(18.dp))
                    .clickable { onSelect(key) },
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center
            ) {
                val tint=if(active) Cyan else Color.White
                when(key) {
                    "Gesamt" -> Icon(Icons.Filled.Home,null,tint=tint,modifier=Modifier.size(26.dp))
                    "Dachgeschoss" -> Canvas(Modifier.size(28.dp)) {
                        val sw=2.5.dp.toPx()
                        drawLine(tint,Offset(2f,size.height*.72f),Offset(size.width*.5f,size.height*.25f),sw)
                        drawLine(tint,Offset(size.width*.5f,size.height*.25f),Offset(size.width-2f,size.height*.72f),sw)
                    }
                    "Erdgeschoss" -> Icon(Icons.Filled.Apartment,null,tint=tint,modifier=Modifier.size(26.dp))
                    else -> Canvas(Modifier.size(28.dp)) {
                        val sw=2.7.dp.toPx()
                        val y0=size.height*.22f
                        drawLine(tint,Offset(size.width*.12f,y0),Offset(size.width*.48f,y0),sw)
                        drawLine(tint,Offset(size.width*.48f,y0),Offset(size.width*.48f,size.height*.48f),sw)
                        drawLine(tint,Offset(size.width*.48f,size.height*.48f),Offset(size.width*.72f,size.height*.48f),sw)
                        drawLine(tint,Offset(size.width*.72f,size.height*.48f),Offset(size.width*.72f,size.height*.74f),sw)
                        drawLine(tint,Offset(size.width*.72f,size.height*.74f),Offset(size.width*.94f,size.height*.74f),sw)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(when(key){"Dachgeschoss"->"Dach";"Erdgeschoss"->"EG";else->key},
                    color=tint,fontSize=10.sp,fontWeight=if(active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun RoomsScreen(vm: ThermoViewModel) {
    var selectedFloor by remember { mutableStateOf("Gesamt") }
    val visibleRooms=if(selectedFloor=="Gesamt") rooms else rooms.filter { it.floor==selectedFloor }
    AppScaffold("Räume","Hausübersicht · Raum anklicken",1,null,vm,scrollable=false) {
        LazyColumn(Modifier.fillMaxSize()) {
        item {
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal=12.dp).aspectRatio(1.48f)
                .clip(RoundedCornerShape(24.dp)).border(1.dp,GlassLine,RoundedCornerShape(24.dp))
        ) {
            val floorImage = when(selectedFloor) {
                "Dachgeschoss" -> R.drawable.floor_dachgeschoss_v3
                "Erdgeschoss" -> R.drawable.floor_erdgeschoss_v3
                "Keller" -> R.drawable.floor_keller_v3
                else -> R.drawable.house_scene
            }
            Image(
                painter=androidx.compose.ui.res.painterResource(floorImage),
                contentDescription=if(selectedFloor=="Gesamt") "Gesamtübersicht" else selectedFloor,
                contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize().background(Color.Black.copy(alpha=.12f))
            )
            Text(
                if(selectedFloor=="Gesamt") "Gesamtübersicht" else selectedFloor,
                color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp,
                modifier=Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .background(Color.Black.copy(alpha=.42f),RoundedCornerShape(12.dp))
                    .padding(horizontal=10.dp,vertical=6.dp)
            )
        }
        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Raumliste",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Text("13 Räume",color=TextSoft,fontSize=12.sp)
        }
        DehumidifierLegend()
        FloorRegister(selectedFloor) { selectedFloor=it }
        Row(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,top=10.dp,bottom=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text(if(selectedFloor=="Gesamt") "Alle Räume" else selectedFloor,color=Cyan,fontSize=16.sp,
                fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Text("${visibleRooms.size} Räume",color=TextSoft,fontSize=10.sp)
        }
        }
        items(visibleRooms,key={it.id}) { room ->
            RoomGlassRow(room,vm)
            Spacer(Modifier.height(10.dp))
        }
        item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun RoomGlassRow(baseRoom:Room,vm:ThermoViewModel) {
    val room=liveRoom(baseRoom,vm)
    val ctx=LocalContext.current
    val saved=vm.savedUri(ctx,room.id)
    GlassCard(
        Modifier.fillMaxWidth().padding(horizontal=12.dp).clickable { vm.openRoom(room) },
        alpha=.60f,padding=PaddingValues(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(108.dp)) {
            Box(Modifier.width(135.dp).fillMaxHeight()) {
                RoomPhoto(saved, room.image, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.20f)))
                Text(room.name,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(room.floor,color=TextSoft,fontSize=10.sp)
                if(room.temp>0f || room.measuredAtMillis!=null) {
                    Text("${room.temp} °C  ·  ${room.humidity} %",color=Color.White,fontSize=11.sp)
                    Text("TP ${room.dewPoint} °C",color=TextSoft,fontSize=10.sp)
                    Spacer(Modifier.height(6.dp))
                    if(room.isDemo) Text("Raumwerte · Demo",color=TextSoft,fontSize=10.sp)
                    else Text("Messung ${room.measuredAtMillis?.let(::measurementTime) ?: "—"}",color=TextSoft,fontSize=10.sp)
                } else {
                    Text("Noch nicht ausgebaut",color=TextSoft,fontSize=11.sp)
                }
            }
            Icon(Icons.Filled.ChevronRight,null,tint=Cyan,modifier=Modifier.align(Alignment.CenterVertically).padding(10.dp))
        }
        RoomDehumidifierStatus(room,vm,Modifier.padding(horizontal=12.dp,vertical=6.dp))
    }
}

@Composable
fun RoomDetailScreen(vm:ThermoViewModel,baseRoom:Room) {
    val room=liveRoom(baseRoom,vm)
    val ctx=LocalContext.current
    val saved=vm.savedUri(ctx,room.id)
    Column(Modifier.fillMaxSize().background(Navy).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().height(245.dp)) {
            RoomPhoto(saved, room.image, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.18f)))
            Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={vm.selectedRoom=null;vm.page=1}) { Icon(Icons.Filled.ArrowBack,null,tint=Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(room.name,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Text(room.floor,color=TextSoft,fontSize=11.sp)
                }
                Icon(Icons.Filled.Settings,null,tint=Color.White)
            }
            GlassCard(Modifier.align(Alignment.BottomStart).padding(12.dp),alpha=.60f,padding=PaddingValues(10.dp)) {
                Text("${room.temp} °C",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text("Temperatur",color=TextSoft,fontSize=9.sp)
            }
            GlassCard(Modifier.align(Alignment.BottomEnd).padding(12.dp),alpha=.60f,padding=PaddingValues(10.dp)) {
                Text("${room.humidity} %",color=Cyan,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text("Luftfeuchte",color=TextSoft,fontSize=9.sp)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            room.measuredAtMillis?.let {
                Text("Letzte Messung: ${measurementTime(it)}",color=TextSoft,fontSize=10.sp)
                Spacer(Modifier.height(8.dp))
            }
            GlassCard(Modifier.fillMaxWidth(),alpha=.72f) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle,null,tint=Good)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if(room.isDemo) "Raumklima · Demowerte" else "Raumklima · Sensormessung",color=Color.White,fontWeight=FontWeight.Bold)
                        Text("Lüftung und Wandklima unten prüfen",color=TextSoft,fontSize=10.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MetricGlass("Temperatur","${room.temp} °C",Icons.Filled.Thermostat,Modifier.weight(1f))
                MetricGlass("Feuchte","${room.humidity} %",Icons.Filled.WaterDrop,Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MetricGlass("Taupunkt","${room.dewPoint} °C",Icons.Filled.AcUnit,Modifier.weight(1f))
                MetricGlass("Abs. Feuchte","${room.absHumidity} g/m³",Icons.Filled.Cloud,Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            RoomDehumidifierCard(room,vm)
            Spacer(Modifier.height(14.dp))
            WallClimateCard(room,vm)
            Spacer(Modifier.height(14.dp))
            Text("Betriebsart · Vorschau",color=Color.White,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Auto","Manuell","Aus").forEachIndexed { i,s ->
                    OutlinedButton(
                        onClick={},shape=RoundedCornerShape(22.dp),
                        colors=ButtonDefaults.outlinedButtonColors(
                            containerColor=if(i==0) Cyan else Color.Transparent,
                            contentColor=if(i==0) Navy else Color.White
                        )
                    ){Text(s)}
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Fensterkontakt",color=Color.White,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            RoomContactCard(room)
            Spacer(Modifier.height(10.dp))
            RoomVentilationCard(room,vm)
            Spacer(Modifier.height(10.dp))
            RoomSensorTestCard(room)
            Spacer(Modifier.height(14.dp))
            Text("Heizung",color=Color.White,fontWeight=FontWeight.Bold)
            GlassCard(Modifier.fillMaxWidth(),alpha=.62f) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment,null,tint=Color(0xFFFF7C3A))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){Text(if(room.floor!="Keller" || room.id in setOf("fitness","laundry")) "IR-Heizung · Tuya" else "Heizung nicht zugeordnet",color=Color.White,fontWeight=FontWeight.Bold);Text("Gerätestatus noch nicht verbunden",color=TextSoft,fontSize=9.sp)}
                    Text("Offen",color=TextSoft)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Raumfunktionen",color=Color.White,fontWeight=FontWeight.Bold)
            listOf("Raum Einstellungen","Zeitpläne / Automatik","Verlauf","Geräte im Raum").forEach {
                GlassCard(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { if(it=="Verlauf") { vm.historyRoomId=room.id;vm.selectedRoom=null;vm.page=3 } },alpha=.50f,padding=PaddingValues(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Filled.ChevronRight,null,tint=Cyan);Spacer(Modifier.width(8.dp));Text(it,color=Color.White)}
                }
            }
        }
    }
}

@Composable
fun MetricGlass(label:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier) {
    GlassCard(modifier,alpha=.62f,padding=PaddingValues(13.dp)) {
        Icon(icon,null,tint=Cyan,modifier=Modifier.size(22.dp))
        Spacer(Modifier.height(7.dp))
        Text(label,color=TextSoft,fontSize=10.sp)
        Text(value,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable
fun TimerScreen(vm:ThermoViewModel) {
    val roomId=vm.timerSelectedRoomId ?: vm.timerRoom ?: "bath"
    var expanded by remember { mutableStateOf(false) }
    val room=liveRoom(rooms.first { it.id==roomId },vm)
    AppScaffold("Lüftung & Timer","Benachrichtigung · Fenster-Erinnerung",6,null,vm) {
        Column(Modifier.padding(12.dp)) {
            Box {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) { Text("Raum: ${room.name}",color=Color.White) }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    rooms.forEach { candidate -> DropdownMenuItem(text={Text(candidate.name)},onClick={vm.timerSelectedRoomId=candidate.id;expanded=false}) }
                }
            }
            vm.timerSnapshot.values.filter { it.roomId!=roomId }.forEach { timer -> TextButton(onClick={vm.timerSelectedRoomId=timer.roomId}) { Text("Aktiver Timer: ${dehumidifierLocationName(timer.roomId)}") } }
            RoomVentilationCard(room,vm)
            Spacer(Modifier.height(10.dp))
            RoomSensorTestCard(room)
        }
    }
}

@Composable
fun Pill(text:String,selected:Boolean,onClick:()->Unit) {
    OutlinedButton(onClick=onClick,shape=RoundedCornerShape(22.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected) Cyan else Color.Transparent,contentColor=if(selected) Navy else Color.White),border=ButtonDefaults.outlinedButtonBorder) { Text(text,fontSize=11.sp) }
}

@Composable
fun DevicesScreen(vm:ThermoViewModel) {
    val context=LocalContext.current
    AppScaffold("Geräte","Tuya · Midea · Sensoren · Solix",4,null,vm) {
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple(Icons.Filled.Thermostat,"Tuya Raumklima","Sensoren und Fensterkontakte · vorbereitet"),
            Triple(Icons.Filled.WaterDrop,"Midea DF-20DEN7-WF","NetHome Plus · noch nicht verbunden"),
            Triple(Icons.Filled.Sensors,"Außenklima","Temperatur · RH · Taupunkt · absolute Feuchte"),
            Triple(Icons.Filled.BatteryChargingFull,"Anker Smartplug / SOLIX 4 Pro","Stromverbrauch · noch nicht verbunden")
        ).forEach { (icon,title,sub) ->
            GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp),alpha=.62f) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(icon,null,tint=Cyan,modifier=Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(sub,color=TextSoft,fontSize=10.sp)}
                    Icon(Icons.Filled.ChevronRight,null,tint=Color.White.copy(alpha=.7f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasementDryingRecommendation(vm,rooms.filter { it.floor=="Keller" })
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp),alpha=.62f) {
            Text("Mobiler Entfeuchter · Standort",color=Color.White,fontWeight=FontWeight.Bold)
            Text("Nach jedem Umstellen den aktuellen Raum wählen. Der Standort wird manuell gespeichert.",color=TextSoft,fontSize=11.sp)
            val locations=listOf(null to "Nicht zugeordnet") + rooms.map { it.id to it.name }
            locations.forEach { (id,label) ->
                Row(Modifier.fillMaxWidth().clickable { vm.moveDehumidifier(context,id) },verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=vm.dehumidifierRoomId==id,onClick={ vm.moveDehumidifier(context,id) },colors=RadioButtonDefaults.colors(selectedColor=Cyan,unselectedColor=TextSoft))
                    Text(label,color=Color.White,fontSize=13.sp)
                }
            }
            DehumidifierOperationPicker(vm)
            Text("Zuordnung ist kein Betriebsnachweis. Midea/NetHome Plus und Anker sind noch nicht verbunden.",color=TextSoft,fontSize=10.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SettingsScreen(vm:ThermoViewModel) {
    var imageFloor by remember { mutableStateOf("Gesamt") }
    val ctx=LocalContext.current
    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id=selectedRoomId
        if(uri!=null && id!=null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception){}
            vm.saveUri(ctx,id,uri)
        }
    }
    AppScaffold("Einstellungen","Temperatur · Lüftungslogik · Raumbilder",5,null,vm) {
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wetteranimationen",color=Color.White,fontWeight=FontWeight.Bold)
                    Text("Dezente Bewegung in der Wetterszene",color=TextSoft,fontSize=10.sp)
                }
                Switch(colors=SwitchDefaults.colors(checkedThumbColor=Navy,checkedTrackColor=Cyan),checked=vm.weatherAnimations,onCheckedChange={ enabled ->
                    vm.weatherAnimations=enabled
                    ctx.getSharedPreferences("display",Context.MODE_PRIVATE)
                        .edit().putBoolean("weather_animations",enabled).apply()
                })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("Auto" to null,"Mittag" to 12,"Abend" to 19,"Nacht" to 23).forEach { (label,hour) ->
                Pill(label,vm.sceneHourOverride==hour) { vm.sceneHourOverride=hour }
            }
        }
        Text("Licht: Auto nach Geräte-Uhrzeit · sonst Testansicht",color=TextSoft,fontSize=10.sp,modifier=Modifier.padding(horizontal=14.dp))
        Text("Wetter-Szenen testen",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=14.dp))
        Text(vm.weatherSource,color=TextSoft,fontSize=10.sp,modifier=Modifier.padding(horizontal=14.dp))
        TextButton(onClick={vm.weatherTest=null}) { Text("Aktuelles Wetter verwenden",color=Cyan) }
        Text("Die Szene auf der Startseite reagiert sofort.",color=TextSoft,fontSize=10.sp,modifier=Modifier.padding(horizontal=14.dp))
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
            listOf(listOf("Sonnig","Bewölkt","Regen"),listOf("Gewitter","Schnee","Nacht")).forEach { rowScenes ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    rowScenes.forEach { scene ->
                        val active=vm.weather==scene
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                                .background(if(active) Cyan else Color.Transparent)
                                .border(1.dp,if(active) Cyan else Color.White.copy(alpha=.55f),RoundedCornerShape(22.dp))
                                .clickable { vm.weatherTest=scene }.padding(vertical=10.dp),
                            contentAlignment=Alignment.Center
                        ) {
                            Text(scene,color=if(active) Navy else Color.White,fontSize=10.sp,
                                fontWeight=if(active) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
        GlassCard(
            Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),
            alpha=.55f,padding=PaddingValues(0.dp)
        ) {
            Box(Modifier.width(132.dp).align(Alignment.CenterHorizontally).aspectRatio(3f / 5f).clip(RoundedCornerShape(14.dp))) {
                AnimatedHouseScene(vm.weather,Modifier.fillMaxSize(),vm.weatherAnimations,vm.sceneHourOverride,vm.weatherEffects)
                Text(
                    "Vorschau: ${vm.weather}",color=Color.White,fontWeight=FontWeight.Bold,fontSize=9.sp,lineHeight=12.sp,
                    modifier=Modifier.align(Alignment.BottomStart).padding(10.dp)
                        .background(Color.Black.copy(alpha=.45f),RoundedCornerShape(10.dp))
                        .padding(horizontal=9.dp,vertical=5.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Temperatur-Ziele",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=14.dp))
        listOf("Komfort" to "22 °C","Eco" to "20 °C","Nacht" to "17 °C").forEach {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp),alpha=.68f) {
                Row(verticalAlignment=Alignment.CenterVertically){Text(it.first,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(it.second,color=Cyan,fontWeight=FontWeight.Bold)}
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Raumbilder",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=14.dp))
        Text("Jeder Raum hat ein eigenes Standardbild. Deine Auswahl wird gespeichert.",color=TextSoft,fontSize=10.sp,modifier=Modifier.padding(horizontal=14.dp))
        Spacer(Modifier.height(4.dp))
        FloorRegister(imageFloor) { imageFloor=it }
        val imageRooms=if(imageFloor=="Gesamt") rooms else rooms.filter { it.floor==imageFloor }
        imageRooms.forEach { room ->
            val saved=vm.savedUri(ctx,room.id)
            GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp),alpha=.66f) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RoomPhoto(saved, room.image, Modifier.size(70.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text(room.name,color=Color.White,fontWeight=FontWeight.Bold);Text(if(saved!=null)"Eigenes Bild gespeichert" else "Standardbild",color=TextSoft,fontSize=10.sp)}
                    Column(horizontalAlignment=Alignment.End) {
                        TextButton(onClick={selectedRoomId=room.id;picker.launch(arrayOf("image/*"))}){Text("Bild ändern",color=Cyan)}
                        if(saved!=null) TextButton(onClick={vm.resetUri(ctx,room.id)}){Text("Demo-Bild",color=TextSoft)}
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
