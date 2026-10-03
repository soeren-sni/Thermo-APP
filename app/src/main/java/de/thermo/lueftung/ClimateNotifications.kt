package de.thermo.lueftung

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/** Conservative defaults, no advice from stale or demo sensor values. */
object ClimateAlertPolicy {
    fun messages(temperature:Double,humidity:Double,basement:Boolean,canDry:Boolean?):List<Pair<String,String>> = buildList {
        if(temperature<=if(basement) 14.0 else 17.0) add("cold" to "Raum zu kalt: Fenster schließen. Weiteres Auskühlen erhöht den Heizbedarf.")
        if(humidity>=70) add("humidity" to if(canDry==true) "Feuchte mindestens 70 %: Außenluft ist aktuell zum Trocknen geeignet. Kurz lüften und Temperatur beobachten." else "Feuchte mindestens 70 %: Ursache prüfen. Ohne passende Außen- und Wandwerte keine Lüftungsfreigabe.")
        if(canDry==true && humidity>=60 && temperature>if(basement) 14.0 else 17.0) add("opportunity" to "Aktuell günstiger Lüftungszeitpunkt: Außenluft kann Feuchte abführen. Geschätzte Dauer im Raum prüfen.")
    }
}
object ClimateNotifications {
    fun enabled(context:Context)=context.getSharedPreferences("alerts",Context.MODE_PRIVATE).getBoolean("enabled",true)
    fun evaluate(context:Context,roomId:String,reading:RoomClimateReading,outside:WeatherData?,wall:WallTemperatureReading?) {
        val now=System.currentTimeMillis()
        if(!enabled(context) || reading.isDemo || now-reading.measuredAtMillis !in 0..DehumidifierRecommendation.MAX_AGE_MILLIS) return
        val room=allThermoRooms().firstOrNull { it.id==roomId } ?: return
        val weather=outside?.takeIf { it.fresh() }
        val wallT=wall?.takeIf { now-it.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS }?.temperatureC
        val canDry=weather?.let { VentilationPlanner.plan(reading.temperatureC.toDouble(),reading.relativeHumidityPercent.toDouble(),it.temperature.toDouble(),it.humidity.toDouble(),room.floor=="Keller",wallT).canVentilate }
        val prefs=context.getSharedPreferences("alerts",Context.MODE_PRIVATE)
        val messages=ClimateAlertPolicy.messages(reading.temperatureC.toDouble(),reading.relativeHumidityPercent.toDouble(),room.floor=="Keller",canDry)
        val keys=setOf("cold","humidity","opportunity")
        keys.filter { key -> messages.none { it.first==key } }.forEach { prefs.edit().putBoolean("active:$roomId:$it",false).apply() }
        messages.forEach { (kind,text) ->
            val key="$roomId:$kind"
            val previous=prefs.getLong("last:$key",0)
            if(!prefs.getBoolean("active:$key",false) || now-previous>=2*60*60000L) {
                if(show(context,room.name,text,1000+key.hashCode().and(0xffff),roomId))
                    prefs.edit().putBoolean("active:$key",true).putLong("last:$key",now).apply()
            }
        }
    }
    fun test(context:Context,room:Room) { show(context,"${room.name} · Test / Demo","Testbenachrichtigung: Fenster öffnen oder schließen. Kein echter Sensoralarm.",1099,room.id) }
    private fun show(context:Context,title:String,text:String,id:Int,roomId:String):Boolean {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(NotificationChannel("climate-advice","Lüftungs- und Klimahinweise",NotificationManager.IMPORTANCE_HIGH))
        val intent=Intent(context,MainActivity::class.java).putExtra("room_advice",roomId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        manager.notify(id,NotificationCompat.Builder(context,"climate-advice").setSmallIcon(R.drawable.ic_notification_timer).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        return true
    }
}
/** Android schedules periodic checks (~15 min, possibly delayed by energy saving). */
class ClimateNotificationJob:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onStartJob(params:JobParameters):Boolean {
        scope.launch {
            val outside=runCatching { WeatherRepository.fetch() }.getOrNull()
            ThermoRuntime.weather=outside
            allThermoRooms().forEach { room -> ThermoRuntime.history.latest(room.id)?.let { sample ->
                val reading=RoomClimateReading(sample.temperature.toFloat(),sample.humidity.toInt(),sample.dewPoint.toFloat(),ClimateMath.absoluteHumidity(sample.temperature,sample.humidity).toFloat(),sample.at,isDemo=sample.source=="Demo")
                ClimateNotifications.evaluate(this@ClimateNotificationJob,room.id,reading,outside,ThermoRuntime.walls[room.id])
            } }
            jobFinished(params,false)
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean { scope.coroutineContext.cancelChildren();return true }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
    companion object {
        fun schedule(context:Context) {
            val scheduler=context.getSystemService(JobScheduler::class.java)
            if(scheduler.getPendingJob(1125)==null) scheduler.schedule(JobInfo.Builder(1125,ComponentName(context,ClimateNotificationJob::class.java)).setPeriodic(15*60000L).setPersisted(true).build())
        }
    }
}
