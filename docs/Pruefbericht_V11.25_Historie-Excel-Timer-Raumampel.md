# Prüfbericht V11.25 – Historie, Excel, Timer und Raumampel

3. Oktober 2026 · Branch `thermo-v11-20-test` · versionCode 31.
Die internen Schritte V11.23/V11.24 sind enthalten; keine separaten ZIPs dafür.

## Build und automatische Prüfungen

- Java 21, Gradle 8.9, Android SDK 35, AGP 8.7.3, Kotlin 2.0.21.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`, `testDebugUnitTest`
  erfolgreich. **38 Unit-Tests, null Fehler, null übersprungen.**
- Lint: **null Fehler, 38 Warnungen**. Vorhandene Ressourcen-/Icon-/Exif-Warnungen;
  neue Warnungen zu API-Konstanten und statischem Anwendungskontext. Der Kontext
  ist ausdrücklich `applicationContext`, kein Activity-Kontext. Android-13-
  Berechtigungsabfragen sind im Verhalten nach API/erteilter Freigabe begrenzt.
- Neue Tests prüfen Kältegrenzen, feuchte Außenluft, Kellerwandvoraussetzung,
  kurze/Querlüftungsdauer, Kalenderzeiträume, monotone Timerzeit, Klimahinweise,
  angenommene Messpakete, Export-XML, Kurvenauswahl, fehlende Daten und Demo-Quelle.
- Native XLSX mit `openpyxl` unabhängig geöffnet: drei Arbeitsblätter,
  ein bearbeitbares Diagramm mit zwei Achsgruppen, alle drei Kurven,
  Lüftungsstart/-ende und 300 Sekunden Dauer im Beispieldatensatz.
  Das ist eine Format-/Strukturprüfung, keine Sichtprüfung in Microsoft Excel.
- `git diff --check` erfolgreich. Nur der bestehende Testbranch wird aktualisiert.

## Gerätetest: API-28-Softwareemulator

Der projektspezifische Instrumentation-Runner wurde auf dem Emulator ausgeführt:

- SQLite speichert Messwerte/Ereignisse über Schließen/Öffnen der Datenbank hinweg.
- Raum- und Zeitfilter, Ereignisüberlappung, Start-/Endklima und gespeicherte
  monotone Frist geprüft; doppelte Messungen werden nicht doppelt gespeichert.
- Doppelte/verspätete Kontaktpakete ändern den neueren Zustand nicht rückwärts.
  Auch ein gleichbleibendes neueres Paket aktualisiert seine Zeitgrenze.
- Ein Testfenster startet den Timer. Ein zweites startet ihn nicht erneut;
  Schließen des ersten beendet ihn nicht, Schließen des letzten schon.
- Testtür und Testheizung liefern gespeicherte Start-/Endereignisse.
- Einminütiger Timer ohne geöffneten App-Bildschirm: laufender Foreground-Service
  und dauerhafte Benachrichtigung nachgewiesen; Ablaufalarm und anschließendes
  Beenden nachgewiesen. Die tatsächliche Laufzeit des ersten Testlaufs betrug
  etwa 64 Sekunden einschließlich Emulator-/Dienstverarbeitung.
- Manuelle Test-Klimabenachrichtigung ausdrücklich Demo; gemischtes Sensor-/
  Demo-Endklima bleibt als Demo markiert.
- Runtime-Export mit den erzeugten Ereignissen ebenfalls unabhängig gelesen.
  Daten stammen ausschließlich aus Tests, nicht aus einer realen Geräteanbindung.

## Sichtprüfung

- Neues generisches fotografisches Bad-Standardbild in Raumdetails sichtbar.
- Legende mit Grün/Gelb/Rot oberhalb der Stockwerkregister sichtbar.
- Historienmenü zeigt alle 13 vollständigen Raumnamen ohne rechte Abschneidung.
- Drei Kurven gemeinsam sichtbar; nach Abschalten von Temperatur/Taupunkt nur
  die Feuchtekurve. Wechsel Tag → Woche ändert Zeitraum/Achsenbeschriftung.
- Raumwechsel zu „Bühne (Kinderzimmer 2)“ zeigt dessen gespeicherte Testdaten,
  keine weiter angezeigte generierte Demo aus einem anderen Raum.
- Das anfängliche Kontrastproblem nicht ausgewählter Filter wurde durch ein
  dunkles Material-Farbschema mit hellen Beschriftungen korrigiert.
- Die Wolkenkorrektur wurde zuvor mit Videos für Sonnig, Bewölkt und Regen
  geprüft: Originalhimmel scharf, kleine Wolken einseitig ziehend, kein kopierter
  schwimmender Himmel. Siehe [V11.23](Pruefbericht_V11.23_Kleine-ziehende-Wolken.md).

## Projekt und Daten

Projektdatei: `Thermo-APP_V11.25_Historie-Excel-Timer-Raumampel.zip`.
Versioniertes Beispielexport: `beispiele/Thermo_V11.25_Bad_Historie-Excel_DEMO.xlsx`.
Exportdateien aus der App enthalten Version, Historie, Raum, Zeitraum und bei
Demo-/Testdaten `DEMO`. UTC-Zeitwerte ergänzen die lokalen Berliner Zeiten.

## Grenzen

- Echte Tuya-/Smart-Life-/Home-Assistant-/MQTT-Sensoren, Fensterkontakte und
  Heizungszustände sind noch nicht angebunden. Schnittstelle/Gerätezuordnung
  wurden beim Nutzer angefragt. Der Ereigniseingang und Demo-Tests ersetzen
  keine Live-Integration; keine echten Gerätebefehle.
- Klimabenachrichtigungen sind lokale Android-Benachrichtigungen, kein eingerichteter
  Cloud-Pushdienst. Nur frische echte Messungen führen zu automatischen Hinweisen.
  Demo-Vorschau/Testdaten sind eindeutig markiert.
- Android-13-Benachrichtigungsfreigabe und Android-12-Freigabe exakter Alarme sowie
  Hersteller-Energiesparregeln wurden auf API 28 nicht Ende zu Ende geprüft.
  Sie müssen im gewünschten aktuellen Emulator und auf dem Handy geprüft werden.
- Keine mathematische Heizkosten-/kWh-Berechnung oder Vorhersage des besten
  Lüftungszeitpunkts für einen ganzen Tag. Konservative Temperaturgrenzen und
  aktuell passende Trocknungsbedingungen bestimmen die Schätzung.
- Open-Meteo wurde vom Cloud-Proxy mit HTTP 403 blockiert. Fehlende Wetterdaten
  bleiben sichtbar Demo; Näherungskoordinate für Rechenberg extern nicht verifiziert.
- Kein KVM: Softwareemulator liefert keine belastbare Leistung-/Akkuprüfung.
  UIAutomator meldete gelegentlich keinen Root; zusätzliche Screenshots/direkte
  Eingaben wurden verwendet. Ein vorhandener System-UI-ANR ist kein App-Absturz.
- Nicht jedes Gerät/Benachrichtigungslimit und nicht jeder vollständige
  zeitliche Animationsablauf wurde auf echter Hardware geprüft.

Cloud-Prüfarbeitsdateien: `/workspace/thermo-validation/V11.25_Historie-Excel-Lueftungstimer`.
