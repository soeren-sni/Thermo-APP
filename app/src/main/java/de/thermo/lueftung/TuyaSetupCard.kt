package de.thermo.lueftung

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun TuyaSetupCard(vm:ThermoViewModel) {
    val repo=vm.tuya
    val state by repo.state.collectAsStateWithLifecycle()
    val source by repo.source.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(repo.settings.url) }
    var token by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp)) {
        Text("Gerätedaten · Tuya / Home Assistant",color=Color.White,fontWeight=FontWeight.Bold)
        Text("Quelle: ${if(source==ClimateSource.DEMO) "DEMO" else state.provider}",color=Color(0xFF2CC7E8),fontSize=12.sp)
        Text(state.error ?: if(state.fetchedAt>0) "${state.devices.count { it.verified }} Zuordnungen bestätigt · ${state.accountDeviceCount} Geräte im Konto · Abfrage ${measurementTime(state.fetchedAt)}" else "Noch keine API-Verbindung geprüft",color=Color.White.copy(alpha=.75f),fontSize=11.sp)
        TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Einrichtung schließen" else "Geräteverbindung einrichten") }
        if(expanded) {
            Text("Lokaler Betrieb: Home Assistant mit lokal gekoppelten Geräten und den mitgelieferten Backend-Adapter einrichten. Benötigt kein Tuya-IoT-Core-Abo.\nTuya-Cloud alternativ: 1. IoT-Core-Verlängerung freigeben lassen. Im Projekt Heim den verknüpften App-Account und die API-Berechtigungen prüfen.\n2. Mitgelieferten Backend-Dienst auf eigenem Rechner/Server einrichten. Access ID und Access Secret ausschließlich dort speichern.\n3. Hier HTTPS-Adresse und separates Backend-Lesetoken eingeben. Keine Tuya-Secrets hier eingeben.",color=Color.White,fontSize=11.sp)
            OutlinedTextField(url,{url=it},label={Text("HTTPS-Backend-Adresse")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(token,{token=it},label={Text("Backend-Lesetoken")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(enabled=!busy,onClick={
                try { repo.settings.save(url,token);repo.useDemo();token="";message="Verbindung gespeichert. Jetzt Geräte prüfen." }
                catch(_:Exception) { message="HTTPS-Adresse ohne Pfad und Lesetoken mit mindestens 32 Zeichen erforderlich." }
            }) { Text("Verbindung speichern") }
            Text("Das Lesetoken wird mit Android Keystore verschlüsselt. Der Tuya Access Secret bleibt auf dem Backend. Details: backend/README.md im Projekt.",color=Color.White.copy(alpha=.7f),fontSize=10.sp)
        }
        Button(enabled=!busy && repo.settings.url.isNotBlank(),onClick={scope.launch { busy=true;try { repo.refresh();message=if(repo.state.value.error==null) "API-Ergebnis geladen. Geräte und Messzeiten in Diagnose prüfen." else "API-Prüfung fehlgeschlagen." } finally { busy=false } }}) { Text(if(busy) "Tuya wird geprüft …" else "Verbindung & Geräte prüfen") }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            FilterChip(selected=source==ClimateSource.DEMO,onClick={repo.useDemo()},label={Text("DEMO")})
            FilterChip(selected=source==ClimateSource.TUYA_LIVE,onClick={if(!repo.enableLive()) message="Noch keine frische, API-verifizierte Klimamessung. Zuerst Diagnose prüfen."},label={Text("LIVE")})
        }
        Text(message,color=Color.White,fontSize=11.sp)
        Text("Live-Abfrage nur bei sichtbarer App, frühestens alle 5 Minuten. Keine Heizungsbefehle. CO-Anzeige ersetzt keinen zertifizierten Warnmelder.",color=Color.White.copy(alpha=.7f),fontSize=10.sp)
        TextButton(onClick={diagnostic=!diagnostic}) { Text(if(diagnostic) "Diagnose schließen" else "Gerätediagnose · ${state.devices.size} Einträge") }
        if(diagnostic) {
            state.devices.forEach { d->
                HorizontalDivider(Modifier.padding(vertical=6.dp))
                Text(d.name,color=Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp)
                Text("${d.room ?: "Diagnose"} · ${d.id}\nProdukt ${d.productId ?: "?"} · Kategorie ${d.category ?: "?"}\n${if(d.verified) "Zuordnung API-bestätigt" else "ID nicht verifiziert"} · ${if(d.online) "online" else "offline/unbekannt"}\nLetzte Messung: ${d.lastContact?.let(::measurementTime) ?: "unbekannt"}",color=Color.White.copy(alpha=.75f),fontSize=10.sp)
                d.error?.let { Text(it,color=Color(0xFFFFD34D),fontSize=10.sp) }
                if(d.role=="heater") {
                    val h=d.heating()
                    Text("Strom: ${h.devicePower ?: "unbekannt"} · heizt tatsächlich: ${h.isActivelyHeating ?: "unbekannt"}\nSoll ${h.targetTemperature ?: "?"} °C · Thermostat ${h.currentTemperature ?: "?"} °C\nSteuerung OFF · keine Schaltbefehle",color=Color.White,fontSize=10.sp)
                }
                d.points.forEach { p->Text("${p.code}: ${p.value ?: "fehlt"} → ${p.normalized ?: "unbestätigt"}\n${p.type ?: "?"} · ${p.unit ?: "—"} · Scale ${p.scale ?: "?"} · R ${p.read ?: "?"}/W ${p.write ?: "?"}\nMesszeit ${p.at?.let(::measurementTime) ?: "unbekannt"}",color=Color.White.copy(alpha=.75f),fontSize=10.sp) }
            }
        }
        TextButton(onClick={repo.useDemo();repo.settings.clear();url="";token="";message="Verbindung entfernt."}) { Text("Verbindung entfernen") }
    }
}

@Composable
fun RoomDataSourceStatus(room:Room,vm:ThermoViewModel) {
    val state by vm.tuya.state.collectAsStateWithLifecycle()
    val mode by vm.tuya.source.collectAsStateWithLifecycle()
    if(mode==ClimateSource.TUYA_LIVE) {
        val devices=state.devices.filter { it.room==room.id && it.role=="climate" }
        val text=when {
            state.error!=null->"Geräteverbindung gestört · letzter Wert / Demo gekennzeichnet"
            devices.isEmpty()->if(room.isDemo) "Kein Live-Klimasensor zugeordnet · Demo" else "Live-Zuordnung noch nicht geprüft · letzter Sensorwert"
            devices.any { !it.online || it.error!=null }->"Sensor offline / Messung unbestätigt · letzter Wert oder Demo"
            room.isDemo->"Keine bestätigte Live-Messung · Demo"
            !TuyaPolicy.fresh(room.measuredAtMillis,vm.dryingEvaluatedAtMillis)->"LIVE · Messung veraltet"
            else->"LIVE · Sensor ${room.measuredAtMillis?.let(::measurementTime)}"
        }
        Text(if(text.startsWith("LIVE")) "${state.provider} · $text" else text,color=if(text.startsWith("LIVE · Sensor")) Color(0xFF61D39A) else Color(0xFFFFD34D),fontSize=10.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
    }
}
