# Raumklima und Geräte – aktueller Stand

## Physik und Anzeige

Der Taupunkt bleibt ein entscheidendes Lüftungskriterium; die Luftfeuchte-Rangfolge
für den Entfeuchter ist keine Lüftungsfreigabe. Lüften trocknet nur, wenn Außenluft
weniger Wasserdampf enthält. Die aktuelle
Beratungsfunktion vergleicht berechneten Taupunkt und absolute Feuchte mit
Sicherheitsabständen (1 °C / 0,5 g/m³). Im Keller ist zusätzlich eine gemessene
Temperatur der kältesten relevanten Wandfläche erforderlich. Der Außentaupunkt
muss mindestens 2 °C darunter liegen. Diese konfigurierbaren Ausgangswerte sind
keine Garantie gegen Schimmel und ersetzen keine Prüfung der Bausubstanz.

`ClimateMath` verwendet die Magnus-Näherung über Wasser. Die Oberflächenfeuchte
wird aus Raumtemperatur, relativer Luftfeuchte und Wandtemperatur abgeleitet.
Der angezeigte aw-Wert ist eine Näherung für das Oberflächenklima, kein gemessener
Wassergehalt des Wandmaterials. Ab ungefähr aw 0,8 kann länger anhaltende Feuchte
Schimmel begünstigen; Material, Temperatur und Dauer beeinflussen das Risiko.
Bei Übersättigung wird aw auf 1 begrenzt und möglicher Tauwasserausfall angezeigt.

Die Wandtemperatur wird manuell je Raum eingegeben und während der aktuellen
App-Sitzung für den Kellervergleich vorgehalten. Nach 15 Minuten wird sie nicht
mehr für aw oder die Empfehlung verwendet. Sie ist kein dauerhaft aktueller
Sensorwert und wird nicht auf Festplatte gespeichert. Raumwerte und Außenwerte bleiben derzeit Demo-Daten.

## Getrennte Kellerräume

Hobby/Fitness (`fitness`), Werkstatt (`workshop`) und Wäschekeller (`laundry`)
sind eigenständige Räume. Messwerte, Wandtemperaturen und Empfehlungen werden
raumweise verarbeitet. Eine künftige Automatik benötigt je Raum eigene
Fensterkontakte, Zeitpläne, Heizungszuordnungen und manuelle Übersteuerung.
Der Entfeuchter darf nur seinem tatsächlichen Standort zugeordnet werden; seine
Wirkung wird nicht für den gesamten Keller angenommen.

## Bekannte Ausstattung

- Batteriebetriebene Zigbee-Temperatur-/Feuchtigkeitssensoren von AliExpress,
  unbekanntes Modell, kleines Gateway mit Display.
- IR-Heizungen in Hobby/Fitness und Wäschekeller sowie in den anderen Hausräumen;
  laut Nutzer über Tuya. Werkstatt ohne bestätigte Heizungszuordnung.
- Midea DF-20DEN7-WF, verwendet mit NetHome Plus; mobil zwischen den drei Kellerräumen.
  Unter **Geräte → Mobiler Entfeuchter → Standort** wird der aktuelle Raum
  manuell ausgewählt und gespeichert. Keine automatische Standorterkennung.
- Anker Smartplug im Zusammenhang mit SOLIX 4 Pro.

IR-Heizung erwärmt Luft und Flächen. Die relative Luftfeuchte kann sinken,
obwohl keine Feuchtigkeit entfernt wird. Eine kalte Wand bleibt kritisch,
solange ihre Oberflächentemperatur nicht steigt. Entfeuchter oder geeignete
Außenluft entfernen dagegen Wasser aus dem Raum.

## Empfehlung für den mobilen Entfeuchter

Unter Geräte vergleicht die App die drei Kellerräume getrennt. Mögliches Tauwasser
hat Vorrang, danach aw ab 0,8, danach Luftfeuchte ab 65 %. Innerhalb derselben
Stufe wird der höhere Wert bevorzugt. Kein Raum wird empfohlen, wenn die
verfügbaren Messwerte keinen entsprechenden Bedarf ergeben. Fehlende Wandwerte
bedeuten dabei keinen Nachweis trockener Wände.

Die aktuelle Ausgangsgrenze liegt bei 15 Minuten; sie muss vor echter
Automatik auf die bekannten Sensor-Meldeintervalle abgestimmt werden.
Sensor- und manuelle Wandmessungen über 15 Minuten alt werden nicht zur Rangfolge
verwendet. Solange keine Sensoren verbunden sind, ist die Anzeige ausdrücklich
Demoanalyse. Sobald echte Raumwerte vorliegen, werden unverbundene Demo-Räume
nicht mit ihnen verglichen. Die Empfehlung ändert den manuell gewählten Standort
nicht und sendet keinen Schaltbefehl. Vor Einsatz die taupunktbezogene
Lüftungsberatung prüfen: geeignete Außenluft kann eine Alternative sein.

## Nächster Integrationsschritt

Noch keine Tuya-/Midea-/Anker-Verbindung und keine automatischen Gerätebefehle.
Vor Aktivierung echter Automatik müssen Geräte, Räume und Messzeitstempel
zugeordnet und die Schnittstellen geprüft werden. Das gilt insbesondere für
Midea-Betriebszustand, Ziel-Feuchte, Tank voll, Störungen und Verhalten nach einer
Stromunterbrechung. Der Smartplug ist nicht automatisch ein Ersatz für den
Entfeuchter-Betriebszustand. Stromabschaltungen für eine spätere Steuerung
benötigen zuvor eine Prüfung des Geräts und seiner Wiederanlaufbedingungen.

Spätere Automatik: Manuell hat Vorrang; offene Fenster, veraltete Messungen,
fehlende Wandtemperatur und Gerätestörungen müssen berücksichtigt werden.
Heizen, Lüften und Entfeuchten dürfen nicht allein nach relativer Luftfeuchte
gegeneinander geschaltet werden. Mindestlaufzeiten und Hysterese sind vor
Aktivierung realer Schaltbefehle gerätespezifisch zu ergänzen.

Schlafende Batteriesensoren lassen sich durch die App nicht beliebig zu neuen
Messungen zwingen. Gateway-Aktualisierung kann nur einen vorhandenen Wert
liefern. Die Anzeige muss deshalb den Zeitpunkt der tatsächlichen Messung
zeigen; Meldeintervalle und explizite Abfragen hängen vom konkreten Modell ab.
