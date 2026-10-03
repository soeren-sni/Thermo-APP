package de.thermo.lueftung

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class TuyaRepository(context:Context) {
    private val appContext=context.applicationContext
    val settings=TuyaConnectionSettings(context)
    private val mutable=MutableStateFlow(TuyaSnapshot())
    val state=mutable.asStateFlow()
    private val mode=MutableStateFlow(if(settings.live) ClimateSource.TUYA_LIVE else ClimateSource.DEMO)
    val source=mode.asStateFlow()
    private val lock=Mutex()
    private var lastEvents=mutableMapOf<String,Long>()
    fun useDemo() { settings.setLive(false);mode.value=ClimateSource.DEMO;ThermoRuntime.outdoorClimate.value=null }
    fun enableLive():Boolean {
        val now=System.currentTimeMillis()
        if(mutable.value.error!=null || mutable.value.climate.none { TuyaPolicy.fresh(it.value.measuredAtMillis,now) }) return false
        settings.setLive(true);mode.value=ClimateSource.TUYA_LIVE;accept(mutable.value);return true
    }
    suspend fun refresh()=withContext(Dispatchers.IO) { lock.withLock {
        try {
            val token=settings.token()
            require(settings.url.isNotBlank() && token.isNotBlank())
            val connection=URL(settings.url+"/v1/snapshot").openConnection() as HttpsURLConnection
            val snapshot=try {
                connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=180000
                connection.setRequestProperty("Authorization","Bearer $token")
                val code=connection.responseCode
                if(code!=200) throw BridgeHttpException(code)
                val bytes=connection.inputStream.use { it.readNBytesCompat(2_000_000) }
                parse(JSONObject(String(bytes,Charsets.UTF_8)))
            } finally { connection.disconnect() }
            coroutineContext.ensureActive()
            mutable.value=snapshot
            if(mode.value==ClimateSource.TUYA_LIVE && snapshot.error==null) accept(snapshot)
            else if(snapshot.error!=null) ThermoRuntime.outdoorClimate.value=null
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { ThermoRuntime.outdoorClimate.value=null;mutable.value=mutable.value.copy(error=when(e) { is BridgeHttpException->"Backend HTTP ${e.code}";is IllegalArgumentException->"Backend-Adresse und Lesetoken fehlen oder sind ungültig";else->"Backend nicht erreichbar – letzte Werte bleiben erhalten" }) }
    } }
    private fun accept(snapshot:TuyaSnapshot) {
        val now=System.currentTimeMillis()
        snapshot.climate.forEach { (room,reading)->
            if(TuyaPolicy.fresh(reading.measuredAtMillis,now)) {
                if(room=="outside") ThermoRuntime.outdoorClimate.value=reading
                else if(allThermoRooms().any { it.id==room }) ThermoRuntime.climate.accept(room,reading)
            }
        }
        snapshot.devices.forEach { device->
            if(device.role=="co") CoNotifications.evaluate(appContext,device)
            if(device.room==null || allThermoRooms().none { it.id==device.room }) return@forEach
            val event=when(device.role) { "window","door"->TuyaPolicy.windowEvent(device,now);"heater"->TuyaPolicy.heatingHistory(device,now);else->null } ?: return@forEach
            if(event.second<=(lastEvents[device.id] ?: 0L)) return@forEach
            lastEvents[device.id]=event.second
            // First poll establishes state, not a retroactive automatic timer start.
            ThermoRuntime.sensorEvent(device.room,device.id,when(device.role) { "window"->"WINDOW";"door"->"DOOR";else->"HEATING" },event.first,event.second,baseline=!seenDevices.contains(device.id))
            seenDevices.add(device.id)
            // Bühnenfenster contributes to attic cross-ventilation without merging room climate.
            if(device.room=="child2" && device.role=="window") ThermoRuntime.sensorEvent("child1",device.id+":attic","DOOR",event.first,event.second,baseline=true)
        }
    }
    private val seenDevices=mutableSetOf<String>()
    private class BridgeHttpException(val code:Int):Exception()
    private fun java.io.InputStream.readNBytesCompat(limit:Int):ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) { val n=read(buffer);if(n<0) break;require(out.size()+n<=limit);out.write(buffer,0,n) };return out.toByteArray()
    }
    companion object {
        private fun JSONObject.text(key:String)=if(isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
        private fun JSONObject.longOrNull(key:String)=if(isNull(key) || !has(key)) null else optLong(key).takeIf { it>0 }
        fun parse(json:JSONObject):TuyaSnapshot {
            require(json.optString("source") in setOf("TUYA LIVE","HOME ASSISTANT LOCAL"))
            val devices=buildList {
                val array=json.optJSONArray("devices")
                if(array!=null) for(i in 0 until array.length()) {
                    val d=array.getJSONObject(i)
                    val points=buildList {
                        val ps=d.optJSONArray("points")
                        if(ps!=null) for(j in 0 until ps.length()) {
                            val p=ps.getJSONObject(j)
                            add(TuyaPoint(p.getString("code"),p.text("value"),p.opt("normalized").takeUnless { it==JSONObject.NULL },p.text("type"),p.text("unit"),if(p.isNull("scale")) null else p.optInt("scale"),if(p.isNull("read")) null else p.optBoolean("read"),if(p.isNull("write")) null else p.optBoolean("write"),p.longOrNull("at")))
                        }
                    }
                    add(TuyaDevice(d.getString("id"),d.getString("name"),d.text("room"),d.getString("role"),d.text("productId"),d.text("category"),d.optBoolean("online"),d.optBoolean("verified"),points,d.longOrNull("lastContact"),d.text("error")))
                }
            }
            val climate=buildMap {
                val cs=json.optJSONArray("climate")
                if(cs!=null) for(i in 0 until cs.length()) {
                    val c=cs.getJSONObject(i);val t=c.getDouble("temperature");val h=c.getDouble("humidity");val at=c.getLong("at")
                    if(!t.isFinite() || t !in -80.0..80.0 || !h.isFinite() || h !in 1.0..100.0 || at<=0) continue
                    val ids=c.optJSONArray("sensorIds")
                    val sensorIds=if(ids==null) emptySet() else (0 until ids.length()).map { ids.getString(it) }.toSet()
                    if(sensorIds.isEmpty() || sensorIds.any { id->devices.none { it.id==id && it.online && it.verified && it.error==null } }) continue
                    put(c.getString("room"),RoomClimateReading(t.toFloat(),kotlin.math.round(h).toInt(),ClimateMath.dewPoint(t,h)!!.toFloat(),ClimateMath.absoluteHumidity(t,h).toFloat(),at,sensorIds,isDemo=false))
                }
            }
            return TuyaSnapshot(devices,climate,json.optLong("fetchedAt"),json.text("error"),json.optInt("accountDeviceCount"),json.getString("source"))
        }
    }
}
