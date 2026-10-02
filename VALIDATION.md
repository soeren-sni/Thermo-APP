# Prüfstand V11.20 – 2. Oktober 2026

## Bestätigt

- Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Java 21; Android SDK/Target 35.
- Wiederholbarer Cloud-Aufbau mit `tools/setup-cloud.sh` erfolgreich.
- `assembleDebug`, `lintDebug`, `testDebugUnitTest` erfolgreich.
- 16 Unit-Tests ausgeführt: sieben Klima-/Taupunkt-/Wandklima-Fälle, vier
  Messwertspeicher-Fälle und fünf Entfeuchter-Priorisierungsfälle. Keine Fehler, keine übersprungenen Tests.
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

Seltene Gewitterblitze und Sternschnuppen sind implementiert; ihr vollständiger
zeitlicher Ablauf wurde nicht in einer durchgehenden Videoaufnahme bestätigt.
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
