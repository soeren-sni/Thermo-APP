package de.thermo.lueftung

/** Explicit source; a failed live request never manufactures demo readings as live. */
enum class ClimateSource { DEMO, TUYA_LIVE }
enum class HeatingOwnership { AUTO, MANUAL_OVERRIDE, OFF }
data class HeatingState(val devicePower:Boolean?,val targetTemperature:Double?,val currentTemperature:Double?,val isActivelyHeating:Boolean?,val rawWorkState:String?,val ownership:HeatingOwnership=HeatingOwnership.OFF)
data class TuyaPoint(val code:String,val value:String?,val normalized:Any?,val type:String?,val unit:String?,val scale:Int?,val read:Boolean?,val write:Boolean?,val at:Long?)
data class TuyaDevice(val id:String,val name:String,val room:String?,val role:String,val productId:String?,val category:String?,val online:Boolean,val verified:Boolean,val points:List<TuyaPoint>,val lastContact:Long?,val error:String?) {
    fun point(vararg codes:String)=codes.firstNotNullOfOrNull { code->points.firstOrNull { it.code==code } }
    fun heating():HeatingState {
        val work=point("work_state","heat_status")
        val raw=work?.normalized as? String
        val active=when { work?.normalized is Boolean->work.normalized as Boolean;raw?.lowercase() in setOf("heating","heat","heat_on")->true;raw?.lowercase() in setOf("heat_off","idle","standby")->false;else->null }
        return HeatingState(point("switch")?.normalized as? Boolean,(point("temp_set")?.normalized as? Number)?.toDouble(),(point("temp_current")?.normalized as? Number)?.toDouble(),active,work?.value)
    }
    /** Boolean contact DP is interpreted only for the standard doorcontact_state code. Unknown enum stays unknown. */
    fun window():Pair<Boolean,Long>? {
        val p=point("doorcontact_state","door_state","contact_state") ?: return null
        val at=p.at ?: return null
        val open=when(val value=p.normalized) {
            is Boolean->if(p.code=="doorcontact_state") value else return null
            is String->when(value.lowercase()) { "open","opened"->true;"close","closed"->false;else->return null }
            else->return null
        }
        return open to at
    }
}
data class TuyaSnapshot(val devices:List<TuyaDevice> = emptyList(),val climate:Map<String,RoomClimateReading> = emptyMap(),val fetchedAt:Long=0,val error:String?=null,val accountDeviceCount:Int=0,val provider:String="TUYA LIVE")
object TuyaPolicy {
    const val MAX_AGE=15*60*1000L
    fun fresh(at:Long?,now:Long)=at!=null && now-at in 0..MAX_AGE
    fun heatingHistory(device:TuyaDevice,now:Long):Pair<Boolean,Long>? {
        if(!device.online || !device.verified || device.error!=null) return null
        val p=device.point("work_state","heat_status") ?: return null
        if(!fresh(p.at,now)) return null
        return (device.heating().isActivelyHeating ?: return null) to p.at!!
    }
    fun windowEvent(device:TuyaDevice,now:Long):Pair<Boolean,Long>? {
        if(!device.online || !device.verified || device.error!=null) return null
        return device.window()?.takeIf { fresh(it.second,now) }
    }
}
