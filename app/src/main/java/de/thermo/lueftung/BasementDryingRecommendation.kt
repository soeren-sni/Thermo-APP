package de.thermo.lueftung

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun BasementDryingRecommendation(vm: ThermoViewModel, baseRooms: List<Room>) {
    val currentRooms=baseRooms.map { liveRoom(it,vm) }
    val demo=currentRooms.all { it.measuredAtMillis==null }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30000); now=System.currentTimeMillis() } }
    val evaluatedAt=maxOf(now,System.currentTimeMillis())
    val candidates=currentRooms.filter { demo || it.measuredAtMillis!=null }.map {
        DryingRoomSnapshot(it.id,it.temp.toDouble(),it.humidity.toDouble(),it.measuredAtMillis ?: evaluatedAt,
            vm.wallTemperatures[it.id])
    }
    val suggestion=DehumidifierRecommendation.suggest(candidates,evaluatedAt)
    val selected=currentRooms.firstOrNull { it.id==suggestion?.roomId }
    val soft=Color.White.copy(alpha=.72f)
    GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp),alpha=.62f) {
        Text("Entfeuchter-Empfehlung",color=Color.White,fontWeight=FontWeight.Bold)
        Text(if(demo) "Demoanalyse · noch keine echten Sensordaten" else "Raumweise Analyse · Werte maximal 15 Minuten alt",color=soft,fontSize=10.sp)
        currentRooms.forEach { room ->
            Text("${room.name}: ${room.temp} °C · ${room.humidity} % RH" +
                if(!demo && room.measuredAtMillis==null) " · nicht verbunden" else "",color=soft,fontSize=11.sp)
        }
        if(suggestion==null || selected==null) {
            Text("Aktuell kein vorrangiger Raum aus den verfügbaren Werten ableitbar.",color=Color.White,fontSize=13.sp)
        } else {
            Text(selected.name,color=Color(0xFF2CC7E8),fontWeight=FontWeight.Bold,fontSize=17.sp)
            Text(when(suggestion.reason) {
                DryingReason.CONDENSATION -> "Kalte Wand: Tauwasser möglich. Ursache prüfen und Feuchte gezielt senken."
                DryingReason.HIGH_SURFACE_HUMIDITY -> String.format(Locale.GERMANY,
                    "Hohes Oberflächenrisiko: aw ≈ %.2f. Länger anhaltende Feuchte vermeiden.",suggestion.estimatedAw)
                DryingReason.HIGH_AIR_HUMIDITY -> "Erhöhte Luftfeuchte: ${selected.humidity} %. Wandklima zusätzlich prüfen."
            },color=soft,fontSize=12.sp)
            Text(if(vm.dehumidifierRoomId==selected.id) "Manuell gewählter Standort stimmt mit der Empfehlung überein."
                else "Bei Bedarf hier einsetzen und anschließend den Standort unten bestätigen.",color=soft,fontSize=11.sp)
        }
        Text("Ohne Wandmessung bleibt das Wandrisiko offen. Veraltete Werte werden nicht zur Rangfolge verwendet.",color=soft,fontSize=10.sp)
        Text("Vor dem Entfeuchten die taupunktbezogene Lüftungsberatung im Raum prüfen. Manuell hat Vorrang.",color=soft,fontSize=10.sp)
    }
}
