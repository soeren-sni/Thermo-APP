# V11.23 – Kleine ziehende Wolken

3. Oktober 2026; Branch `thermo-v11-20-test`.
VersionName 11.23, versionCode 29.

## Änderung

Die versetzte Kopie des gesamten Himmels aus V11.22 ist entfernt. Sie wurde über
den unveränderten Hintergrund geblendet und erzeugte dadurch doppelte Wolken
und eine verschwommene, hin- und herschwimmende Bewegung. Der fotografierte
Originalhimmel bleibt jetzt scharf und unbewegt.

Zusätzliche kleine Wolkentexturen ziehen ausschließlich von links nach rechts.
Größe, Höhe, Startabstand und Geschwindigkeit unterscheiden sich. Die Bewegung
ist linear und verwendet keine Sinus-Richtungsumkehr. Beim Umlauf liegt die
Wolke vollständig außerhalb des Bildes; innerhalb des Bildes gibt es keinen
Positionssprung. Subpixel-Verschiebung vermeidet Schritte von ganzen Pixeln.

Wolkenzahl und Deckkraft richten sich nach Wolkenbedeckung und Gerätebudget.
Die bisherige weiche Maske am Wald und vor dem Laub bleibt erhalten. Wasser,
Laub, Sonnen-/Lampenreflexe und Sterne sind gegenüber V11.22 unverändert.

## Build und Tests

- Abschließender `assembleDebug`, `lintDebug`, `testDebugUnitTest`: erfolgreich.
- 22 Tests, null Fehler und null übersprungen.
- Lint: null Fehler, 33 Warnungen.
- Debug-APK signaturgeprüft und im API-28-Emulator installiert.

## Grenzen

Echte Wetterdaten bleiben in dieser Cloud durch den Proxy blockiert; der
Demo-Fallback bleibt gekennzeichnet. Softwareemulation bestätigt keine echte
Geräte-Bildrate. Animationen bleiben abschaltbar, respektieren die systemweite
Bewegungsreduzierung und pausieren außerhalb der sichtbaren/aktiven App.

Die Wolkenkorrektur ist in `Thermo-APP_V11.25_Historie-Excel-Timer-Raumampel.zip` enthalten; keine separate V11.23-/V11.24-ZIP.
Nur die Projekt-ZIP wird auf GitHub bereitgestellt; keine zusätzliche APK.

## Emulatorprüfung der Wolkenkorrektur

API-28-Emulator: Sonnig, Bewölkt und Regen mit zeitversetzten Screenshots geprüft;
zusätzlich kurze Videoaufnahme der Sonnenansicht. Originalhimmel wieder scharf,
keine verschobene Himmelkopie. Kleine Wolkensprites mit linearer Bewegung; die
Richtungsumkehr und das Nachzeichnen des vollständigen Hintergrunds sind entfernt.
Prüfarbeitsdateien: `/workspace/thermo-validation/V11.23_Kleine-ziehende-Wolken`.
