# AccessToilet Berlin

**BHT Berlin – Mobile Geoanwendungen, SoSe 2026**  
**Levent Vorpahl – Matrikelnummer 106002**

AccessToilet Berlin ist eine native Android-App zur standortbezogenen Suche nach geeigneten öffentlichen Toiletten in Berlin. Die Anwendung kombiniert lokale Toilettendaten mit Standortanzeige, Filtern, einer lokalen Kandidatensuche, Fußgängerrouting und lokalen Nutzermeldungen.

## Funktionen

- 509 öffentliche Toiletten aus einem lokalen GeoJSON-Datensatz (WGS84 / EPSG:4326)
- MapLibre 11.13.1 mit OpenFreeMap Liberty
- Detailansicht mit Attributen und Datenquelle
- Standortanzeige über Android `LocationManager`
- Filter nach Rollstuhleignung, Gebührenstatus und Wickeltisch
- Suche nach den fünf nächsten passenden Toiletten mit Haversine-Distanz
- Fußgängerrouting über Valhalla/FOSSGIS OSM-DE
- Anzeige von Weglänge und geschätzter Gehzeit
- lokale Nutzermeldungen mit SQLite / `SQLiteOpenHelper`
- eigener Karten-Layer für gespeicherte Meldungen
- 746 erfolgreich ausgeführte automatisierte Prüfungen im finalen Entwicklungsstand

## Projektstruktur

```text
.
├─ app/                     Android-Anwendung
├─ gradle/                  Gradle Wrapper
├─ verification/            Prüfprogramme und Gradle-Tasks
├─ docs/
│  ├─ Kurzbeschreibung_AccessToiletBerlin.pdf
│  └─ screenshots/
├─ release/
│  └─ AccessToiletBerlin.apk
├─ build.gradle
├─ settings.gradle
├─ gradle.properties
├─ gradlew
├─ gradlew.bat
└─ README.md
```

## Schnellstart für Nutzer

Für die normale Nutzung muss das Projekt **nicht selbst kompiliert** werden. Die fertige APK liegt bereits im Repository unter:

```text
release/AccessToiletBerlin.apk
```

### Installation auf einem Android-Smartphone

1. Dieses GitHub-Repository herunterladen:
   - über **Code → Download ZIP**, anschließend die ZIP-Datei entpacken,
   - oder direkt die Datei `release/AccessToiletBerlin.apk` herunterladen.
2. Die APK auf das Android-Gerät übertragen, falls sie am PC heruntergeladen wurde.
3. `AccessToiletBerlin.apk` auf dem Smartphone öffnen.
4. Falls Android nachfragt, die Installation von Apps aus dieser Quelle für den verwendeten Browser oder Dateimanager erlauben.
5. Die Installation bestätigen und anschließend **AccessToilet Berlin** öffnen.

Die App benötigt **Android 7.0 (API 24) oder neuer**.

### Erste Nutzung

- Für die Basiskarte und das Fußgängerrouting wird eine Internetverbindung benötigt.
- Die 509 Toilettendatensätze selbst sind Bestandteil der App und werden lokal geladen.
- Mit **STANDORT** kann die eigene Position eingeblendet werden. Erst dann fragt Android nach der Standortberechtigung.
- Mit **FILTER** lassen sich Toiletten nach Rollstuhleignung, Gebührenstatus und Wickeltisch einschränken.
- Mit **IN DER NÄHE** werden nach aktivierter Standortfunktion die fünf nächstgelegenen Toiletten angezeigt, die zum aktuellen Filter passen.
- Nach Auswahl einer Toilette kann über **ROUTE ANZEIGEN** eine Fußroute berechnet werden.
- Unter **MELDUNGEN** können Hinweise zu einer Toilette lokal auf dem Gerät gespeichert werden. Diese Daten werden nicht übertragen.

### Falls die Installation blockiert wird

Bei aktuellen Android-Versionen kann die Installation einer APK außerhalb des Play Stores zunächst gesperrt sein. In diesem Fall zeigt Android normalerweise direkt die passende Einstellung für den verwendeten Browser oder Dateimanager an. Nach Freigabe dieser Quelle kann die APK erneut geöffnet und installiert werden.

Die App wurde als Hochschulprojekt außerhalb des Google Play Stores bereitgestellt.

## Build aus dem Quellcode

Wer die App selbst kompilieren möchte, benötigt:

- Android SDK mit Plattform 34
- Gradle-kompatible Java-Laufzeit; der geprüfte Entwicklungsstand verwendet JBR/JDK 21
- Java-Quellcode mit Source-/Target-Level 17

Build unter Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Die erzeugte APK liegt anschließend unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Ausführbare Version

Die für die Abgabe gebaute APK liegt unter:

```text
release/AccessToiletBerlin.apk
```

Die App unterstützt Android ab Version 7.0 (API 24). Die APK enthält native MapLibre-Bibliotheken für `arm64-v8a`, `armeabi-v7a`, `x86` und `x86_64`.

Die finale Fassung wurde auf einem Pixel-7-Emulator mit Android API 34 geprüft. Daraus folgt keine Garantie für jedes Android-Gerät: Herstelleranpassungen, fehlende oder deaktivierte Ortungsdienste, Grafiktreiber und Netzwerkbedingungen können das Verhalten beeinflussen.

## Netzwerk und Datenschutz

- Die Toilettendaten liegen lokal in der App und werden nicht automatisch aktualisiert.
- Basiskarte und Routing benötigen eine Internetverbindung.
- Der verwendete Valhalla-Endpunkt ist ein öffentlicher Demo-/Fair-Use-Dienst und keine garantierte Produktionsinfrastruktur.
- Standortberechtigungen werden nur für die Standortfunktion benötigt.
- Lokale Nutzermeldungen werden ausschließlich im privaten App-Speicher abgelegt und nicht übertragen.

## Nicht enthalten

`local.properties`, IDE-Einstellungen und Build-Ausgaben sind nicht Bestandteil des GitHub-Abgabeordners.