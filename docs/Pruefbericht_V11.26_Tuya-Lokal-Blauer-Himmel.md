# Prüfbericht V11.26 – Tuya, lokale Daten und blauer Sonnenhimmel

3. Oktober 2026. Branch `thermo-v11-20-test`; `main` bleibt unverändert.
Projektdatei: `Thermo-APP_V11.26_Tuya-Lokal-Blauer-Himmel.zip`, keine separate APK.

## Ergebnis

Vorhandene App um lesende Geräte-Datenanbindung ergänzt. Tuya Cloud über sicheren
privaten Backend-Dienst, alternativ lokaler Home-Assistant-Adapter. Bestehende
Navigation, Raumansichten und Bilder werden nicht neu gestaltet. Die bereits
begonnene V11.26-Sonnenbildänderung mit blauem Grundhimmel ist enthalten; Wolken-
Textur und Animationscode bleiben unverändert.

Android enthält kein Tuya Access Secret und keinen Local Key. Backend-Zugang in
Android mit Keystore AES-GCM verschlüsselt und von Backups ausgeschlossen. Der
Lesedienst besitzt keine schreibenden Endpunkte und sendet keine Heizungsbefehle.
Bekannte 47 Geräte in `backend/devices.json`, noch ohne echte API-Verifikation.

## Tatsächlich durchgeführt

- Gradle-Konfiguration und vollständiger Debug-Build: `assembleDebug`,
  `assembleDebugAndroidTest`, `lintDebug`, `testDebugUnitTest` erfolgreich.
  Nach finaler kleiner Diagnose-Beschriftung nochmals Debug-Build/Lint/Unit-Tests.
  Android Studio selbst wurde in der Cloud nicht geöffnet; GUI-Gradle-Sync ist
  erst beim Öffnen des Projekts im lokalen Android Studio möglich.
- **49 JVM-Unit-Tests, 0 Fehler.** Bestehende Tests plus Heizungszustände,
  Kontakt-Enums, Offline/veraltete Daten, CO-Alarmregeln und Demo→Live-Wechsel.
- **29 Python-Backend-Tests, 0 Fehler.** Cloud-/HA-Fixtures, Rohwert-Scale,
  bereits skalierte HA-Werte, fehlende Messzeiten, Offline, Berechtigungsfehler,
  ID-Abweichung, Rate Limit, Token-Erneuerung, Mittelwert/Ausreißer, CSV,
  Backend-Authentifizierung und nicht vorhandene Befehlsrouten.
- Signierung zusätzlich mit der frisch von GitHub geladenen offiziellen
  Tuya-Connector-Implementierung verglichen: identische Signaturen für Token-
  und authentifizierte GET-Anfrage; ausschließlich synthetische Test-Credentials.
- Lint: **0 Fehler, 38 Warnungen**, u.a. bestehende veraltete Icon-/Material-APIs.
- APK-Signatur mit `apksigner verify --verbose` erfolgreich, v2.
- API-28-Softwareemulator: APK und Android-Test-APK installiert. Neue reine
  Tuya-JSON-Fixtures für T&H, echten Kontakt-DP und getrennte HY18-Power/Heat-
  Zustände erfolgreich geparst. Offline-Fixture wird nicht zum Live-Raumwert.
  Keystore-Verschlüsselung/Entschlüsselung und Ablehnung von HTTP-/Query-/kurzen
  Zugangsdaten bestanden. Diese Fixtures wurden nicht als Live-Geräte in die
  Nutzerhistorie veröffentlicht.
- Vollständiger vorhandener Instrumentation-Test bestanden: SQLite-Persistenz,
  Raum-/Zeitraumfilter, Ereignisüberlappung, doppelte/alte Kontakte, mehrere
  Fenster, Sensor-Timerstart/-stopp, Tür-/Heizungsereignisse, laufende Android-
  Benachrichtigung und Alarm nach 60 Sekunden im Hintergrund. Testereignisse
  bleiben ausdrücklich Demo.
- Startseite gestartet, Activity resumed bestätigt. Geräteseite mit DEMO,
  Einrichtung und Diagnose sichtbar. Auf dem Softwareemulator gab es beim
  Systemstart einen System-UI-ANR; nach „Wait“ war die Geräteseite bedienbar.
  Dies ist kein geprüfter Leistungsnachweis für ein echtes Smartphone.
- `git diff --check` und Python-Syntaxprüfung bestanden.

## Nicht durchgeführt / noch offen

**0 physische Geräte erkannt; 0 DP-Mappings anhand echter API-Antworten bestätigt.**
Keine gültigen Tuya-Zugangsdaten, keine freigegebene IoT-Core-Verlängerung und
keine laufende lokale Home-Assistant-Installation wurden bereitgestellt. Daher
kein echter Cloud-Authentifizierungs-/Live-/Heizungs-Lesetest und keine echte
Offline-Prüfung eines physischen Geräts. Kein Gerät wurde geschaltet.

Aktuelle Tuya-Dokumentation auf developer.tuya.com ist aus dieser Cloud mit
HTTP 403 blockiert. Lizenz-/Service-Pakete sowie Shadow-Route und Messzeitformat
müssen beim echten Portal/API-Test bestätigt werden. Keine aktuellen Preise oder
kostenfreie Dauerverlängerung zugesagt. Die auf dem Nutzerscreenshot gewählte
25.000-US-Dollar-Flagship-Edition ist keine Empfehlung dieses Projekts.

Home Assistant auf Pi 3, lokale Zigbee-Hardware und Produktkompatibilität wurden
nicht eingerichtet oder getestet. Die aktuelle offizielle Anleitung empfiehlt
Pi 4/5 ab 2 GB RAM. Lokaler Betrieb ohne IoT-Core-Abo ist nur bei tatsächlich lokal
unterstützten und gekoppelten Geräten möglich. Kein aktives Backend/HTTPS-
Deployment erfolgt; Beispielkonfiguration enthält keine echten Credentials.

Polling bei sichtbarer App alle fünf Minuten, keine kontinuierliche Hintergrund-
Geräteüberwachung. Zwischen Abfragen können Ereignisse verloren gehen. CO-Warnungen
sind keine verlässliche Sicherheitsüberwachung. Eigentumsmodell für spätere
Heizungssteuerung vorbereitet; aktive AUTO-Steuerung/Override-Persistenz offen.

## Architektur, Einrichtung und Mapping

- [Architektur und detaillierte Grenzen](Architektur_V11.26_Tuya-Lokal.md)
- [Sichere Backend-/Portal-Einrichtung](../backend/README.md)
- [Initiale Mapping-Tabelle – alle API-Verifikationen offen](Tuya_Geraetemapping_V11.26.csv)
- Nach echter Verbindung: authentifizierter Backend-Endpunkt `/v1/mapping.csv`
  für tatsächliche Product IDs, Categories und DP-Schemas.

## Geänderte Dateien

Android-Integration: `TuyaModels.kt`, `TuyaRepository.kt`,
`TuyaConnectionSettings.kt`, `TuyaSetupCard.kt`, `CoNotifications.kt`.
Anbindung an bestehende App: `MainActivity.kt`, `ThermoRuntime.kt`, `LiveRoom.kt`,
`RoomClimateStore.kt`, `RoomVentilation.kt`, `ClimateNotifications.kt`.
Sicherheit: `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`,
`network_security_config.xml`, `.gitignore`.
Backend: `backend/tuya_bridge.py`, `backend/home_assistant_bridge.py`,
`backend/run.py`, `backend/devices.json` und dokumentierte Beispielkonfigurationen.
Tests: `TuyaPolicyTest.kt`, `CoPolicyTest.kt`, `RoomClimateStoreTest.kt`,
`TuyaDeviceChecks.kt`, `HistoryRuntimeInstrumentation.kt`, `tests/backend/*`.
Bereits begonnene V11.26-Versionierung: `app/build.gradle.kts`,
`ExcelHistoryExport.kt`, `HistoryScreen.kt`, `ExcelHistoryExportTest.kt`,
`weather_portrait_sunny.webp`, V11.26-Demo-XLSX.
Dokumentation/Download: `README.md`, `VALIDATION.md`, `downloads/README.md`,
Architektur, Mapping und dieser Prüfbericht, versionierte Projekt-ZIP und SHA-256.
