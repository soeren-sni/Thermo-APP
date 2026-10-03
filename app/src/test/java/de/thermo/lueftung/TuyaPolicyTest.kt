package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test

class TuyaPolicyTest {
    private val now=10000000L
    private fun point(code:String,value:Any?,at:Long?=now)=TuyaPoint(code,value?.toString(),value,"Enum",null,null,true,false,at)
    private fun device(vararg points:TuyaPoint)=TuyaDevice("test","Test","living","heater",null,null,true,true,points.toList(),now,null)
    @Test fun powerOnDoesNotMeanHeating() {
        val d=device(point("switch",true),point("work_state","heat_off"))
        assertEquals(true,d.heating().devicePower);assertEquals(false,d.heating().isActivelyHeating)
        assertEquals(false,TuyaPolicy.heatingHistory(d,now)?.first)
    }
    @Test fun unknownHeatingStateRemainsUnknown() {
        val d=device(point("switch",true),point("work_state","new_firmware_state"))
        assertNull(d.heating().isActivelyHeating);assertNull(TuyaPolicy.heatingHistory(d,now))
    }
    @Test fun staleOrOfflineHeatingDoesNotCreateEvents() {
        val d=device(point("work_state","heating",now-TuyaPolicy.MAX_AGE-1))
        assertNull(TuyaPolicy.heatingHistory(d,now));assertNull(TuyaPolicy.heatingHistory(device(point("work_state","heating")).copy(online=false),now))
    }
    @Test fun thermostatWindowDpIsNotARealWindowContact() {
        assertNull(device(point("windows_open",true)).window())
        assertNull(device(point("switch",true)).window())
    }
    @Test fun openClosedContactEnumsAreRecognized() {
        assertEquals(true,device(point("doorcontact_state","Open")).window()?.first)
        assertEquals(false,device(point("doorcontact_state","Closed")).window()?.first)
    }
    @Test fun unknownContactEnumOrTimestampNeverInventsClosed() {
        assertNull(device(point("door_state","something_new")).window())
        assertNull(device(point("door_state","open",null)).window())
        assertNull(device(point("door_state",true)).window())
    }
    @Test fun verifiedFreshContactRequired() {
        val d=device(point("doorcontact_state",true))
        assertNotNull(TuyaPolicy.windowEvent(d,now))
        assertNull(TuyaPolicy.windowEvent(d.copy(verified=false),now))
        assertNull(TuyaPolicy.windowEvent(d.copy(error="permission missing"),now))
        assertNull(TuyaPolicy.windowEvent(d,now+TuyaPolicy.MAX_AGE+1))
        assertNull(TuyaPolicy.windowEvent(d,now-1))
    }
    @Test fun heaterSensorsRemainSeparate() {
        val d=device(point("temp_set",21.0),point("temp_current",23.7))
        assertEquals(21.0,d.heating().targetTemperature!!,0.0)
        assertEquals(23.7,d.heating().currentTemperature!!,0.0)
        assertEquals(HeatingOwnership.OFF,d.heating().ownership)
    }
}
