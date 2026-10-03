package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test

class VentilationPlannerTest {
    @Test fun coldRoomsBlockAutomaticAdvice() {
        assertFalse(VentilationPlanner.plan(17.0,80.0,0.0,50.0,false,null).canVentilate)
        assertFalse(VentilationPlanner.plan(14.0,80.0,0.0,50.0,true,13.0).canVentilate)
    }
    @Test fun basementRequiresCurrentWallInput() {
        assertNull(VentilationPlanner.plan(18.0,80.0,4.0,50.0,true,null).minutes)
    }
    @Test fun moistOutsideDoesNotGetDryingTimer() {
        assertFalse(VentilationPlanner.plan(20.0,60.0,30.0,90.0,false,null).canVentilate)
    }
    @Test fun highTemperatureDifferenceAndCrossVentilationShortenEstimate() {
        assertEquals(3,VentilationPlanner.plan(22.0,70.0,4.0,60.0,false,null).minutes)
        assertEquals(2,VentilationPlanner.plan(22.0,70.0,4.0,60.0,false,null,true).minutes)
    }
    @Test fun monotonicCountdownSurvivesWallClockChange() {
        val timer=ActiveVentilationTimer(1,"bath",1000,61000,"Test",false,80000,12)
        assertEquals(20,timer.remaining(3600000,60000,12))
        assertEquals(0,timer.remaining(500,81000,12))
        assertEquals(60,timer.remaining(1000,60000,13))
    }
    @Test fun yearMonthAndWeekUseCalendarTime() {
        val now=java.time.Instant.parse("2026-03-31T12:00:00Z").toEpochMilli()
        val start=java.time.Instant.ofEpochMilli(HistoryPeriod.MONTH.start(now)).atZone(java.time.ZoneId.of("Europe/Berlin"))
        assertEquals(28,start.dayOfMonth)
        assertTrue(HistoryPeriod.YEAR.start(now)<HistoryPeriod.MONTH.start(now))
    }
    @Test fun alertsKeepVentilationBlockedWhenColdOrOutsideUnknown() {
        val cold=ClimateAlertPolicy.messages(16.0,80.0,false,false)
        assertTrue(cold.any { it.first=="cold" });assertFalse(cold.any { it.first=="opportunity" })
        assertFalse(ClimateAlertPolicy.messages(20.0,80.0,false,null).any { it.first=="opportunity" })
        assertTrue(ClimateAlertPolicy.messages(20.0,65.0,false,true).any { it.first=="opportunity" })
    }
}
