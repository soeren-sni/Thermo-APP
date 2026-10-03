package de.thermo.lueftung
import org.junit.Assert.*
import org.junit.Test
class CoPolicyTest {
    private val now=10000000L
    private fun sensor(value:String,at:Long=now)=TuyaDevice("co","CO","living","co",null,null,true,true,listOf(TuyaPoint("co_state",value,value,"Enum",null,null,true,false,at)),at,null)
    @Test fun onlyConfirmedAlarmOrNormalIsInterpreted() {
        assertEquals(true,CoPolicy.alarm(sensor("alarm"),now));assertEquals(false,CoPolicy.alarm(sensor("normal"),now))
        assertNull(CoPolicy.alarm(sensor("new_enum"),now));assertNull(CoPolicy.alarm(sensor("1"),now))
    }
    @Test fun offlineOrStaleDoesNotMeanSafe() {
        assertNull(CoPolicy.alarm(sensor("normal").copy(online=false),now))
        assertNull(CoPolicy.alarm(sensor("normal",now-TuyaPolicy.MAX_AGE-1),now))
    }
}
