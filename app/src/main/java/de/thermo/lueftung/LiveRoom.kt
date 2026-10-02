package de.thermo.lueftung

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun liveRoom(base: Room, vm: ThermoViewModel): Room {
    val stream = remember(vm, base.id) {
        vm.climate.readings.map { it[base.id] }.distinctUntilChanged()
    }
    val reading by stream.collectAsStateWithLifecycle(initialValue = null)
    return reading?.let {
        base.copy(temp = it.temperatureC, humidity = it.relativeHumidityPercent,
            dewPoint = it.dewPointC, absHumidity = it.absoluteHumidityGramsPerCubicMeter,
            measuredAtMillis = it.measuredAtMillis)
    } ?: base
}

fun measurementTime(timestampMillis: Long): String = java.time.format.DateTimeFormatter
    .ofPattern("HH:mm:ss")
    .format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(java.time.ZoneId.systemDefault()))
