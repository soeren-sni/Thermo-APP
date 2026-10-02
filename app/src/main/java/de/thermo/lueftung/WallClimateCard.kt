package de.thermo.lueftung

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun WallClimateCard(room: Room, vm: ThermoViewModel) {
    var wallText by rememberSaveable(room.id) { mutableStateOf(vm.wallTemperatures[room.id]?.temperatureC?.toString() ?: "") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.id) { while (true) { delay(30000); now=System.currentTimeMillis() } }
    val wallFresh=vm.wallTemperatures[room.id]?.let { now-it.measuredAtMillis <= DehumidifierRecommendation.MAX_AGE_MILLIS } == true
    val wallTemperature = wallText.replace(',', '.').toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in -80.0..80.0 && wallFresh }
    val available = room.measuredAtMillis != null || room.humidity > 0
    val soft = Color.White.copy(alpha=.72f)
    GlassCard(Modifier.fillMaxWidth(),alpha=.62f) {
        Text("Wandklima · aw", color=Color.White, fontWeight=FontWeight.Bold)
        Text("Schätzung aus Raumklima und gemessener Wandtemperatur",color=soft,fontSize=11.sp)
        if (room.measuredAtMillis==null) Text("Raumwerte: Demo",color=soft,fontSize=10.sp)
        OutlinedTextField(
            value=wallText, onValueChange={
                wallText=it
                val value=it.replace(',', '.').toDoubleOrNull()?.takeIf { t -> t.isFinite() && t in -80.0..80.0 }
                if (value==null) vm.wallTemperatures.remove(room.id)
                else vm.wallTemperatures[room.id]=WallTemperatureReading(value,System.currentTimeMillis())
            }, singleLine=true,
            label={ Text("Wandtemperatur manuell · °C") },
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
            isError=wallText.isNotBlank() && wallTemperature==null,
            colors=OutlinedTextFieldDefaults.colors(
                focusedTextColor=Color.White,unfocusedTextColor=Color.White,
                focusedLabelColor=Color(0xFF2CC7E8),unfocusedLabelColor=soft
            ), modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)
        )
        if (!available) {
            Text("Raummessung fehlt",color=soft)
        } else if (wallTemperature == null) {
            Text("Für aw eine aktuelle Wandtemperatur eingeben (max. 15 Minuten alt).",color=soft,fontSize=12.sp)
        } else {
            val wall=ClimateMath.wallSurface(room.temp.toDouble(),room.humidity.toDouble(),wallTemperature)
            Text(String.format(Locale.GERMANY,"aw ≈ %.2f · Oberflächenfeuchte ≈ %.0f %%",wall.estimatedAw,wall.relativeHumidityPercent),
                color=Color.White,fontWeight=FontWeight.Bold)
            Text(when {
                wall.condensationLikely -> "Tauwasser möglich: kalte Fläche untersuchen und Feuchte senken."
                wall.estimatedAw >= .8 -> "Erhöhtes Schimmelrisiko bei länger anhaltender Feuchte."
                wall.estimatedAw >= .7 -> "Wandklima beobachten; Dauer und Material beeinflussen das Risiko."
                else -> "Aktuell geringere Oberflächenfeuchte; weiter beobachten."
            },color=soft,fontSize=11.sp)
            Text("Manuelle Momentaufnahme · bei Änderungen erneut messen. Kein Messwert für den Wassergehalt der Wand.",color=soft,fontSize=10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Lüften & Trocknen",color=Color.White,fontWeight=FontWeight.Bold)
        if (available) {
            // Explicit demo outdoor weather until an authenticated weather/sensor adapter is connected.
            val advice=DryingAdvice.ventilation(room.temp.toDouble(),room.humidity.toDouble(),12.0,86.0,
                room.floor=="Keller",wallTemperature)
            Text(when (advice) {
                VentilationAdvice.NO_DRYING_POTENTIAL -> "Außenluft bietet aktuell kein ausreichendes Trocknungspotenzial. Entfeuchtung prüfen."
                VentilationAdvice.WALL_MEASUREMENT_REQUIRED -> "Außenluft könnte trocknen. Im Keller zuerst die kälteste Wandtemperatur prüfen."
                VentilationAdvice.OUTSIDE_DEW_POINT_TOO_HIGH -> "Außentaupunkt zu nahe an der Wandtemperatur: keine Lüftungsempfehlung. Entfeuchtung prüfen."
                VentilationAdvice.DRYING_POTENTIAL -> "Außenluft bietet Trocknungspotenzial. Fensterzustand und Messwerte vor dem Lüften prüfen."
            },color=soft,fontSize=12.sp)
        }
        Text("Außenwerte: Demo 12 °C / 86 % RH · keine automatische Gerätesteuerung",color=soft,fontSize=10.sp)
        Spacer(Modifier.height(8.dp))
        if (room.floor!="Keller" || room.id in setOf("fitness","laundry")) {
            Text("IR-Heizung berücksichtigt: wärmere Wandflächen können das aw-Risiko senken. Wasser wird dadurch nicht entfernt.",color=soft,fontSize=11.sp)
        }
        Text(when (vm.dehumidifierRoomId) {
            null -> "Mobiler Midea: Standort unter Geräte wählen. Keine Entfeuchtung diesem Raum zugeordnet."
            room.id -> "Mobiler Midea ist diesem Raum manuell zugeordnet. Betriebszustand noch nicht verbunden."
            else -> "Mobiler Midea ist einem anderen Kellerraum zugeordnet. Hier keine Entfeuchtung durch dieses Gerät annehmen."
        },color=soft,fontSize=11.sp)
        Text("Nach dem Umstellen: Geräte → Standort aktualisieren.",color=soft,fontSize=10.sp)
        Text("Manuelle Eingriffe haben Vorrang vor einer späteren Automatik.",color=soft,fontSize=10.sp)
    }
}
