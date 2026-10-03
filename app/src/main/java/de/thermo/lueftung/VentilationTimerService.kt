package de.thermo.lueftung

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All state mutations are serialized; disk access never blocks the main thread. */
class VentilationTimerService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val lock=Mutex()
    private val active=linkedMapOf<String,ActiveVentilationTimer>()
    private lateinit var restored:Deferred<Unit>
    private var sound:MediaPlayer?=null
    private var soundTimeout:Job?=null
    private var ticker:Job?=null
    private var wakeLock:PowerManager.WakeLock?=null
    private val manager get()=getSystemService(NotificationManager::class.java)
    override fun onCreate() {
        super.onCreate();ThermoRuntime.initialize(this)
        manager.createNotificationChannel(NotificationChannel("ventilation-timer","Lüftungstimer",NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("ventilation-alarm","Fenster-Erinnerung",NotificationManager.IMPORTANCE_HIGH).apply { setSound(null,null);enableVibration(true) })
        startForeground(700,notification(null))
        restored=scope.async {
            withContext(Dispatchers.IO) { ThermoRuntime.history.activeTimers() }.forEach { active[it.roomId]=it }
        }
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        scope.launch {
            restored.await()
            lock.withLock {
                val room=intent?.getStringExtra("room")
                when(intent?.action) {
                    "START" -> if(room!=null && allThermoRooms().any { it.id==room }) {
                        // Multiple open contacts must not restart an already running timer.
                        val sensorStart=intent.getBooleanExtra("sensor",false)
                        if(!sensorStart || !active.containsKey(room)) {
                            val minutes=intent.getIntExtra("minutes",5).coerceIn(1,30)
                            val now=System.currentTimeMillis()
                            val at=intent.getLongExtra("at",now).coerceAtMost(now)
                            val deadline=at+minutes*60000
                            val elapsedDeadline=SystemClock.elapsedRealtime()+(deadline-now).coerceAtLeast(0)
                            val boot=ThermoRuntime.bootCount()
                            val raw=ThermoRuntime.climate.readings.value[room]
                            val reading=raw?.takeIf { now-it.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS }
                            val source=(intent.getStringExtra("source") ?: "Manuell")+if(reading?.isDemo==true) " · Klima Demo" else ""
                            val id=withContext(Dispatchers.IO) {
                                ThermoRuntime.history.activeEvent(room,"VENTILATION")?.let { ThermoRuntime.history.end(it,at,"Timer neu gestartet",reading) }
                                ThermoRuntime.history.begin(room,"VENTILATION",source,at,minutes,reading,
                                    if(reading==null) "Keine aktuelle Klimamessung beim Start" else "Raumklima beim Start",deadline,elapsedDeadline,boot)
                            }
                            active[room]=ActiveVentilationTimer(id,room,at,deadline,source,false,elapsedDeadline,boot)
                            manager.cancel(alarmId(room))
                        }
                    }
                    "STOP" -> if(room!=null) {
                        val timer=active.remove(room)
                        withContext(Dispatchers.IO) { (timer?.eventId ?: ThermoRuntime.history.activeEvent(room,"VENTILATION"))?.let {
                            ThermoRuntime.history.end(it,System.currentTimeMillis(),intent.getStringExtra("note") ?: "Manuell beendet",ThermoRuntime.climate.readings.value[room]?.takeIf { System.currentTimeMillis()-it.measuredAtMillis in 0..DehumidifierRecommendation.MAX_AGE_MILLIS })
                        } }
                        manager.cancel(alarmId(room));silence()
                        if(intent.getBooleanExtra("alarm",false)) alert(room,intent.getStringExtra("note") ?: "Fenster schließen")
                    }
                    "ACK" -> silence()
                    "SHORTEN" -> if(room!=null) active[room]?.takeIf { !it.expired }?.let { timer ->
                        val remaining=timer.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),ThermoRuntime.bootCount())
                        val seconds=minOf(remaining,120)
                        val deadline=System.currentTimeMillis()+seconds*1000
                        val elapsedDeadline=SystemClock.elapsedRealtime()+seconds*1000
                        withContext(Dispatchers.IO) { ThermoRuntime.history.writableDatabase.execSQL("UPDATE events SET deadline=?,elapsedDeadline=?,boot=?,note=? WHERE id=?",arrayOf(deadline,elapsedDeadline,ThermoRuntime.bootCount(),"Tür geöffnet · Querlüftung: Restzeit auf maximal 2 Minuten verkürzt",timer.eventId)) }
                        active[room]=timer.copy(deadline=deadline,elapsedDeadline=elapsedDeadline,boot=ThermoRuntime.bootCount())
                    }
                }
                publish();configureWakeup();expireTimers()
            }
            startTicker()
        }
        return START_STICKY
    }
    private fun publish() {
        ThermoRuntime.timers.value=active.toMap();ThermoRuntime.historyRevision.value++
        manager.notify(700,notification(active.values.minByOrNull { it.deadline }))
    }
    private suspend fun expireTimers() {
        active.toMap().forEach { (room,timer) ->
            if(timer.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),ThermoRuntime.bootCount())==0 && !timer.expired) {
                active[room]=timer.copy(expired=true)
                withContext(Dispatchers.IO) { ThermoRuntime.history.markAlarm(timer.eventId) }
                alert(room,"Lüftungszeit abgelaufen – Fenster schließen und bestätigen")
                publish();configureWakeup()
            }
        }
    }
    private fun startTicker() {
        if(ticker?.isActive==true) return
        ticker=scope.launch {
            while(isActive) {
                lock.withLock { expireTimers() }
                if(active.isEmpty()) {
                    delay(if(sound!=null) 60000 else 1000)
                    // A new timer may have arrived while the alarm was sounding.
                    if(active.isEmpty()) { silence();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();break }
                } else manager.notify(700,notification(active.values.minByOrNull { it.deadline }))
                delay(1000)
            }
        }
    }
    private fun wakeIntent()=PendingIntent.getForegroundService(this,799,Intent(this,VentilationTimerService::class.java).setAction("TICK"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun configureWakeup() {
        val alarm=getSystemService(AlarmManager::class.java)
        alarm.cancel(wakeIntent())
        wakeLock?.let { if(it.isHeld) it.release() };wakeLock=null
        val seconds=active.values.filter { !it.expired }.minOfOrNull { it.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),ThermoRuntime.bootCount()) } ?: return
        if(Build.VERSION.SDK_INT<31 || alarm.canScheduleExactAlarms()) {
            runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+seconds*1000,wakeIntent()) }
        }
        wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Thermo:ventilationCountdown").apply { acquire((seconds*1000L+60000).coerceIn(1000,31*60000)) }
    }
    private fun pageIntent(room:String?):PendingIntent=PendingIntent.getActivity(this,701+(room?.hashCode()?.and(0x7fff) ?: 0),
        Intent(this,MainActivity::class.java).putExtra("show_timer",true).putExtra("timer_room",room).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(timer:ActiveVentilationTimer?):Notification {
        val builder=NotificationCompat.Builder(this,"ventilation-timer").setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(if(timer==null) "Lüftungstimer" else dehumidifierLocationName(timer.roomId))
            .setContentText(when { timer==null -> "Timer wird vorbereitet"; timer.expired -> "Zeit abgelaufen – Fenster schließen"; else -> "Lüftung läuft · ${active.size} Raum/Räume" })
            .setContentIntent(pageIntent(timer?.roomId)).setOnlyAlertOnce(true).setOngoing(true).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if(timer!=null && !timer.expired) builder.setWhen(System.currentTimeMillis()+timer.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),ThermoRuntime.bootCount())*1000L).setUsesChronometer(true).setChronometerCountDown(true)
        if(timer!=null) builder.addAction(0,"Fenster zu / beenden",PendingIntent.getService(this,timer.roomId.hashCode(),
            Intent(this,VentilationTimerService::class.java).setAction("STOP").putExtra("room",timer.roomId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return builder.build()
    }
    private fun alarmId(room:String)=800+room.hashCode().and(0x7fff)
    private fun alert(room:String,text:String) {
        manager.notify(alarmId(room),NotificationCompat.Builder(this,"ventilation-alarm").setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle("${dehumidifierLocationName(room)} · Fenster-Erinnerung").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pageIntent(room))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0,"Ton aus",PendingIntent.getService(this,702,Intent(this,VentilationTimerService::class.java).setAction("ACK"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        silence()
        runCatching {
            val uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            sound=MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@VentilationTimerService,uri);isLooping=true;prepare();start()
            }
        }
        soundTimeout=scope.launch { delay(60000);silence() }
    }
    private fun silence() { soundTimeout?.cancel();soundTimeout=null;sound?.runCatching { stop();release() };sound=null }
    override fun onDestroy() {
        wakeLock?.let { if(it.isHeld) it.release() };getSystemService(AlarmManager::class.java).cancel(wakeIntent())
        silence();scope.cancel();super.onDestroy()
    }
}
