# Thermo Lüftung V11.26

Android-Studio-Projekt auf **thermo-v11-20-test**. `main` bleibt unverändert.
Aktuelle Projektdatei: **Thermo-APP_V11.26_Tuya-Lokal-Blauer-Himmel.zip**.
[Projektdateien auf GitHub](downloads/README.md) · [Prüfbericht](docs/Pruefbericht_V11.26_Tuya-Lokal-Blauer-Himmel.md)

## Neu in V11.26

- Lesende Tuya-Integration über privaten Backend-Dienst; Access Secret bleibt außerhalb der APK.
- Optionaler lokaler Home-Assistant-Datenweg ohne Tuya-IoT-Core-Abo bei lokal unterstützten Geräten.
- Geräte → Einrichtung und Diagnose, bewusste DEMO/LIVE-Umschaltung, letzte Werte und Fehlerstatus.
- Alle 47 übermittelten Geräte zugeordnet, noch **nicht durch echte API-Antworten verifiziert**.
- [Einrichtung](backend/README.md) · [Architektur und offene Punkte](docs/Architektur_V11.26_Tuya-Lokal.md) · [Mapping-Tabelle](docs/Tuya_Geraetemapping_V11.26.csv)


Das Sonnenbild hat einen blauen, wolkenfreien Grundhimmel. Die fest eingebrannten
Wolken und ihre weißen Teichspiegelungen wurden aus diesem Bild entfernt.
**Die bereits vorbeiziehenden weißen Wolken sind unverändert**: gleiche Textur,
Farbe, Form, Zahl und Geschwindigkeit wie zuvor. Sie ziehen weiterhin einseitig
vorbei. Die übrigen Wetterszenen und der Animationscode sind unverändert.

Das Bild bleibt 864 × 1440 Pixel; Bildausschnitt und Partikelbudgets bleiben fest.
Exportdateien verwenden jetzt V11.26 im Namen. Alle Funktionen aus V11.25 sind enthalten.

## Enthalten aus V11.25

- Eigenes fotografisches Standardbild für das Bad; eigene Raumbilder bleiben möglich.
- Historie mit vollständiger Raumauswahl, Tag/Woche/Monat/Jahr und unabhängig
  auswählbaren Kurven für Temperatur, Feuchte und Taupunkt. Temperatur/Taupunkt
  teilen die °C-Skala; Feuchte nutzt eine eigene 0–100-%-Skala.
- Dauerhafte SQLite-Aufzeichnung angenommener Raum-Messwerte sowie Lüftungs-,
  Fenster-, Tür- und Heizungsereignisse. Kein nachträgliches Erfinden fehlender Daten.
- Excel-Export der aktuellen Auswahl: Messwerte, Ereignisse, Start-/Endklima soweit
  vorhanden, Gesamtdauer und Dauer innerhalb des Zeitraums sowie ein bearbeitbares
  Diagramm mit getrennten Achsen und Lüftungs-/Heizungsmarkern.
- Lüftungsdauer als konservative Schätzung aus Raum-/Außenklima, Temperaturdifferenz,
  Kellerwand und gegebenenfalls Querlüftung. Zu kalte Räume bekommen keine automatische
  Trocknungsempfehlung. Manuelle Timer bleiben möglich.
- Hintergrundtimer mit dauerhafter Android-Benachrichtigung, Countdown, Schließen-/
  Beenden-Aktion, Ablaufbenachrichtigung und Alarmton (maximal 60 Sekunden).
- Gemeinsamer Ereigniseingang für Fenster-/Türkontakte und Heizungszustände:
  erstes geöffnetes Fenster startet, weitere Fenster starten nicht neu; das letzte
  geschlossene Fenster beendet den Timer. Eine geöffnete Tür begrenzt die Restzeit
  bei Querlüftung auf maximal zwei Minuten. Automatik ist ohne geprüfte Geräte bislang nur als Demo testbar.
- Lokale Android-Klimabenachrichtigungen bei frischen echten Werten: mindestens
  70 % RH, zu kalter Raum (17 °C, Keller 14 °C), aktuell günstige Trocknung ab 60 % RH.
  Nur mit passenden Außen-/Kellerwandwerten wird Lüftung empfohlen. Wiederholungen
  gleicher Hinweise sind auf zwei Stunden begrenzt; neue Zustandswechsel melden sich erneut.
- Dunkles Farbschema für lesbare nicht ausgewählte Filter und Eingabefelder.

V11.25 enthält außerdem die bislang unveröffentlichten Änderungen V11.23/V11.24:
kleine einseitig ziehende Wolken, Entfeuchter-Empfehlung und Standort in Raumdetails
sowie die Raumampel und ihre Legende oberhalb der Stockwerkregister.

## Android Studio und Emulator

1. ZIP entpacken und den enthaltenen Projektordner in Android Studio öffnen.
2. Android SDK 35 installieren, JDK 17 oder neuer verwenden, Gradle synchronisieren.
3. Modul `app` im Emulator starten (Android 8.0/API 26 oder neuer).

Gradle 8.9 mit offiziellem SHA-256-Prüfwert, AGP 8.7.3, Kotlin 2.0.21,
Compose BOM 2024.12.01. App-Version **11.26**, versionCode **32**.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Die APK wird lokal unter `app/build/outputs/apk/debug/app-debug.apk` erzeugt;
bereitgestellt wird ausschließlich die Projekt-ZIP.

### Funktionen ohne echte Geräte prüfen

- **Historie → Demo-Vorschau** einschalten, Raum und Zeitraum wechseln und
  Kurven einzeln/zusammen aktivieren. Die erzeugten Beispieldaten werden nicht
  als echte Messreihe gespeichert. Der Export ist ausdrücklich `DEMO` benannt.
- **Start → Lüftung/Timer** oder **Raumdetail → Lüftungsberatung & Timer**:
  Benachrichtigungen erlauben, Dauer wählen, Timer starten, App verlassen.
  Nach Ablauf Fenster schließen und in App/Benachrichtigung bestätigen.
- **Sensortests öffnen · Demo**: Testklima speichern, Testfenster/Tür öffnen und
  schließen, Testheizung ein-/ausschalten. Diese Werte/Ereignisse werden dauerhaft
  mit Demo-Quelle gespeichert und erscheinen ohne generierte Demo-Vorschau in der Historie.
- Der manuelle Timer speichert bei bisher unverbundenen Räumen eine explizite
  Demo-Momentaufnahme der angezeigten Raumwerte. Fehlende Klimamessungen bleiben leer.
- **Raumdetail → Entfeuchter**: nach Umstellen Standort wählen und manuell „Läuft“
  oder „Gestoppt“ melden; anschließend Ampel in der Raumübersicht prüfen.
- **Einstellungen → Wetter-Szenen testen**: Sonnig/Bewölkt/Regen/Nacht und andere
  Szenen wählen; Tageszeit und Animationsschalter separat prüfen.

Der zusätzliche Gerätetest verwendet einen eigenen Instrumentation-Runner:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w de.thermo.lueftung.test/de.thermo.lueftung.HistoryRuntimeInstrumentation
```

Nur im Testemulator ausführen: SQLite-Kerntests nutzen eine isolierte Datenbank;
der anschließende Hintergrundtest erzeugt ausdrücklich markierte Demo-Ereignisse
im Raum „Bühne (Kinderzimmer 2)“ und spielt nach einer Minute den Alarmton ab.

## Grenzen echter Sensoren und Benachrichtigungen

**Midea und MQTT sind noch
nicht verbunden.** Die Tuya-/Home-Assistant-Adapter sind implementiert, aber echte
Geräte und DP-Schemas müssen noch über die Diagnose geprüft werden.
Ein vertrauenswürdiger Adapter kann kohärente Klimawerte an `ThermoRuntime.climate`
und Kontakt-/Heizungsereignisse an `ThermoRuntime.sensorEvent` liefern. Die neue Leseschicht liefert nach aktiver Verbindung verifizierte Daten; ohne
Konfiguration bleibt DEMO aktiv. Es gibt keine echte
Heizungs- oder Entfeuchtersteuerung; „Läuft“ ist eine manuelle Betriebsmeldung.

Benachrichtigungen werden **lokal auf Android** erzeugt, nicht von einem eingerichteten
Cloud-Pushdienst. Ohne fortlaufende echte Sensorversorgung sind automatische
Klimaalarme nicht möglich. Demo-Werte lösen keine echten Klimaempfehlungen aus.
Der periodische Android-Job prüft ungefähr alle 15 Minuten; Energiesparen kann ihn
verzögern. „Günstig“ bezeichnet den aktuellen Trocknungszustand, keine Vorhersage
des optimalen Zeitpunkts über einen ganzen Tag.

Android 13+ benötigt Benachrichtigungsfreigabe. Für pünktliche Timeralarme auch im
Ruhezustand benötigt Android 12+ die optionale Freigabe „Exakte Timeralarme“.
Ohne sie können Energiesparregeln den Ablaufalarm verzögern. Die App verwendet
Androids dauerhafte Benachrichtigung/Countdown; ein fremdes Vordergrundfenster
oder ein Samsung-Systemtimer wird nicht erzwungen. „Nicht stören“, stummgeschaltete
Kanäle und Herstellerregeln können Anzeige/Ton beeinflussen.

Laufende Timer werden gespeichert und bei App-Neustart wiederhergestellt. Innerhalb
desselben Geräteboots schützt eine monotone Zeitbasis vor Uhrzeitänderungen; nach
Geräteneustart wird die gespeicherte Uhrzeit verwendet. Nach Zwangsstopp muss die
App wieder geöffnet werden. Heizenergie wird nicht in kWh berechnet: dafür fehlen
Raum-/Gebäude- und Heizungsdaten. Die Kältegrenzen sind vorsichtige Standardwerte.

## Wetter, Wolken und Leistung

Wetterort: **74597 Stimpfach · Rechenberg**, näherungsweise 49,065° N / 10,145° E.
Open-Meteo liefert Raster-/Modellwetter, keine lokale Stationsmessung. Abruf beim
Start und alle 15 Minuten; Daten über 90 Minuten alt werden verworfen. Fehlende
Daten: **Demo · Wetterdaten fehlen**. Vorhersage, Gartenwerte und Luftqualität
bleiben ausdrücklich Demo. Der Cloud-Proxy blockiert den Live-Wetterdienst aktuell;
die genaue Ortskoordinate und Live-Antwort wurden hier nicht extern verifiziert.

Die unscharfe verschobene Kopie des fotografierten Himmels wurde entfernt.
Der Originalhimmel bleibt scharf; einzelne kleine Wolken ziehen mit unterschiedlichen
Abständen, Größen und Geschwindigkeiten gleichmäßig von links nach rechts. Sie
wechseln erst außerhalb des Bildes auf die andere Seite. Im Sonnenbild ist der
Grundhimmel jetzt wolkenfrei; nur die zusätzlichen weißen Wolken ziehen vorbei. Keine Richtungsumkehr im Bild.
Weiche Masken vermeiden die Wolkenkante am Wald und über Vordergrundblättern.

Teichstreifen, Wasserreflexe, Laubbereiche und Sonnenreflexe bewegen sich lokal;
nachts ergänzt durch warme Lampenreflexe, sanftes Flackern und zufällige funkelnde
Sterne. Regen/Schnee/Wolkendecke steuern Zahl und Deckkraft der Effekte.
Haus und Bildausschnitt bleiben fest. Höchstens 30 Aktualisierungen pro Sekunde,
begrenzte Partikelzahlen, reduzierte Last auf Low-RAM-Geräten, Pausen bei unsichtbarer/
inaktiver App und abschaltbare Animationen. Die Historie reduziert nur die Bildschirmkurve;
im Excel-Export bleiben alle ausgewählten gespeicherten Messpunkte enthalten.

## Entfeuchter und Raumampel

Standort und Betriebsmeldung sind gemeinsam gespeichert. Ein Standortwechsel setzt
den gemeldeten Betrieb zurück. Grün = hier manuell als laufend gemeldet; Rot =
Feuchte-/Wandklimabedarf; Gelb = Gerät steht hier, nicht als laufend gemeldet.
Grün hat Vorrang, danach Rot, danach Gelb. Ohne Standort und Bedarf keine Ampel.
Bei rotem Bedarf mit Gerät im Raum wird der Standort zusätzlich genannt.
Ohne aktuelle Wandmessung bleibt das Wandrisiko offen; Demoanalysen sind markiert.

## Cloud-Umgebung

Im isolierten Checkout `/workspace/Thermo-APP` arbeiten. `tools/setup-cloud.sh`
richtet verifizierte Werkzeuge unter `/workspace/toolchains` und Java-Proxyvertrauen
für den verwalteten Cloud-Build ein. Java 21 und Android SDK 35 sind geprüft.

```sh
bash tools/setup-cloud.sh
```

Emulatorprozesse überleben eine neue Cloud-Aufgabe nicht. Ohne `/dev/kvm` ist die
Softwareemulation langsam und dient Funktions-/Bildprüfungen, keiner belastbaren
Aussage über Flüssigkeit, Akku oder Speicher auf einem echten Handy.
Weitere Geräte-/Klimanotizen: [CLIMATE_NOTES.md](CLIMATE_NOTES.md).
