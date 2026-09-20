# Verification

Dieser Ordner enthält die Prüfprogramme und Gradle-Tasks des finalen Projektstands.

## Prüfstand

- 544 Repository- und Datensatzprüfungen
- 21 Filterprüfungen
- 57 Routingprüfungen
- 58 Prüfungen der Kandidatensuche
- 66 Prüfungen der lokalen Meldungen
- **746 Prüfungen insgesamt**

Die automatisierten Routingprüfungen arbeiten ohne öffentliche Netzwerkanfragen. Netzwerk- und Darstellungsverhalten wurden zusätzlich im Emulator geprüft.

## Gesamtaufruf unter Windows

```powershell
.\gradlew.bat `
  -I verification\repository-check.gradle `
  -I verification\filter-check.gradle `
  -I verification\routing-check.gradle `
  -I verification\candidate-search-check.gradle `
  -I verification\user-report-check.gradle `
  :app:checkPhase2Repository `
  :app:checkPhase5Filters `
  :app:checkPhase6Routing `
  :app:checkPhase7CandidateSearch `
  :app:checkPhase8UserReports `
  :app:assembleDebug
```
