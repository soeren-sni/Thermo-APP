package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test

class ManualDehumidifierPlacementTest {
    @Test fun movingDeviceClearsPreviousOperationReport() {
        val running=ManualDehumidifierPlacement("fitness",ManualDehumidifierOperation.RUNNING)
        val moved=running.movedTo("workshop")
        assertEquals("workshop",moved.roomId)
        assertEquals(ManualDehumidifierOperation.UNKNOWN,moved.operation)
        assertEquals(running,running.movedTo("fitness"))
    }
    @Test fun removingLocationClearsOperation() {
        val placement=ManualDehumidifierPlacement("laundry",ManualDehumidifierOperation.RUNNING).movedTo(null)
        assertNull(placement.roomId)
        assertEquals(ManualDehumidifierOperation.UNKNOWN,placement.operation)
    }
    @Test(expected=IllegalArgumentException::class) fun cannotReportOperationWithoutLocation() {
        ManualDehumidifierPlacement().reported(ManualDehumidifierOperation.RUNNING)
    }
    @Test fun reportingOperationKeepsTheLocation() {
        val stopped=ManualDehumidifierPlacement("workshop").reported(ManualDehumidifierOperation.STOPPED)
        assertEquals("workshop",stopped.roomId)
        assertEquals(ManualDehumidifierOperation.STOPPED,stopped.operation)
    }
}
