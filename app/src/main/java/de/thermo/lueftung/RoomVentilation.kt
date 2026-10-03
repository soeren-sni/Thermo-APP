package de.thermo.lueftung

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RoomVentilationCard(room:Room,vm:ThermoViewModel) {
    val context=LocalContext.current
    var permission by remember { mutableStateOf(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) }
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission=it }
        val outdoor by ThermoRuntime.outdoorClimate.collectAsState()
    val outside=outdoor?.takeIf { TuyaPolicy.fresh(it.measuredAtMillis,System.currentTimeMillis()) }
    val weather=vm.weatherData?.takeIf { it.fresh() }
    val wall=vm.wallTemperatures[room.id]?.takeIf { System.currentTimeMillis()-it.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS }?.temperatureC
    val plan=VentilationPlanner.plan(room.temp.toDouble(),room.humidity.toDouble(),outside?.temperatureC?.toDouble() ?: weather?.temperature?.toDouble() ?: 12.0,outside?.relativeHumidityPercent?.toDouble() ?: weather?.humidity?.toDouble() ?: 86.0,room.floor=="Keller",wall)
    var minutes by remember(room.id) { mutableIntStateOf(plan.minutes ?: 5) }
    val fresh=room.measuredAtMillis?.let { System.currentTimeMillis()-it in 0..DehumidifierRecommendation.MAX_AGE_MILLIS } ?: true
    var alerts by remember { mutableStateOf(ClimateNotifications.enabled(context)) }
    val timer=vm.timerSnapshot[room.id]
    GlassCard(Modifier.fillMaxWidth(),alpha=.62f) {
        Text("Lüftungsberatung & Timer",color=Color.White,fontWeight=FontWeight.Bold)
        Text(if(room.isDemo || (weather==null && outside==null)) "Vorschau · Raum-/Außenwerte enthalten Demo" else if(outside!=null) "Raumsensor + echter Tuya-Außensensor" else "Raumsensor + Open-Meteo-Modellwetter",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
        Text(if(fresh) plan.explanation else "Aktuelle Raummessung fehlt: keine automatische Lüftungsempfehlung.",color=Color.White,fontSize=12.sp)
        if(timer!=null) {
            val remaining=timer.remaining(vm.timerNow,android.os.SystemClock.elapsedRealtime(),ThermoRuntime.bootCount())
            Text(if(remaining>0) "${remaining/60}:${(remaining%60).toString().padStart(2,'0')} · läuft" else "Zeit abgelaufen – Fenster schließen",color=Color(0xFFFFD34D),fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("${timer.source} · Start ${measurementTime(timer.started)}",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
            Button(onClick={vm.stopTimer(room.id)}) { Text("Fenster geschlossen bestätigen") }
            TextButton(onClick={ThermoRuntime.acknowledgeAlarm()}) { Text("Alarmton ausschalten") }
        } else {
            if(plan.minutes!=null && fresh) TextButton(onClick={minutes=plan.minutes}) { Text("Empfehlung übernehmen · ${plan.minutes} Min",color=Color(0xFF61D39A),fontSize=12.sp) }
            if(!permission) Button(onClick={request.launch(Manifest.permission.POST_NOTIFICATIONS)}) { Text("Benachrichtigungen erlauben") }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                listOf(1,3,5,10).forEach { value -> FilterChip(selected=minutes==value,onClick={minutes=value},label={Text("$value Min",fontSize=10.sp)}) }
            }
            Button(onClick={vm.startTimer(room,minutes)},enabled=permission,modifier=Modifier.fillMaxWidth()) {
                Text(if(plan.canVentilate && fresh) "Timer starten · $minutes Min" else "Manueller Timer · $minutes Min",fontSize=12.sp)
            }
            Text("Manueller Start: Fenster jetzt öffnen. Dauer wird aufgezeichnet; nach Schließen bestätigen.",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
        }
        if(Build.VERSION.SDK_INT>=31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}) { Text("Exakte Timeralarme erlauben",fontSize=11.sp) }
            Text("Ohne diese Freigabe kann Android den Alarm im Ruhezustand verzögern.",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
        }
        Row { Switch(checked=alerts,onCheckedChange={alerts=it;context.getSharedPreferences("alerts",Context.MODE_PRIVATE).edit().putBoolean("enabled",it).apply() });Text("Klimahinweise",color=Color.White,modifier=Modifier.padding(12.dp)) }
        Text("Hinweise ab 70 % Feuchte, günstige Trocknung ab 60 %. Kältewarnung: 17 °C, Keller 14 °C. Nur frische echte Werte; regelmäßige Prüfung ca. alle 15 Min.",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
        TextButton(onClick={if(permission) ClimateNotifications.test(context,room) else request.launch(Manifest.permission.POST_NOTIFICATIONS)}) { Text("Testbenachrichtigung · Demo") }
        Text("Tuya-Kontakte nur nach Live-Freigabe. Abfrage bei geöffneter App ca. alle 5 Min.; keine Echtzeitüberwachung im Hintergrund. Tests unten sind Demo.",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
    }
}

@Composable
fun RoomSensorTestCard(room:Room) {
    var expanded by remember(room.id) { mutableStateOf(false) }
    var window by remember(room.id) { mutableStateOf(false) }
    var door by remember(room.id) { mutableStateOf(false) }
    var heating by remember(room.id) { mutableStateOf(false) }
    var temp by remember(room.id) { mutableStateOf(room.temp.toString()) }
    var humidity by remember(room.id) { mutableStateOf(room.humidity.toString()) }
    var feedback by remember(room.id) { mutableStateOf("") }
    val revision by ThermoRuntime.historyRevision.collectAsState()
    LaunchedEffect(room.id,revision) {
        withContext(Dispatchers.IO) { listOf("window","door","heating").map { ThermoRuntime.history.contactOpen("test-$it:${room.id}") } }.let { window=it[0];door=it[1];heating=it[2] }
    }
    GlassCard(Modifier.fillMaxWidth(),alpha=.50f) {
        TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Sensortests schließen" else "Sensortests öffnen · Demo") }
        if(expanded) {
            Text("Nur Emulator-/Funktionstest. Keine echten Gerätebefehle oder Sensormessungen.",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
            OutlinedTextField(value=temp,onValueChange={temp=it},label={Text("Testtemperatur °C")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=humidity,onValueChange={humidity=it},label={Text("Testfeuchte %")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(onClick={
                val t=temp.replace(',','.').toFloatOrNull();val rh=humidity.toIntOrNull()
                if(t==null || t !in -80f..80f || rh==null || rh !in 1..100) feedback="Gültige Temperatur und 1–100 % Feuchte eingeben."
                else {
                    ThermoRuntime.climate.accept(room.id,RoomClimateReading(t,rh,ClimateMath.dewPoint(t.toDouble(),rh.toDouble())!!.toFloat(),ClimateMath.absoluteHumidity(t.toDouble(),rh.toDouble()).toFloat(),System.currentTimeMillis(),isDemo=true));feedback="Testwert als Demo gespeichert."
                }
            }) { Text("Testklima speichern · Demo") }
            Text(feedback,color=Color.White,fontSize=10.sp)
            Button(onClick={window=!window;ThermoRuntime.sensorEvent(room.id,"test-window:${room.id}","WINDOW",window,demo=true)}) { Text(if(window) "Testfenster schließen" else "Testfenster öffnen") }
            Button(onClick={door=!door;ThermoRuntime.sensorEvent(room.id,"test-door:${room.id}","DOOR",door,demo=true)}) { Text(if(door) "Testtür schließen" else "Testtür öffnen") }
            Button(onClick={heating=!heating;ThermoRuntime.sensorEvent(room.id,"test-heating:${room.id}","HEATING",heating,demo=true)}) { Text(if(heating) "Testheizung aus" else "Testheizung an") }
        }
    }
}

@Composable
fun RoomContactCard(room:Room) {
    val revision by ThermoRuntime.historyRevision.collectAsState()
    var text by remember(room.id) { mutableStateOf("Noch nicht verbunden · Zustand unbekannt") }
    LaunchedEffect(room.id,revision) {
        text=withContext(Dispatchers.IO) {
            ThermoRuntime.history.readableDatabase.rawQuery("SELECT opened,source FROM contacts WHERE room=? AND type='WINDOW'",arrayOf(room.id)).use { c ->
                var count=0;var open=0;var demo=false
                while(c.moveToNext()) { count++;if(c.getInt(0)==1) open++;if(c.getString(1)=="Demo") demo=true }
                if(count==0) "Noch nicht verbunden · Zustand unbekannt" else "Zuletzt gemeldet: $open von $count Fensterkontakten offen${if(demo) " · Demo enthalten" else " · Sensor"}"
            }
        }
    }
    GlassCard(Modifier.fillMaxWidth(),alpha=.62f,padding=PaddingValues(13.dp)) { Text(text,color=Color.White,fontSize=12.sp) }
}
