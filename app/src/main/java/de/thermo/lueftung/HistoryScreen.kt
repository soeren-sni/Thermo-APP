package de.thermo.lueftung

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun historyDate(at:Long,pattern:String="dd.MM.yyyy HH:mm")=DateTimeFormatter.ofPattern(pattern,Locale.GERMAN).format(Instant.ofEpochMilli(at).atZone(ZoneId.of("Europe/Berlin")))
private fun eventLabel(type:String)=when { type=="VENTILATION" -> "Lüften";type.startsWith("HEATING") -> "Heizung";type.startsWith("WINDOW") -> "Fensterkontakt";else -> "Türkontakt" }

@Composable
fun HistoryScreen(vm:ThermoViewModel) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var roomId by remember { mutableStateOf(vm.historyRoomId) }
    var expanded by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(HistoryPeriod.DAY) }
    var metrics by remember { mutableStateOf(HistoryMetric.entries.toSet()) }
    var types by remember { mutableStateOf(setOf("VENTILATION","HEATING","CONTACT")) }
    var demo by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<HistorySelection?>(null) }
    var pendingExport by remember { mutableStateOf<HistorySelection?>(null) }
    var feedback by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val revision by ThermoRuntime.historyRevision.collectAsState()
    val room=allThermoRooms().first { it.id==roomId }
    LaunchedEffect(Unit) { while(true) { delay(30000);refresh++ } }
    LaunchedEffect(roomId,period,metrics,types,demo,revision,refresh) {
        selection=null
        val end=System.currentTimeMillis();val start=period.start(end)
        selection=withContext(Dispatchers.IO) {
            val samples=if(demo) HistoryDemo.samples(roomId,start,end) else ThermoRuntime.history.samples(roomId,start,end)
            val events=(if(demo) HistoryDemo.events(roomId,start,end) else ThermoRuntime.history.events(roomId,start,end)).filter {
                it.type in types || ("HEATING" in types && it.type.startsWith("HEATING")) || ("CONTACT" in types && (it.type.startsWith("WINDOW") || it.type.startsWith("DOOR")))
            }
            HistorySelection(roomId,room.name,start,end,metrics,samples,events,demo || samples.any { it.source=="Demo" } || events.any { it.source.contains("Demo") })
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val snapshot=pendingExport;pendingExport=null
        if(uri!=null && snapshot!=null) scope.launch {
            exporting=true
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"w")?.use { ExcelHistoryExport.write(snapshot,it) } ?: error("Datei nicht geöffnet") };feedback="Excel-Datei mit Diagramm gespeichert." }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { feedback="Export fehlgeschlagen. Bitte anderen Speicherort wählen." }
            finally { exporting=false }
        }
    }
    AppScaffold("Historie","Aufzeichnung · Zeitraum · Excel",3,null,vm) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
                    Text("Raum: ${room.name}",modifier=Modifier.weight(1f),color=Color.White,fontSize=13.sp)
                    Icon(Icons.Filled.ArrowDropDown,null)
                }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    allThermoRooms().forEach { candidate -> DropdownMenuItem(text={Text(candidate.name)},onClick={roomId=candidate.id;vm.historyRoomId=roomId;expanded=false}) }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { HistoryPeriod.entries.forEach { item ->
                FilterChip(selected=period==item,onClick={period=item},label={Text(item.label,fontSize=11.sp)})
            } }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { HistoryMetric.entries.forEach { item ->
                FilterChip(selected=item in metrics,onClick={metrics=if(item in metrics) metrics-item else metrics+item},label={Text(item.label,fontSize=10.sp)})
            } }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                listOf("VENTILATION" to "Lüften","HEATING" to "Heizung","CONTACT" to "Fenster/Tür").forEach { (type,label) ->
                    FilterChip(selected=type in types,onClick={types=if(type in types) types-type else types+type},label={Text(label,fontSize=10.sp)})
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Demo-Vorschau",color=Color.White,fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
                Switch(checked=demo,onCheckedChange={demo=it})
            }
            Text(if(demo) "Erzeugte Beispieldaten, keine echte Aufzeichnung." else "Gespeicherte Raumwerte und Ereignisse. Ohne Geräteanbindung fehlen echte Messreihen.",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
            val data=selection
            if(data==null) Text("Verlauf wird geladen …",color=Color.White)
            else {
                Text("${historyDate(data.start)} – ${historyDate(data.end)}",color=Color.White.copy(alpha=.72f),fontSize=10.sp,modifier=Modifier.padding(vertical=8.dp))
                if(data.demo) Text("Enthält Demo-/Testdaten",color=Color(0xFFFFD34D),fontSize=11.sp)
                HistoryPlot(data,period)
                Button(onClick={pendingExport=data;export.launch("Thermo_V11.26_Historie_${data.roomId}_${historyDate(data.start,"yyyyMMdd-HHmm")}_${historyDate(data.end,"yyyyMMdd-HHmm")}${if(data.demo) "_DEMO" else ""}.xlsx")},enabled=!exporting && metrics.isNotEmpty() && (data.samples.isNotEmpty() || data.events.isNotEmpty()),modifier=Modifier.fillMaxWidth()) {
                    Text(if(exporting) "Export läuft …" else "Auswahl als Excel mit Diagramm")
                }
                Text(feedback,color=Color.White,fontSize=11.sp)
                Text("Ereignisse im Zeitraum",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=8.dp))
                if(data.events.isEmpty()) Text("Keine aufgezeichneten Ereignisse für diese Auswahl.",color=Color.White.copy(alpha=.72f),fontSize=11.sp)
                data.events.forEach { event ->
                    val duration=((event.end ?: System.currentTimeMillis().coerceAtLeast(event.start))-event.start).coerceAtLeast(0)/1000
                    GlassCard(Modifier.fillMaxWidth().padding(vertical=4.dp),alpha=.50f,padding=PaddingValues(10.dp)) {
                        Text("${eventLabel(event.type)} · ${historyDate(event.start)}",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)
                        Text("${if(event.end==null) "Noch offen / nicht beendet" else "Ende ${historyDate(event.end)}"} · ${duration/60} Min ${duration%60} Sek",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
                        Text("${event.source} · ${event.note}",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
                        if(event.temperature!=null) Text("Start: ${event.temperature} °C · ${event.humidity} % · TP ${event.dewPoint} °C",color=Color.White.copy(alpha=.72f),fontSize=10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPlot(data:HistorySelection,period:HistoryPeriod) {
    if(data.metrics.isEmpty()) { Text("Messwerte für das Diagramm auswählen.",color=Color.White);return }
    if(data.samples.isEmpty()) { Text("Keine Messwerte im gewählten Zeitraum. Ereignisse werden separat angezeigt; Demo-Vorschau erlaubt den Diagrammtest.",color=Color.White.copy(alpha=.72f),fontSize=12.sp);return }
    val degreeMetrics=data.metrics.filter { it!=HistoryMetric.HUMIDITY }
    val bounds=remember(data.samples,data.metrics) {
        val lo=degreeMetrics.minOfOrNull { metric -> data.samples.minOf { metric.value(it) } } ?: 0.0
        val hi=degreeMetrics.maxOfOrNull { metric -> data.samples.maxOf { metric.value(it) } } ?: 30.0
        lo-2 to hi+2
    }
    val (lo,hi)=bounds
    val plotSamples=remember(data.samples) {
        val step=((data.samples.size+399)/400).coerceAtLeast(1)
        data.samples.filterIndexed { index,_ -> index%step==0 || index==data.samples.lastIndex }
    }
    Column {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { data.metrics.sortedBy { it.ordinal }.forEach { Text("${it.label} (${it.unit})",color=Color(it.color),fontSize=10.sp) } }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(degreeMetrics.isNotEmpty()) String.format(Locale.GERMAN,"%.1f–%.1f °C",lo,hi) else "",color=Color.White.copy(alpha=.72f),fontSize=9.sp)
            Text(if(HistoryMetric.HUMIDITY in data.metrics) "Feuchte: 0–100 %" else "",color=Color.White.copy(alpha=.72f),fontSize=9.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(vertical=8.dp)) {
            fun x(at:Long)=((at-data.start).toDouble()/(data.end-data.start).coerceAtLeast(1)*size.width).toFloat().coerceIn(0f,size.width)
            repeat(5) { i -> drawLine(Color.White.copy(alpha=.13f),Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1f) }
            data.events.filter { it.type=="VENTILATION" || it.type.startsWith("HEATING") }.forEach { event ->
                val left=x(event.start);val right=x(event.end ?: data.end)
                drawRect((if(event.type=="VENTILATION") Color(0xFF61D39A) else Color(0xFFFFBE65)).copy(alpha=.14f),Offset(left,0f),androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(2f),size.height))
            }
            data.metrics.forEach { metric ->
                val path=Path()
                // Preserve all export data; only the screen path is reduced for drawing cost.
                plotSamples.forEachIndexed { index,sample ->
                    val value=metric.value(sample)
                    val fraction=if(metric==HistoryMetric.HUMIDITY) value/100.0 else (value-lo)/(hi-lo)
                    val point=Offset(x(sample.at),(size.height*(1-fraction)).toFloat())
                    if(index==0) path.moveTo(point.x,point.y) else path.lineTo(point.x,point.y)
                }
                drawPath(path,Color(metric.color),style=Stroke(2.dp.toPx()))
                if(data.samples.size==1) {
                    val sample=data.samples.first();val fraction=if(metric==HistoryMetric.HUMIDITY) metric.value(sample)/100 else (metric.value(sample)-lo)/(hi-lo)
                    drawCircle(Color(metric.color),4.dp.toPx(),Offset(x(sample.at),(size.height*(1-fraction)).toFloat()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(historyDate(data.start,if(period==HistoryPeriod.DAY) "HH:mm" else "dd.MM.yy"),color=Color.White.copy(alpha=.72f),fontSize=9.sp)
            Text(historyDate(data.end,if(period==HistoryPeriod.DAY) "HH:mm" else "dd.MM.yy"),color=Color.White.copy(alpha=.72f),fontSize=9.sp)
        }
        Text("Grüne Zeitflächen: Lüften · Orange: Heizung",color=Color.White.copy(alpha=.72f),fontSize=9.sp)
    }
}
