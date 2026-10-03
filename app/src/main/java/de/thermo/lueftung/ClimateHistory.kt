package de.thermo.lueftung

import java.time.Instant
import java.time.ZoneId
import kotlin.math.sin

enum class HistoryPeriod(val label:String) {
    DAY("Tag"), WEEK("Woche"), MONTH("Monat"), YEAR("Jahr");
    fun start(now:Long):Long {
        val time=Instant.ofEpochMilli(now).atZone(ZoneId.of("Europe/Berlin"))
        return when(this) { DAY->time.minusDays(1); WEEK->time.minusWeeks(1); MONTH->time.minusMonths(1); YEAR->time.minusYears(1) }.toInstant().toEpochMilli()
    }
}
enum class HistoryMetric(val label:String,val unit:String,val color:Long) {
    TEMPERATURE("Temperatur","°C",0xFFFFBE65), HUMIDITY("Feuchte","%",0xFF2CC7E8), DEW_POINT("Taupunkt","°C",0xFFA394FF);
    fun value(sample:ClimateSample):Double=when(this) { TEMPERATURE->sample.temperature; HUMIDITY->sample.humidity; DEW_POINT->sample.dewPoint }
}
data class ClimateSample(val at:Long,val temperature:Double,val humidity:Double,val dewPoint:Double,val source:String)
data class ClimateEvent(val id:Long,val roomId:String,val type:String,val start:Long,val end:Long?,val source:String,
    val plannedMinutes:Int=0,val temperature:Double?=null,val humidity:Double?=null,val dewPoint:Double?=null,val note:String="",val endTemperature:Double?=null,val endHumidity:Double?=null,val endDewPoint:Double?=null)
data class HistorySelection(val roomId:String,val roomName:String,val start:Long,val end:Long,
    val metrics:Set<HistoryMetric>,val samples:List<ClimateSample>,val events:List<ClimateEvent>,val demo:Boolean=false)
object HistoryDemo {
    fun samples(roomId:String,start:Long,end:Long):List<ClimateSample> {
        val offset=(roomId.hashCode()%17)/10.0
        return (0..144).map { index ->
            val t=18.0+offset+sin(index*.14)*2
            val rh=54.0+sin(index*.18+offset)*12
            ClimateSample(start+(end-start)*index/144,t,rh,ClimateMath.dewPoint(t,rh)!!,"Demo")
        }
    }
    fun events(roomId:String,start:Long,end:Long):List<ClimateEvent> {
        val span=end-start
        return listOf(ClimateEvent(-1,roomId,"VENTILATION",start+span/3,start+span/3+300000,"Demo",5,20.0,65.0,13.2,"Beispiel · 5 Minuten"),
            ClimateEvent(-2,roomId,"HEATING",start+span*2/3,start+span*2/3+1800000,"Demo",0,18.0,58.0,9.8,"Beispiel · Heizung"))
    }
}
