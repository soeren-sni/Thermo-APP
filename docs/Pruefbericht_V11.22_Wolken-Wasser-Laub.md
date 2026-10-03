# V11.22 – Sichtbare Wolken-, Wasser- und Laubbewegung

Stand: 3. Oktober 2026. Testbranch: `thermo-v11-20-test`.

## Änderung gegenüber V11.21

V11.21 zeigte bei Sonne sehr schwache Wolkenflächen und bewegte kleine Laubpatches
um weniger als einen Bildpunkt in der 360-Pixel-Ansicht. Viele Wasserlinien lagen
hinter den Glas-Karten. Pixelunterschiede allein belegten keine ausreichend
sichtbare Bewegung beim Betrachten der Startseite.

V11.22 bewegt die Wolkenformen des Hintergrunds innerhalb der weich maskierten
Himmelsfläche und stellt die strukturierte Wolkentextur wieder her, mit sichtbarer langsamer
Verschiebung bei Sonnig, Bewölkt und Regen/Gewitter. Eine separate Alpha-Maske
blendet sie nach unten und links aus; der bisherige rechteckige Wald-Abschluss
wird nicht wieder eingeführt. Wetterdaten bestimmen weiterhin Wolkenstärke und
Niederschlag; manuelle Szenen und fehlende Daten bleiben als Demo gekennzeichnet.

Innerhalb einer Teichmaske bewegen sich Bildstreifen leicht gegeneinander.
Dadurch wandern die fotografierten Spiegelungen und Wasserstrukturen selbst.
Kurze, gebrochene Glanzlichter liegen auch oberhalb der Wetterkarten. Nacht
zeichnet zusätzlich warme, bewegte Lampenreflexe. Haus und Bildausschnitt bleiben
statisch. Verschobene Laubpatches besitzen weiche Ränder und einen stärkeren
Ausschlag; Sonnenreflexe wandern im Laub.

## Build und Tests

- `assembleDebug`, `lintDebug`, `testDebugUnitTest`: erfolgreich.
- 22 Unit-Tests, null Fehler und null übersprungene Tests.
- Lint: null Fehler, 33 Warnungen.
- Debug-APK signaturgeprüft und im API-28-Emulator installiert.
- versionName **11.22**, versionCode **28**.

## Emulatorprüfung

Im Verlauf der Überarbeitung wurden Sonnig, Bewölkt, Regen und Nacht auf der
Startseite mit zeitversetzten Aufnahmen geprüft. In allen vier Szenen bewegt sich
auch der frei sichtbare Teichstreifen oberhalb der Karten. Die letzte zusätzliche
Verschiebung der fotografierten Wolken wurde anschließend in der endgültigen
Sonnenansicht mit Screenshot-Paar und kurzer Videoaufnahme kontrolliert.

Die endgültigen Sonnenaufnahmen zeigen Änderungen in Himmel, Laub und frei
sichtbarem Wasser. Hausgeometrie und Bildausschnitt bleiben fest; Lichtflächen
können die Helligkeit von Haus und Umgebung ändern. Keine harte horizontale
Wolkenkante sichtbar. Bei ausgeschalteten Animationen sind die zeitversetzten
Vorschauaufnahmen pixelgleich. Im erfassten AndroidRuntime-Log kein App-Absturz.
Lokale Prüfarbeitsdateien liegen unter
`/workspace/thermo-validation/V11.22_Wolken-Wasser-Laub` und gehören nicht zur ZIP.

## Grenzen

Softwareemulation ohne KVM bewertet weder echte Geräte-Bildrate noch Akkuverbrauch.
Die Zeichnung bleibt auf höchstens 30 Aktualisierungen pro Sekunde begrenzt;
Low-RAM-Geräte verwenden weniger Wasserstreifen und Laubpatches. Unsichtbare bzw.
inaktive Szenen pausieren. App-Schalter und systemweit deaktivierte Bewegung
bleiben wirksam. Bei einem Emulator mit gespeicherten Einstellungen bitte unter
**Einstellungen → Wetteranimationen** prüfen, dass der Schalter eingeschaltet ist.

Der Live-Wetterdienst blieb in dieser Cloud durch die Netzwerkregel blockiert;
Ortskoordinate und Live-Antwort sind weiterhin nicht extern verifiziert.

Projektdatei: `Thermo-APP_V11.22_Wolken-Wasser-Laub.zip`. Nur die Projekt-ZIP wird
auf GitHub bereitgestellt; keine separate APK für diesen Teststand.
