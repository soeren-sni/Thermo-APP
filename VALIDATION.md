# Wetterüberarbeitung – 3. Oktober 2026

Diese Prüfung beschreibt den aktuellen Stand auf `thermo-v11-20-test`.
Die unten erhaltenen V11.21-Prüfungen sind historische Ergebnisse.

- Cloud-Setup `tools/setup-cloud.sh` vollständig ausgeführt und erfolgreich;
  Installation mit Prüfsummen/TLS-Verifikation, bestehender Checkout verwendet.
- Abschließender Build: `assembleDebug`, `lintDebug`, `testDebugUnitTest`
  erfolgreich. **22 Tests, null Fehler, null übersprungen.** Drei neue Wettertests
  prüfen Wettercodes/Nacht, skalierte Niederschlagszahlen und Datenalter.
- Lint: **null Fehler, 34 Warnungen**. Übernommene veraltete Icon-APIs bleiben.
- Abschließende APK mit `apksigner verify --verbose` erfolgreich geprüft (v2).
- API-28-Softwareemulator: erste Android-Systeminitialisierung zeigte System-UI-ANRs.
  Nach dem Start gelang `adb install --no-streaming -r`; Streaming-Installation
  lief ins Zeitlimit. App gestartet, Activity als resumed bestätigt, Startseite
  und Einstellungen sichtbar. Kein App-Fehler im erfassten AndroidRuntime-Log.
- Wetterort und **Demo · Wetterdaten fehlen** auf der Startseite sichtbar geprüft.
  Vorhersage/Garten/Außenluft/Luftqualität und Lüftungsberatung als Demo markiert.
  Manuelle Szenen zeigen **Demo · Wettertest**.
- Alle sechs Szenen in den Einstellungen bei aktivierten Animationen mit
  zeitversetzten Screenshots verglichen. Bildausschnitt und Hausgeometrie bleiben
  fest. Bewegung im Szenenbereich bestätigt: Sonnig 8220, Bewölkt 3504, Regen 4942,
  Gewitter 5760, Schnee 3749 und Nacht mit separat bestätigten Änderungen im
  Himmel/Laub/Wasser. Gezählt wurden Pixel mit mehr als 3 RGB-Stufen Differenz in
  einem 132 × 206-Pixel-Szenenausschnitt; dies ist **keine Bildratenmessung**.
- Sonnig: Änderungen in Laub (515 Pixel) und Wasser (1826 Pixel). Nacht: Änderungen
  in Himmel (669), Laub (352) und Wasser (335). Zusätzliche volle Nachtaufnahmen
  zeigen unregelmäßig verteilte Sterne und lokale Laubbewegung.
- Animationen ausgeschaltet: zwei zeitversetzte Vorschauaufnahmen sind im
  Szenenausschnitt **pixelgleich**. Tageszeit und statisches Bild bleiben erhalten.
- Screenshot-Artefakte dieser Cloud-Prüfung: `/workspace/thermo-validation`.

## Verbleibende Grenzen

Der echte Open-Meteo-Aufruf wurde vom Cloud-Proxy mit HTTP 403 blockiert. Die
Domains `api.open-meteo.com` und `geocoding-api.open-meteo.com` wurden als
Netzwerkentwurf gespeichert; ein gespeicherter Entwurf aktiviert den laufenden
Zugriff nicht. Live-Antwortformat, echte Ortsdaten und Ende-zu-Ende-Aktualisierung
sind deshalb noch nicht gegen den Wetterdienst geprüft. Die feste Ortskoordinate
49,065° N / 10,145° E ist eine Näherung für Rechenberg und extern nicht verifiziert.
Die App fällt sichtbar auf Demo zurück; manuelle Tests ersetzen keine Live-Prüfung.

Die Zeichnung ist weiterhin auf höchstens 30 Aktualisierungen pro Sekunde
begrenzt, pausiert bei unsichtbarer/inaktiver App und berücksichtigt systemweit
reduzierte Bewegung. Partikelbudgets: 96 Regen / 160 Schnee; Low-RAM 40 / 64.
Geräte-Bildrate, Akkuverbrauch und RAM unter Last müssen auf echter Hardware
geprüft werden; Softwareemulation ohne KVM liefert hierzu keinen belastbaren Wert.
Lampenflackern und Sonnenlichtmodulation sind implementiert; ihre vollständigen
zeitlichen Abläufe wurden nicht in einer durchgehenden Videoaufnahme bewertet.

Installationsskript und überprüfte Start-/Emulatoranweisungen wurden im
Cloud-Konfigurationsentwurf gespeichert. Veröffentlichung bzw. Wiederherstellung
in einer neuen Cloud-Aufgabe sind nicht bestätigt. `main` wird nicht aktualisiert.

---

# Prüfstand V11.21 – 3. Oktober 2026

## Bestätigt

- Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Java 21; Android SDK/Target 35.
- Wiederholbarer Cloud-Aufbau mit `tools/setup-cloud.sh` erfolgreich.
- `assembleDebug`, `lintDebug`, `testDebugUnitTest` erfolgreich.
- 19 Unit-Tests ausgeführt: sieben Klima-/Taupunkt-/Wandklima-Fälle, vier
  Messwertspeicher-Fälle, fünf Entfeuchter-Priorisierungsfälle und drei
  Tageszeit-Fälle (Regen/Schnee über Mitternacht, feste Nacht, gültige Uhrzeit). Keine Fehler, keine übersprungenen Tests.
- Lint: null Fehler, 35 Warnungen, unter anderem übernommene ungenutzte bzw.
  doppelte Bildressourcen. Kotlin meldet übernommene veraltete Icon-APIs.
- APK-Signatur mit `apksigner verify` geprüft (Debug-Schlüssel, v2).
- API-28-AOSP-Emulator gestartet, APK installiert, Startseite sichtbar.
- Alle sechs Wetterszenen auf der Startseite aufgenommen und visuell verglichen:
  Haus-/Garten-/Teichgeometrie bleibt konsistent. Aufnahmen zu unterschiedlichen
  Zeitpunkten zeigen bewegte Pixel für alle sechs Szenen, einschließlich
  nächtlicher Wasserreflexionen. Keine Aussage über eine garantierte Bildrate.
- Raumübersicht, Kellerfilter und Hobby/Fitness-Raumdetail sichtbar geprüft.
- Eigene Bilddatei über Android-Dokumentauswahl ausgewählt, im Raum-Thumbnail
  angezeigt und URI-Zugriff in den App-Einstellungen gespeichert; nach
  App-Neustart und APK-Update weiterhin vorhanden.
- Manuelle Entfeuchter-Zuordnung zu Werkstatt in der Oberfläche ausgewählt;
  Standortpräferenz gespeichert und nach App-Neustart weiterhin vorhanden.
- Ohne Wandtemperatur kein aw-Wert; mit 10 °C Wandtemperatur im Demo-Hobbyraum
  (17 °C / 51 % RH) aw ungefähr 0,80 und Hinweis auf längerfristiges Risiko.
- Sechs unterschiedliche Wetterassets, 864 × 1440, vollständig mit `Fit` angezeigt;
  keine Hintergrundtransformation. 13 Raum-IDs und vier vorhandene
  Stockwerkbilder erhalten, letztere bytegleich zum Upload.

## Grenzen und nächste Prüfungen

Die Cloud verfügt nicht über `/dev/kvm`. Der API-35-Emulator zeigte Fehler der
Android-Systemdienste und ANR-Dialoge; ein stabiler API-35-Lauf ist nicht bestätigt.
Der API-28-AOSP-Emulator erlaubt funktionale Bildprüfung, läuft aber langsam.
UiAutomator lieferte zeitweise keinen Accessibility-Root; Navigation wurde deshalb
zusätzlich über direkte Eingaben und anschließend kontrollierte Screenshots geprüft.

Gewitterblitze alle etwa 6–14 Sekunden und drei Sternschnuppen je Minute sind
implementiert. Blitzimpulse wurden in einer 32-Sekunden-Aufnahme im Emulator
bestätigt. Der vollständige zeitliche Ablauf aller Sternschnuppen bleibt ohne
durchgehende Videoaufnahme; zeitversetzte Aufnahmen bestätigen Änderungen im
Nachthimmel.
Flüssigkeit, Akkunutzung, echtes Sensor-/Gateway-Verhalten und Reaktionszeiten
müssen auf einem realen, möglichst schwächeren Android-Gerät gemessen werden.

Sensoren, Außenwetter und Gerätezustände sind weiterhin Demo bzw. unverbunden.
Keine automatische Steuerung von Tuya-Heizungen, Midea oder Anker implementiert.
NetHome-/Tuya-/Anker-Schnittstellen und Raumzuordnungen sind der nächste Schritt.
Der aw-Wert ist eine berechnete Schätzung für das Oberflächenklima; ohne gemessene
Wandtemperatur lässt er sich aus Raumdaten allein nicht bestimmen.

Cloud-Konfiguration: vollständiges Installationsskript und Startanweisungen als
Entwurf gespeichert; keine Veröffentlichung und kein Nachweis einer Wiederherstellung
in einer neuen Cloud-Aufgabe. Bereitstellung dieser Testversion auf dem separaten
GitHub-Branch `thermo-v11-20-test`; `main` wird dafür nicht verändert.

## V11.21

Die Einstellungen wurden im API-28-Emulator geprüft: kleine Hochformat-Vorschau
ist vollständig sichtbar, Tageszeit-Testwahl steht oberhalb, Temperaturziele
folgen darunter. Die Uhrzeit-Auswertung wurde durch drei neue Tests geprüft;
sie ist eine lokale Uhrzeit-Näherung und keine astronomische Standortberechnung.

Zeitversetzte Aufnahmen der V11.21-Vorschau zeigen bewegte Pixel bei Bewölkt,
Sonnig und Nacht. Der Schnee-Lichtvergleich wurde im Emulator geprüft:
die mittlere Bildhelligkeit sank in der Nachtansicht um ungefähr 40 %.
Die Leistungsprüfung auf dem S25 Ultra bleibt ausstehend.
