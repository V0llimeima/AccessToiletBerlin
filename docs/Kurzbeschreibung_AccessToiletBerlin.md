# Kurzbeschreibung - AccessToilet Berlin

**Name:** Levent Vorpahl  
**Matrikelnummer:** 106002  
**Kurs:** Mobile Geoanwendungen, BHT Berlin, SoSe 2026

## Projektidee

AccessToilet Berlin ist eine native Android-App, die öffentliche Toiletten in Berlin nicht nur als POIs darstellt, sondern standortbezogen bei der Suche nach einer geeigneten Toilette unterstützt. Nutzer können ihren Standort aktivieren, Toiletten nach persönlichen Kriterien filtern, die nächstgelegenen passenden Toiletten anzeigen lassen und für eine ausgewählte Toilette eine echte Fußroute berechnen.

## Daten und Technik

Die App verwendet 509 lokal gespeicherte Toiletten-Features als GeoJSON in WGS84 / EPSG:4326. Die Kartendarstellung erfolgt mit MapLibre und OpenFreeMap Liberty. Die Anwendung ist in Java 17 mit klassischen XML-Layouts umgesetzt.

Standortdaten werden über Android `LocationManager` nur nach bewusster Nutzeraktion verwendet. Die Kandidatensuche arbeitet lokal mit dem aktiven Filterzustand und Haversine-Distanzen. Für die echte Fußroute wird der öffentliche Valhalla-Demodienst von FOSSGIS/OSM Deutschland genutzt.

Lokale Nutzermeldungen zu bestehenden Toiletten werden getrennt von den statischen Daten mit SQLite / `SQLiteOpenHelper` gespeichert und als eigener Kartenlayer dargestellt. Es erfolgt keine Cloud-Synchronisation.

## Umgesetzte Funktionen

- Kartenansicht mit 509 Toiletten
- Detailinformationen und Datenprovenienz
- Standortanzeige
- Filter: Rollstuhleignung, Gebührenstatus, Wickeltisch
- Suche nach fünf nächstgelegenen passenden Toiletten
- Luftlinienentfernung
- reales Fußgängerrouting mit Weglänge und Gehzeit
- lokale Nutzermeldungen mit dauerhafter SQLite-Persistenz
- eigener Meldungs-Layer

## Qualitätssicherung

Zum finalen Stand wurden 746 automatisierte Prüfungen erfolgreich ausgeführt:
- 544 Repository-/Datensatzprüfungen
- 21 Filterprüfungen
- 57 Routingprüfungen
- 58 Kandidatensuchprüfungen
- 66 Meldungsprüfungen

Zusätzlich wurden Build, Installation und zentrale Abläufe auf einem Pixel-7-Emulator mit API 34 praktisch geprüft.

## Grenzen

Die Toilettendaten sind ein lokaler Snapshot. Kartenkacheln und Routing benötigen Internet. Der verwendete Routingdienst ist ein öffentlicher Demo-/Fair-Use-Dienst. Lokale Meldungen werden nur auf dem Gerät gespeichert und nicht mit anderen Nutzern geteilt.
