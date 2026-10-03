package de.thermo.lueftung

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NeedRed=Color(0xFFFF9090)
private val LocationYellow=Color(0xFFFFD34D)
private val RunningGreen=Color(0xFF61D39A)
private val Soft=Color.White.copy(alpha=.72f)

private fun needFor(room: Room, vm: ThermoViewModel, now: Long): DehumidifierSuggestion? {
    if(room.isDemo && room.humidity<=0) return null
    return DehumidifierRecommendation.suggest(listOf(DryingRoomSnapshot(room.id,
        room.temp.toDouble(),room.humidity.toDouble(),room.measuredAtMillis ?: now,
        vm.wallTemperatures[room.id])),now)
}

@Composable
private fun StatusDot(label: String, color: Color) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label,color=color,fontSize=10.sp)
    }
}

@Composable
fun RoomDehumidifierStatus(room: Room, vm: ThermoViewModel, modifier: Modifier = Modifier) {
    val now=maxOf(vm.dryingEvaluatedAtMillis,System.currentTimeMillis())
    val need=needFor(room,vm,now)
    val here=vm.dehumidifierRoomId==room.id
    if(here || need!=null) Column(modifier) {
        when {
            here && vm.dehumidifierOperation==ManualDehumidifierOperation.RUNNING ->
                StatusDot("Hier in Betrieb · manuell gemeldet",RunningGreen)
            need!=null -> StatusDot("Hier gebraucht" + (if(here) " · Gerät hier" else "") +
                (if(room.isDemo) " · Demo" else ""),NeedRed)
            else -> StatusDot(if(vm.dehumidifierOperation==ManualDehumidifierOperation.STOPPED)
                "Steht hier · gestoppt gemeldet" else "Steht hier · Betrieb unbekannt",LocationYellow)
        }
    }
}

@Composable
fun DehumidifierLegend() {
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("Entfeuchter · Legende",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)
        StatusDot("Grün: hier in Betrieb · manuell gemeldet",RunningGreen)
        StatusDot("Gelb: steht hier · nicht als laufend gemeldet",LocationYellow)
        StatusDot("Rot: wird hier gebraucht",NeedRed)
        Text("Standort/Betrieb manuell · Bedarf aus Raumwerten, ggf. Demo",color=Soft,fontSize=9.sp)
    }
}

@Composable
fun DehumidifierOperationPicker(vm: ThermoViewModel) {
    val context=LocalContext.current
    if(vm.dehumidifierRoomId!=null) {
        Text("Betrieb manuell melden",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            ManualDehumidifierOperation.entries.forEach { operation ->
                val selected=vm.dehumidifierOperation==operation
                val color=when(operation) {
                    ManualDehumidifierOperation.RUNNING -> RunningGreen
                    ManualDehumidifierOperation.STOPPED -> LocationYellow
                    else -> Soft
                }
                OutlinedButton(onClick={vm.reportDehumidifier(context,operation)},modifier=Modifier.weight(1f),
                    contentPadding=PaddingValues(horizontal=3.dp,vertical=6.dp),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=color,
                        containerColor=if(selected) color.copy(alpha=.16f) else Color.Transparent)) {
                    Text(when(operation) {
                        ManualDehumidifierOperation.UNKNOWN -> "Unbekannt"
                        ManualDehumidifierOperation.RUNNING -> "Läuft"
                        ManualDehumidifierOperation.STOPPED -> "Gestoppt"
                    },fontSize=10.sp)
                }
            }
        }
        Text("Manuelle Meldung, kein Gerätebefehl. Echte Betriebsdaten fehlen noch.",color=Soft,fontSize=10.sp)
    }
}

@Composable
fun RoomDehumidifierCard(room: Room, vm: ThermoViewModel) {
    val context=LocalContext.current
    val now=maxOf(vm.dryingEvaluatedAtMillis,System.currentTimeMillis())
    val need=needFor(room,vm,now)
    val here=vm.dehumidifierRoomId==room.id
    val fresh=room.measuredAtMillis?.let { now-it in 0..DehumidifierRecommendation.MAX_AGE_MILLIS }
        ?: (room.humidity>0)
    GlassCard(Modifier.fillMaxWidth(),alpha=.62f) {
        Text("Entfeuchter · Raumempfehlung",color=Color.White,fontWeight=FontWeight.Bold)
        Text(if(room.isDemo) "Demoanalyse · Raumwerte noch nicht verbunden" else
            "Aus aktuellen Raumwerten · maximal 15 Minuten alt",color=Soft,fontSize=10.sp)
        Spacer(Modifier.height(6.dp))
        Text(when {
            !fresh -> "Keine Empfehlung: aktuelle Raummessung fehlt oder ist veraltet."
            need?.reason==DryingReason.CONDENSATION -> "Entfeuchtung prüfen: Tauwasser an der kalten Wand möglich."
            need?.reason==DryingReason.HIGH_SURFACE_HUMIDITY ->
                "Entfeuchtung prüfen: erhöhte Oberflächenfeuchte (aw ≈ ${String.format(java.util.Locale.GERMAN,"%.2f",need.estimatedAw)})."
            need?.reason==DryingReason.HIGH_AIR_HUMIDITY -> "Entfeuchtung prüfen: erhöhte Luftfeuchte (${room.humidity} %)."
            else -> "Aus den verfügbaren Werten aktuell kein Entfeuchtungsbedarf ableitbar."
        },color=if(need!=null) NeedRed else Color.White,fontSize=12.sp)
        Text("Ohne aktuelle Wandmessung bleibt das Wandrisiko offen. Lüftungsberatung und Ursache der Feuchte prüfen.",color=Soft,fontSize=10.sp)
        Spacer(Modifier.height(10.dp))
        Text("Standort: ${dehumidifierLocationName(vm.dehumidifierRoomId)}",color=LocationYellow,fontSize=13.sp,fontWeight=FontWeight.Bold)
        Text("Standort wird nach dem Umstellen manuell bestätigt.",color=Soft,fontSize=10.sp)
        RoomDehumidifierStatus(room,vm,Modifier.padding(vertical=6.dp))
        Button(onClick={vm.moveDehumidifier(context,if(here) null else room.id)},modifier=Modifier.fillMaxWidth(),
            colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF2CC7E8),contentColor=Color(0xFF062F45))) {
            Text(if(here) "Standortzuordnung aufheben" else "Nach Umstellen: hier zuordnen",fontSize=12.sp)
        }
        if(here) DehumidifierOperationPicker(vm)
        else if(vm.dehumidifierRoomId!=null) Text(
            if(vm.dehumidifierOperation==ManualDehumidifierOperation.RUNNING)
                "Am genannten Standort: Betrieb manuell als laufend gemeldet."
            else "Der Entfeuchter ist diesem Raum aktuell nicht zugeordnet.",color=Soft,fontSize=10.sp)
    }
}
