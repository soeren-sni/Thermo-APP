package de.thermo.lueftung

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.provider.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThermoApplication:Application() {
    override fun onCreate() { super.onCreate();ThermoRuntime.initialize(this) }
}
object ThermoRuntime {
    private lateinit var context:Context
    lateinit var history:HistoryDatabase
        private set
    val io=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile var weather:WeatherData?=null
    val walls=java.util.concurrent.ConcurrentHashMap<String,WallTemperatureReading>()
    private val sensorLock=Mutex()
    val historyRevision=MutableStateFlow(0)
    val timers=MutableStateFlow<Map<String,ActiveVentilationTimer>>(emptyMap())
    val outdoorClimate=MutableStateFlow<RoomClimateReading?>(null)
    lateinit var tuya:TuyaRepository
        private set
    val climate=RoomClimateStore { room,reading ->
        if(::history.isInitialized) io.launch {
            history.record(room,reading);historyRevision.value++
            val active=timers.value[room]
            if(active!=null && !reading.isDemo && System.currentTimeMillis()-reading.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS && reading.temperatureC<=minimumVentilationTemperature(room))
                stopTimer(room,"Raum zu kalt – Fenster schließen",true)
            if(!reading.isDemo) ClimateNotifications.evaluate(context,room,reading,weather,walls[room])
        }
    }
    @Synchronized fun initialize(app:Context) {
        if(::history.isInitialized) return
        context=app.applicationContext;history=HistoryDatabase(context);tuya=TuyaRepository(context)
        ClimateNotificationJob.schedule(context)
        io.launch { allThermoRooms().forEach { room -> history.latest(room.id)?.let { s ->
            climate.accept(room.id,RoomClimateReading(s.temperature.toFloat(),s.humidity.toInt(),s.dewPoint.toFloat(),
                ClimateMath.absoluteHumidity(s.temperature,s.humidity).toFloat(),s.at,isDemo=s.source=="Demo"))
        } } }
    }
    fun bootCount():Int=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0)
    fun resumeTimers() { io.launch { if(history.activeTimers().isNotEmpty()) ContextCompat.startForegroundService(context,Intent(context,VentilationTimerService::class.java).setAction("RESTORE")) } }
    fun startTimer(room:String,minutes:Int,source:String="Manuell",at:Long=System.currentTimeMillis(),sensor:Boolean=false) {
        require(minutes in 1..30)
        ContextCompat.startForegroundService(context,Intent(context,VentilationTimerService::class.java).setAction("START").putExtra("room",room).putExtra("minutes",minutes).putExtra("source",source).putExtra("at",at).putExtra("sensor",sensor))
    }
    fun stopTimer(room:String,note:String="Fenster geschlossen · manuell bestätigt",alarm:Boolean=false) {
        ContextCompat.startForegroundService(context,Intent(context,VentilationTimerService::class.java).setAction("STOP").putExtra("room",room).putExtra("note",note).putExtra("alarm",alarm))
    }
    fun acknowledgeAlarm() { ContextCompat.startForegroundService(context,Intent(context,VentilationTimerService::class.java).setAction("ACK")) }
    /** Entry point for a trusted in-app device adapter; no externally exported receiver. */
    fun sensorEvent(room:String,sensor:String,type:String,open:Boolean,at:Long=System.currentTimeMillis(),demo:Boolean=false,baseline:Boolean=false) {
        require(allThermoRooms().any { it.id==room });require(type in setOf("WINDOW","DOOR","HEATING"));require(sensor.isNotBlank())
        require(at>0 && at<=System.currentTimeMillis()+60000)
        io.launch { sensorLock.withLock {
            val source=if(demo) "Demo" else "Sensor"
            val previousWindows=history.openWindows(room)
            if(!history.contact(sensor,room,type,open,at,source)) return@withLock
            if(baseline) { historyRevision.value++;return@withLock }
            val now=System.currentTimeMillis()
            val reading=climate.readings.value[room]?.takeIf { now-it.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS }
            val eventType=if(type=="HEATING") "HEATING_$sensor" else "${type}_$sensor"
            if(open) history.begin(room,eventType,if(reading?.isDemo==true && !demo) "Sensor · Klima Demo" else source,at,reading=reading)
            else history.activeEvent(room,eventType)?.let { history.end(it,at,"Kontakt geschlossen / Heizung aus",reading) }
            historyRevision.value++
            if(type=="WINDOW") {
                if(open && previousWindows==0) {
                    val base=allThermoRooms().first { it.id==room }
                    val outdoor=outdoorClimate.value?.takeIf { !it.isDemo && now-it.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS }
                    val outside=weather?.takeIf { it.fresh() }
                    val wall=walls[room]?.takeIf { now-it.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS }
                    val plan=if(reading!=null && (outdoor!=null || outside!=null) && !reading.isDemo) VentilationPlanner.plan(reading.temperatureC.toDouble(),reading.relativeHumidityPercent.toDouble(),outdoor?.temperatureC?.toDouble() ?: outside!!.temperature.toDouble(),outdoor?.relativeHumidityPercent?.toDouble() ?: outside!!.humidity.toDouble(),base.floor=="Keller",wall?.temperatureC,history.openDoors(room)>0) else null
                    // Without verified drying potential, issue a short closing reminder.
                    // This is not a claim that ventilation is advisable.
                    startTimer(room,plan?.minutes ?: 2,source+if(plan?.minutes==null) " · Schließerinnerung, keine Lüftungsfreigabe" else "",at,sensor=true)
                } else if(!open && history.openWindows(room)==0) stopTimer(room,"Letztes Fenster geschlossen · $source")
            } else if(type=="DOOR" && open && history.openWindows(room)>0) {
                ContextCompat.startForegroundService(context,Intent(context,VentilationTimerService::class.java).setAction("SHORTEN").putExtra("room",room))
            }
        } }
    }
}
fun allThermoRooms():List<Room> = thermoRooms()
fun minimumVentilationTemperature(room:String):Float = if(allThermoRooms().firstOrNull { it.id==room }?.floor=="Keller") 14f else 17f
