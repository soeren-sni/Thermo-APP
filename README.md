# Thermo Lüftung V11.21

Weiterentwicklung des vorhandenen Android-Studio-Projekts aus
`ThermoLueftungsApp_V11_19_Sechs_Wetter_Portrait_Richtig.zip`.

## Oberfläche

- Sechs eigenständige Wetterszenen: Sonnig, Bewölkt, Regen, Gewitter, Schnee und Nacht.
- Gemeinsame Haus-/Garten-/Teichkomposition nach dem bereitgestellten Referenzbild.
- Wetterbilder: WebP, 864 × 1440 Pixel, Seitenverhältnis 3:5, `drawable-nodpi`.
- Die Startseite zeigt die Szene vollständig mit `ContentScale.Fit`. Auf kleineren
  Displays ist der Inhalt scrollbar; die Navigation bleibt erreichbar.
- Glasflächen für Außenwetter, Lüftung, Garten/Außenluft/Luftqualität und Vorhersage.
- Wetterumschaltung: **Einstellungen → Wetter-Szenen testen → Wetter auswählen → Start**.
- Feste Hintergrundbilder mit separater Animation: Licht/Wasser, transparente
  Wolkentextur, Regen mit Tiefenstaffelung und Teichringen, seltene Gewitterblitze,
  taumelnder Schnee und dezente nächtliche Reflexionen/Sternschnuppe.
- Die Animation zeichnet mit maximal 30 Bildern pro Sekunde innerhalb der Canvas, pausiert außerhalb des sichtbaren
  Bereichs sowie bei nicht aktiver App und verwendet höchstens 96 Regenpartikel
  bzw. 160 Schneeflocken; auf Low-RAM-Geräten höchstens 40 Regenpartikel bzw. 64 Schneeflocken. Wetteranimationen lassen sich in den Einstellungen abschalten. Gewitterblitze folgen zufällig nach 6–14 Sekunden.
- Die 13 Räume und die vorhandenen Stockwerkbilder sind erhalten. Die Raumliste lädt Einträge bedarfsgerecht über `LazyColumn`.
- Eigene Raumbilder werden in Übersicht, Raumdetail und Einstellungen angezeigt.
  Dokumentzugriffe bleiben gespeichert; Bilder werden im Hintergrund verkleinert
  geladen, Fotoorientierung berücksichtigt und bei fehlendem Zugriff durch das
  Standardbild ersetzt. **Demo-Bild** setzt die Auswahl zurück.

**Raummesswerte, Vorhersage, Gartenwerte, Luftqualität, Lüftungsberatung,
Gerätezustände und Automationsanzeigen bleiben Demo-Daten.** Das aktuelle Wetter
wird separat aus Open-Meteo-Modellwerten abgerufen und als solches gekennzeichnet.
Ohne aktuelle Daten steht ausdrücklich „Demo · Wetterdaten fehlen“ in der Wetterkarte.
Tuya, echte Fensterkontakte, Gerätebefehle und automatische Lüftungs-/Heizungslogik
sind noch nicht integriert.

## Android Studio

1. Diesen vorhandenen Projektordner öffnen.
2. Android SDK 35 installieren und Gradle synchronisieren.
3. JDK 17 oder neuer verwenden (Cloud-Build geprüft mit Java 21).
4. Modul `app` starten. Android 8.0/API 26 oder neuer ist erforderlich.

Der Gradle Wrapper ist enthalten und verwendet Gradle 8.9 mit dem offiziellen
SHA-256-Prüfwert. AGP 8.7.3, Kotlin/Compose-Plugin 2.0.21 und Compose BOM
2024.12.01 entsprechen dem übernommenen Projekt.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Die Test-APK liegt anschließend unter `app/build/outputs/apk/debug/app-debug.apk`.

## Cloud-Umgebung

Im bestehenden isolierten Checkout `/workspace/Thermo-APP` arbeiten; nur auf
ausdrücklichen Wunsch einen Git Worktree erstellen.

`tools/setup-cloud.sh` richtet die Werkzeuge unter `/workspace/toolchains` ein,
verifiziert Downloads, aktiviert den Cloud-Proxy für Java-Werkzeuge und führt Build
Lint und Unit-Tests aus. Quelltexte werden durch das Setup-Skript nicht verändert.

```sh
cd /workspace/Thermo-APP
./tools/setup-cloud.sh
```

Emulatorprozesse überleben eine neue Cloud-Aufgabe nicht. Die zugehörigen
Startanweisungen sind in der Cloud-Umgebung gespeichert. Ohne `/dev/kvm` dauert der
Android-Start in dieser Maschine erheblich länger. Softwareemulation dient der
Funktions- und Bildprüfung; Flüssigkeit, Akkunutzung und Speicherverhalten müssen
zusätzlich auf einem echten schwächeren Android-Gerät beurteilt werden.

## Vorbereitung für Messwerte

`RoomClimateStore` nimmt kohärente Raum-Messwerte mit Zeitstempel ereignisgesteuert entgegen. `StateFlow` aktualisiert sichtbare Raumanzeigen unabhängig von Wetteranimation und Timer. Verzögerte Pakete ersetzen keine neueren Messungen. Die Geräteanbindung und historische Speicherung sind noch nicht implementiert; die aktuellen Anzeigen bleiben Demo-Werte, bis ein Adapter echte Messwerte liefert.

## Taupunkt, aw und Trocknung

Die Raumansicht enthält eine aw-Schätzung nach manueller Eingabe der gemessenen Wandtemperatur und eine taupunktbezogene Lüftungsberatung. Außenwerte bleiben Demo-Werte. Die Beratung berücksichtigt kalte Kellerwände und trennt IR-Heizung von echter Wasserentfernung durch Lüften oder Entfeuchten. Geräteausstattung und nächste Integrationsschritte: [CLIMATE_NOTES.md](CLIMATE_NOTES.md).

## Mobiler Entfeuchter

Unter **Geräte** vergleicht die App die drei getrennten Kellerräume und empfiehlt bei erhöhtem Feuchtebedarf einen Einsatzort. Ohne Wandmessung wird allein die Luftfeuchte bewertet und kein aw erfunden. Die Analyse ist bis zur Sensoranbindung als Demo gekennzeichnet. Der aktuelle Standort des Midea lässt sich manuell wählen und wird gespeichert. Empfehlungen verschieben die Standortzuordnung nicht automatisch.

## Änderungen V11.21

- Deutlichere bewegte Wolkentexturen und Sonnenreflexionen bei festem Hintergrund.
- Funkelnde Sterne und drei gelegentliche Sternschnuppen je Minute im klaren Nachthimmel.
- Gewitterblitze etwa alle 6–14 Sekunden.
- Licht nach Geräte-Uhrzeit: Nacht 21–6 Uhr, Dämmerung 6–9/18–21 Uhr, sonst Tag.
  Regen/Gewitter tagsüber heller, Wetter nachts dunkler; klare Nächte verwenden
  das Nachtbild. Dies ist eine Uhrzeit-Näherung, keine standortbezogene Berechnung
  von Sonnenauf- und Sonnenuntergang.
- Einstellungen zeigen eine vollständige, kompakte animierte Hochformat-Vorschau
  vor den Temperaturzielen. Auto/Mittag/Abend/Nacht erlauben den Lichtvergleich.
- Der Animationsschalter und die systemweite Bewegungsreduzierung bleiben wirksam.

## Wetterüberarbeitung auf dem Testbranch

- Wetterort: **74597 Stimpfach, Ortsteil Rechenberg**; näherungsweise Ortskoordinate
  49,065° N / 10,145° E. Open-Meteo liefert Raster-/Modellwerte, keine lokale
  Stationsmessung. Die genaue Ortskoordinate wurde in dieser Cloud nicht extern verifiziert.
- Aktuelles Wetter wird beim Start und alle 15 Minuten im Hintergrund geladen.
  Daten über 90 Minuten alt oder über 5 Minuten in der Zukunft werden verworfen.
  Ohne nutzbare Daten verwendet die Szene gekennzeichnetes Demo-Wetter.
- Regen einschließlich Schauern (mm), Schneefall (cm) und Wolkendecke (%) steuern
  Partikelzahl und Wolkenopazität mit begrenztem Aufwand. Null Niederschlag zeichnet
  keine Niederschlagspartikel. WMO-Wettercodes wählen die passende Grundszene.
- Einstellungen: manuelle Auswahl ist „Demo · Wettertest“; **Aktuelles Wetter
  verwenden** beendet den Testmodus. Tageszeit bleibt separat prüfbar; Auto verwendet
  die Uhrzeit in Europe/Berlin.
- Wolken laufen weich aus; keine rechteckige Kante am Wald. Gelegentliche
  Schleierwolken und pulsierendes, gefiltertes Sonnenlicht ergänzen die Szene.
- Kleine Laubbereiche bewegen sich lokal, Sonnenreflexe wandern im Laub,
  Teichreflexe sind deutlicher. Haus und Bildausschnitt bleiben fest.
- Nacht: sanftes Lampenflackern, unabhängig zufällig verteilte Sterne mit
  sichtbarem Funkeln. Dichterer, nach Niederschlagsdaten skalierter Schnee.
- Internetberechtigung für HTTPS-Wetterabruf hinzugefügt. Die Cloud benötigt
  Zugriff auf `api.open-meteo.com`; ein gespeicherter Netzwerkentwurf allein
  schaltet den laufenden Cloud-Zugriff noch nicht frei.
