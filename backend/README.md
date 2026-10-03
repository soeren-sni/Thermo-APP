# Thermo V11.26 – Gerätedaten ohne Geheimnisse in der APK

Die App ist nicht zeitlich begrenzt. Nur der optionale Tuya-Cloud-Datenweg benötigt
aktive API-Berechtigungen. Es wurde kein kostenpflichtiger Tarif gebucht und kein
Backend veröffentlicht. Dieser Ordner enthält zwei **ausschließlich lesende** Adapter.
Der lokale Adapter benötigt kein Tuya-IoT-Core-Abo, sofern die Geräte tatsächlich
lokal über Home Assistant angebunden sind. Die normale Home-Assistant-Tuya-
Cloudintegration ist keine zugesicherte lokale, cloudunabhängige Anbindung.

## Empfohlener dauerhafter Weg: Home Assistant lokal

1. Home Assistant einrichten. Die aktuelle offizielle Raspberry-Pi-Anleitung
   empfiehlt Pi 4/5 mit mindestens 2 GB RAM. Beim vorhandenen Pi 3 sind Eignung,
   passendes 64-Bit-Abbild und Speicherreserve vorab zu prüfen; hier wurde kein
   Home Assistant auf Pi 3 installiert oder getestet. Keine Neukaufpflicht wird
   behauptet. Siehe https://www.home-assistant.io/installation/raspberrypi/.
2. Kompatiblen Zigbee-USB-Koordinator für ZHA oder Zigbee2MQTT auswählen. ZHA-
   Hardwareliste: https://www.home-assistant.io/integrations/zha/#compatible-hardware.
   Der vorhandene Tuya-Mini-Gateway wird nicht automatisch durch seine Cloud-ID
   zu einem lokalen Koordinator. Gerätemodelle/Verbindungstypen vor dem Kauf prüfen.
3. Zunächst einen T&H-Sensor und einen Kontakt lokal koppeln. Oft muss ein Gerät
   zurückgesetzt und vom alten Gateway getrennt werden. Dadurch können bestehende
   Tuya-Szenen ihre Verbindung verlieren. Nicht gleich alle Geräte umstellen.
4. In Home Assistant die tatsächlich erzeugten Entity IDs und Werte prüfen.
   Aus Tuya Device IDs lassen sich diese nicht zuverlässig ableiten. Für HY18,
   600-W-Heizung und CO-Melder ist lokale Unterstützung noch nicht bestätigt.
5. Eigenen Home-Assistant-Benutzer anlegen, dessen Token nur im Backend speichern.
   Ein HA-Token ist nicht automatisch auf Lesezugriffe beschränkt; dieser Dienst
   stellt nur GET-Abfragen bereit. Token bei Verlust im HA-Profil widerrufen.
6. `backend/ha_entities.example.json` nach `backend/private/ha_entities.json`
   kopieren, echte Entity IDs einsetzen. `localConfirmed` erst nach Prüfung der
   lokalen Integration auf `true` setzen. Fehlende/Cloud-Entities nicht als lokal
   ausgeben. `room` verwendet die IDs aus `devices.json`; `outside` bleibt separat.
7. Beispielkonfiguration nach `backend/private/config.json` kopieren. `HA_URL`
   und `HA_READ_TOKEN`, absoluten Pfad `HA_MAPPING_FILE`, eigenes zufälliges
   `THERMO_BRIDGE_TOKEN` mit mindestens 32 Zeichen eintragen. Das Lesetoken kann
   lokal mit `python -c "import secrets; print(secrets.token_urlsafe(32))"`
   erzeugt werden. Es ist kein Tuya-Secret.
8. Private Konfiguration schützen (`chmod 600 backend/private/config.json` unter
   Linux). Start mit Python 3.10 oder neuer: `python backend/run.py`.
9. HTTPS-Reverse-Proxy auf demselben Host einrichten, z.B. Caddy mit der enthaltenen
   LAN-Beispieldatei und lokalem DNS-Namen. Der Dienst bindet nur `127.0.0.1:8787`.
   Keine öffentliche Router-Portfreigabe ist nötig. Für Android-Studio-Debug-
   Builds lässt sich die eigene Caddy-CA bewusst als Benutzer-CA im Emulator
   installieren; Release-Builds vertrauen nur System-CAs. TLS/Hostname-Prüfung
   wird niemals abgeschaltet. Android-Emulator-`localhost` ist nicht der PC!
10. In Thermo unter **Geräte → Geräteverbindung einrichten** nur HTTPS-Adresse
    und separates Backend-Lesetoken eintragen. Verbindung speichern, **Verbindung
    & Geräte prüfen**, Diagnose ansehen, erst dann **LIVE** wählen.

Für Heizungen kann eine Mapping-Zuordnung Attribute einer `climate`-Entity lesen:

```json
{
  "id": "eigene-stabile-id",
  "name": "Heizung – Zuordnung prüfen",
  "room": "living",
  "role": "heater",
  "localConfirmed": false,
  "entities": {
    "temp_set": {"entity": "climate.ECHTE_ENTITY", "attribute": "temperature", "unit": "°C"},
    "temp_current": {"entity": "climate.ECHTE_ENTITY", "attribute": "current_temperature", "unit": "°C"},
    "work_state": {"entity": "climate.ECHTE_ENTITY", "attribute": "hvac_action"}
  }
}
```

Die Beispiele sind **keine erkannten Geräte**. Das HA-State-Zeitfeld
`last_reported`, ersatzweise `last_updated`, wird verwendet; Abrufzeit wird nicht
als Sensormesszeit erfunden. Die Aussagekraft des Zeitstempels hängt auch von der
gewählten lokalen Integration ab. Werte `unknown`/`unavailable` sperren Live-Werte.

## Optionaler Tuya-Weg für die Geräteprüfung

Vorhanden: Projekt **Heim**, **Central Europe Data Center**, 47 vom Nutzer
übermittelte IDs. Die sechsmonatige Verlängerung wurde beantragt; **eine Freigabe
wurde nicht nachgewiesen**. Flagship/Corporate sind keine hier empfohlene Anschaffung.
Eine weitere kostenlose Verlängerung oder künftige Preise können nicht zugesagt werden.

Im Tuya-Portal https://iot.tuya.com prüfen:

- IoT Core unter Cloud Services: Status aktiv, Laufzeit, Geräte-/API-Limits.
  Antrag auf Verlängerung genügt nicht; erst die Freigabe öffnet den Datenweg.
- Projektregion Central Europe und `https://openapi.tuyaeu.com`.
- Projekt-Geräte/App-Account-Verknüpfung; UID des verknüpften Smart-Life/Tuya-
  Benutzerkontos ermitteln. Nicht die UID aus der Cloud-Token-Antwort einsetzen.
- Unter Service API die für Geräteverwaltung, Spezifikationen und Shadow/
  Geräte-Eigenschaften erforderlichen Dienste autorisieren. Die genaue verfügbare
  Paketbezeichnung und Berechtigung muss im konkreten Projekt geprüft werden.
- Access ID und Access Secret ausschließlich am Backend konfigurieren.
  Keine Tokens, Secrets oder Local Keys in Chat/Git/APK eintragen.

Für diesen Datenweg in der privaten Konfiguration `THERMO_PROVIDER` auf `tuya`
setzen und `TUYA_ACCESS_ID`, `TUYA_ACCESS_SECRET`, `TUYA_ACCOUNT_UID` eintragen.
`THERMO_BRIDGE_TOKEN` ist ein getrenntes, widerrufbares Lesetoken für die App.
Der Backend-Prozess hält Tuya-Tokens nur im Arbeitsspeicher. Lokale Tuya-Schlüssel
werden verworfen und niemals in Antwort, CSV oder Log übernommen.

Verwendete APIs (ausschließlich GET):

- `/v1.0/token?grant_type=1`
- `/v1.0/users/{uid}/devices`
- `/v1.0/devices/{id}/specifications`
- `/v2.0/cloud/thing/{id}/shadow/properties`

Signierung entspricht dem öffentlich erreichbaren offiziellen
[Tuya Connector](https://github.com/tuya/tuya-connector-python/blob/master/tuya_connector/openapi.py).
Die klassischen Geräte-/Schema-Routen sind auch im offiziellen
[Tuya IoT SDK](https://github.com/tuya/tuya-iot-python-sdk/blob/main/tuya_iot/device.py)
enthalten. Kein SDK mit geheimen Cloud-Credentials wird in Android eingebettet.

**Dokumentationsgrenze am 3. Oktober 2026:** developer.tuya.com ist in dieser
Cloud-Umgebung mit HTTP 403 blockiert. Aktuelle Lizenzpakete, Shadow-Antwortschema
und tatsächliche Berechtigungen konnten hier deshalb nicht anhand einer aktuellen
Portal-Antwort bestätigt werden. Die Shadow-Route und ihr per-DP-Zeitfeld müssen
beim echten Verbindungsversuch geprüft werden. Fehlende Berechtigung oder fehlende
Messzeiten führen zu Diagnosewerten, nicht zu erfundenen frischen Live-Werten.

## Polling, Grenzen und Fehler

Die App fragt nur ab, wenn sie sichtbar ist, ungefähr alle fünf Minuten. Die
Tuya-Bridge cached mindestens fünf Minuten; lokale HA-Abfragen mindestens eine
Minute. Ein Durchlauf lädt die Geräteliste und bei relevanten Geräten die Shadow-
Eigenschaften; Schema-Abfragen werden im Backend-Prozess gecached. Keine
API-Abrufkostenfreiheit zugesagt. Bei 36 relevanten Geräten sind bis zu etwa 37
Tuya-Leseaufrufe pro normalem Durchlauf möglich; erstmalig kommen Schemas hinzu.
Offline-Geräte/Fehler werden nicht als Live-Klima angenommen. Keine aggressive
Wiederholung bei Rate Limit; Tokenfehler maximal einmal mit neuem Token versuchen.

HTTP-Fehler und Tuya-Fehlercodes werden in der App angezeigt; letzte gültige Werte
bleiben mit Zeitstempel erhalten. Bei fehlendem Backend/Lizenzen keine Umschaltung
auf erfundene Live-Werte. Für fehlende Messzeiten, unbekannte Enum-Werte oder
mehrdeutige IDs Diagnose verwenden. Kein Erraten von I/l/1-Zeichen.

Fenster- und Heizungsereignisse werden aus bestätigten frischen DP-Werten erfasst.
Der erste Abruf setzt einen Ausgangszustand und startet keinen rückwirkenden Timer.
Änderungen zwischen zwei Abfragen können verloren gehen. **Keine zuverlässige
Echtzeit-/Hintergrund-Fensterüberwachung und kein CO-Sicherheitssystem.** Später
lassen sich HA-Events/Tuya-Events in das Repository aufnehmen. Bestehende bereits
laufende Lüftungstimer bleiben über ihren Android-Dienst im Hintergrund aktiv.
Bühnenfenster wirken zusätzlich als Querlüftungskontakt im Kinderzimmer.

`switch` wird getrennt von tatsächlichem Heizen behandelt. Ownership-Modell
`AUTO / MANUAL_OVERRIDE / OFF` ist als Domain-Modell vorbereitet; aktive
Steuerung, automatische Wiederübernahme und Override-Persistenz sind bewusst
noch nicht implementiert. Es gibt keine schreibenden Tuya-/HA-Endpunkte.

CO-Werte bleiben ein eigener Kanal. Nur ein frisches, bestätigtes `alarm` löst
eine lokale Android-Warnung aus; `normal` ist kein Sicherheitsnachweis. Unbekannte
Enum-Werte und Offline-Zustände sind nicht sicher. Keine FCM-/Cloud-Push-Integration.

Tests: `python -m unittest discover -s tests/backend -v`.
