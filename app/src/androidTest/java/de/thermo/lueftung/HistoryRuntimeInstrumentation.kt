package de.thermo.lueftung

import android.app.Instrumentation
import android.os.Bundle

/** Dependency-free device test runner. Uses an isolated database, never user history. */
class HistoryRuntimeInstrumentation:Instrumentation() {
    private var tuyaOnly=false
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);tuyaOnly=arguments?.getString("tuya_only")=="true";start() }
    override fun onStart() {
        val result=Bundle()
        try {
            TuyaDeviceChecks.run(targetContext)
            if(tuyaOnly) { result.putString("result","Tuya JSON fixtures, offline policy and Keystore checks passed (no real devices)");finish(android.app.Activity.RESULT_OK,result);return }
            targetContext.deleteDatabase("validation-history.db")
            var db=HistoryDatabase(targetContext,"validation-history.db")
            val at=System.currentTimeMillis()-10000
            val reading=RoomClimateReading(22f,70,16f,13f,at,isDemo=true)
            db.record("bath",reading);db.record("bath",reading);db.record("office",reading)
            check(db.samples("bath",at-1,at+1).size==1)
            check(db.samples("bath",at+1,at+100).isEmpty())
            val id=db.begin("bath","VENTILATION","Demo",at,1,reading,deadline=at+60000,elapsedDeadline=100000,boot=1)
            check(db.activeTimers().single().elapsedDeadline==100000L)
            db.end(id,at+1000,reading=reading.copy(temperatureC=20f));check(db.activeTimers().isEmpty())
            val mixed=db.begin("office","HEATING_real","Sensor",at,reading=reading.copy(isDemo=false))
            db.end(mixed,at+1000,reading=reading)
            check(db.events("office",at,at+2000).single().source.contains("Ende Klima Demo"))
            check(db.events("bath",at+500,at+600).single().id==id)
            check(db.contact("window","bath","WINDOW",true,at,"Demo"))
            check(!db.contact("window","bath","WINDOW",true,at+500,"Demo"))
            check(!db.contact("window","bath","WINDOW",false,at+250,"Demo"))
            check(db.openWindows("bath")==1)
            check(db.contact("second","bath","WINDOW",true,at+500,"Demo"))
            check(db.contact("window","bath","WINDOW",false,at+1000,"Demo"));check(db.openWindows("bath")==1)
            check(db.contact("second","bath","WINDOW",false,at+1000,"Demo"));check(db.openWindows("bath")==0)
            db.close();db=HistoryDatabase(targetContext,"validation-history.db")
            check(db.latest("bath")?.source=="Demo");check(db.events("bath",at,at+10000).single().end==at+1000)
            check(db.events("bath",at,at+10000).single().endTemperature==20.0)
            db.close();targetContext.deleteDatabase("validation-history.db")
            val room="child2"
            ThermoRuntime.climate.accept(room,reading.copy(measuredAtMillis=System.currentTimeMillis()))
            fun await(description:String,seconds:Int=30,condition:()->Boolean) {
                val until=android.os.SystemClock.elapsedRealtime()+seconds*1000
                while(!condition()) { check(android.os.SystemClock.elapsedRealtime()<until) { "Timed out: $description" };Thread.sleep(100) }
            }
            ThermoRuntime.history.activeTimers().forEach { ThermoRuntime.stopTimer(it.roomId,"Demo · vorherigen Prüfversuch beenden") }
            await("previous timers closed") { ThermoRuntime.history.activeTimers().isEmpty() && ThermoRuntime.timers.value.isEmpty() }
            ThermoRuntime.sensorEvent(room,"validation-window-1","WINDOW",true,demo=true)
            await("window starts timer") { ThermoRuntime.timers.value[room]!=null }
            val first=ThermoRuntime.timers.value.getValue(room).eventId
            ThermoRuntime.sensorEvent(room,"validation-window-2","WINDOW",true,demo=true)
            await("second window recorded") { ThermoRuntime.history.openWindows(room)==2 }
            check(ThermoRuntime.timers.value.getValue(room).eventId==first)
            ThermoRuntime.sensorEvent(room,"validation-door","DOOR",true,demo=true)
            ThermoRuntime.sensorEvent(room,"validation-heating","HEATING",true,demo=true)
            await("heating recorded") { ThermoRuntime.history.activeEvent(room,"HEATING_validation-heating")!=null }
            ThermoRuntime.sensorEvent(room,"validation-window-1","WINDOW",false,demo=true)
            await("first window closes") { ThermoRuntime.history.openWindows(room)==1 }
            check(ThermoRuntime.timers.value[room]!=null)
            ThermoRuntime.sensorEvent(room,"validation-window-2","WINDOW",false,demo=true)
            await("last window stops timer") { ThermoRuntime.timers.value[room]==null }
            ThermoRuntime.sensorEvent(room,"validation-door","DOOR",false,demo=true)
            ThermoRuntime.sensorEvent(room,"validation-heating","HEATING",false,demo=true)
            await("heating stopped") { ThermoRuntime.history.activeEvent(room,"HEATING_validation-heating")==null }
            ThermoRuntime.startTimer(room,1,"Demo · automatisierter Hintergrundtest")
            await("manual timer starts") { ThermoRuntime.timers.value[room]!=null }
            val manager=targetContext.getSystemService(android.app.NotificationManager::class.java)
            await("ongoing notification delivered") { manager.activeNotifications.any { it.id==700 } }
            ClimateNotifications.test(targetContext,allThermoRooms().first { it.id==room })
            await("test climate notification delivered") { manager.activeNotifications.any { it.notification.channelId=="climate-advice" && it.notification.extras.getString("android.title")?.contains("Demo")==true } }
            manager.cancel(1099)
            await("background alarm expires",80) { ThermoRuntime.timers.value[room]?.expired==true }
            await("alarm notification delivered") { manager.activeNotifications.any { it.notification.channelId=="ventilation-alarm" } }
            ThermoRuntime.stopTimer(room,"Demo · Hintergrundtest beendet")
            await("timer cleared") { ThermoRuntime.timers.value[room]==null }
            val events=ThermoRuntime.history.events(room,at-1000,System.currentTimeMillis())
            check(events.any { it.type.startsWith("HEATING") && it.end!=null })
            check(events.filter { it.type=="VENTILATION" }.all { it.end!=null })
            java.io.File(targetContext.cacheDir,"Thermo_V11.26_Runtime_DEMO.xlsx").outputStream().use {
                ExcelHistoryExport.write(HistorySelection(room,"Bühne · Sensortest",at-1000,System.currentTimeMillis(),HistoryMetric.entries.toSet(),ThermoRuntime.history.samples(room,at-1000,System.currentTimeMillis()),events,true),it)
            }
            result.putString("stream","PASS: SQLite persistence, room/range isolation, event overlap/duration, monotonic deadline persistence, duplicate/late packets and multiple windows; sensor-triggered timer start/stop, door/heating events, ongoing notification and 60-second background alarm.\n")
            finish(-1,result)
        } catch(error:Throwable) { result.putString("stream","FAIL: ${error.stackTraceToString()}");finish(0,result) }
    }
}
