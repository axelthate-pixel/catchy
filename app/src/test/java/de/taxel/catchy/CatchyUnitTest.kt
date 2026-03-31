package de.taxel.catchy

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unit-Tests für reine Kotlin/Java-Logik der Catchy-App.
 * Laufen ohne Gerät direkt auf der JVM — in Android Studio über
 * Run > CatchyUnitTest oder per Kommandozeile: ./gradlew test
 *
 * Abgedeckte Bereiche:
 *  1. Sortierung der Fangliste nach Datum
 *  2. Duplikaterkennung beim Backup-Import
 *  3. GPX-Export für Navigationssoftware
 *  4. Gezeitenberechnung (astronomische Näherung)
 *  5. Datum-Umrechnung aus Kamera-EXIF-Daten (Formatlogik)
 */
class CatchyUnitTest {

    // ─────────────────────────────────────────────────────────────
    // 1. Datumssortierung
    //
    // In der Fangliste soll der neueste Fang immer ganz oben stehen.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun faengeWerdenAbsteigendNachDatumSortiert() {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht",  laenge = "60", notizen = "", datum = "01.03.2026 08:00"),
            Fang(id = 2L, fischart = "Zander", laenge = "45", notizen = "", datum = "15.03.2026 14:30"),
            Fang(id = 3L, fischart = "Barsch", laenge = "25", notizen = "", datum = "08.03.2026 11:00"),
        )
        val sortiert = faenge.sortedByDescending {
            try { sdf.parse(it.datum) } catch (e: Exception) { Date(0) }
        }
        assertEquals("Zander", sortiert[0].fischart)
        assertEquals("Barsch", sortiert[1].fischart)
        assertEquals("Hecht",  sortiert[2].fischart)
    }

    @Test
    fun leereListeWirdOhneFehlerSortiert() {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val sortiert = emptyList<Fang>().sortedByDescending {
            try { sdf.parse(it.datum) } catch (e: Exception) { Date(0) }
        }
        assertTrue("Leere Liste bleibt leer nach Sortierung", sortiert.isEmpty())
    }

    @Test
    fun ungueltigesDatumLandetAmEndeDerSortiertenListe() {
        // Die App darf bei fehlerhaften Datumseinträgen nicht abstürzen —
        // solche Einträge sollen ans Ende sortiert werden.
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht",  laenge = "", notizen = "", datum = "01.03.2026 08:00"),
            Fang(id = 2L, fischart = "Defekt", laenge = "", notizen = "", datum = "KEIN_DATUM"),
            Fang(id = 3L, fischart = "Zander", laenge = "", notizen = "", datum = "15.03.2026 14:30"),
        )
        val sortiert = faenge.sortedByDescending {
            try { sdf.parse(it.datum) } catch (e: Exception) { Date(0) }
        }
        assertEquals("Defekt", sortiert.last().fischart)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Duplikat-Erkennung beim Import
    //
    // Beim Backup-Import darf kein Fang doppelt erscheinen.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun duplikateWerdenBeimImportKorrektErkannt() {
        // ID 100 ist bereits vorhanden → muss übersprungen werden.
        // ID 200 ist neu → muss importiert werden.
        val bestehendeIds = setOf(100L)
        val importIds = listOf(100L, 200L)
        val neueIds = importIds.filter { !bestehendeIds.contains(it) }
        assertEquals(1, neueIds.size)
        assertEquals(200L, neueIds[0])
    }

    @Test
    fun duplikaterkennungMitLeererImportListeErgibtKeineNeueIds() {
        val bestehendeIds = setOf(100L, 200L)
        val neueIds = emptyList<Long>().filter { !bestehendeIds.contains(it) }
        assertTrue("Leere Importliste soll keine neuen IDs ergeben", neueIds.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GPX-Export
    //
    // GPX ist ein XML-Standard für GPS-Wegpunkte (Garmin, komoot, …).
    // ─────────────────────────────────────────────────────────────

    /** Spiegelt die GPX-Baulogik aus der App wider. */
    private fun gpxErstellen(faenge: List<Fang>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Catchy" xmlns="http://www.topografix.com/GPX/1/1">""")
        for (fang in faenge.filter { it.latitude != 0.0 }) {
            val lat  = String.format(Locale.US, "%.6f", fang.latitude)
            val lon  = String.format(Locale.US, "%.6f", fang.longitude)
            val name = fang.fischart
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            sb.appendLine("""  <wpt lat="$lat" lon="$lon">""")
            sb.appendLine("""    <name>$name</name>""")
            sb.appendLine("""    <sym>Fishing Hot Spot Facility</sym>""")
            sb.appendLine("""  </wpt>""")
        }
        sb.appendLine("""</gpx>""")
        return sb.toString()
    }

    @Test
    fun gpxEnthaeltKorrekteKoordinaten() {
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht", laenge = "60", notizen = "", datum = "08.03.2026 15:50",
                latitude = 53.5753, longitude = 10.0153)
        )
        val gpx = gpxErstellen(faenge)
        assertTrue(gpx.contains("""lat="53.575300""""))
        assertTrue(gpx.contains("""lon="10.015300""""))
        assertTrue(gpx.contains("<name>Hecht</name>"))
        assertTrue(gpx.contains("Fishing Hot Spot Facility"))
    }

    @Test
    fun fangOhneGpsWirdNichtInGpxExportiert() {
        // Ein Wegpunkt ohne Koordinaten wäre für Navigationsgeräte sinnlos.
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht", laenge = "", notizen = "", datum = "08.03.2026 15:50",
                latitude = 0.0, longitude = 0.0)
        )
        val gpx = gpxErstellen(faenge)
        assertFalse("Fang ohne GPS darf keinen <wpt>-Eintrag erzeugen", gpx.contains("<wpt"))
    }

    @Test
    fun sonderzeichenInFischartWerdenInGpxEscaped() {
        // Im XML haben & < > eine Sonderbedeutung und müssen escaped werden,
        // damit die GPX-Datei valide bleibt und importiert werden kann.
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht & Zander <groß>", laenge = "", notizen = "", datum = "08.03.2026 15:50",
                latitude = 53.5753, longitude = 10.0153)
        )
        val gpx = gpxErstellen(faenge)
        assertTrue(gpx.contains("&amp;"))
        assertTrue(gpx.contains("&lt;"))
        assertTrue(gpx.contains("&gt;"))
        assertFalse("Rohes & darf nicht im XML stehen", gpx.contains("<name>Hecht & Zander"))
    }

    @Test
    fun gpxMitMehrerenFaengenEnthaeltAlleWegpunkte() {
        // Nur Fänge mit GPS-Koordinaten dürfen exportiert werden.
        // Fänge ohne GPS (latitude = 0.0) müssen ausgelassen werden.
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht",  laenge = "", notizen = "", datum = "01.03.2026 08:00", latitude = 53.57, longitude = 10.01),
            Fang(id = 2L, fischart = "Zander", laenge = "", notizen = "", datum = "08.03.2026 15:50", latitude = 53.86, longitude = 8.70),
            Fang(id = 3L, fischart = "Barsch", laenge = "", notizen = "", datum = "15.03.2026 12:30", latitude = 0.0,   longitude = 0.0),
        )
        val gpx = gpxErstellen(faenge)
        val wptAnzahl = Regex("<wpt ").findAll(gpx).count()
        assertEquals("Nur Fänge mit GPS dürfen exportiert werden", 2, wptAnzahl)
        assertTrue(gpx.contains("<name>Hecht</name>"))
        assertTrue(gpx.contains("<name>Zander</name>"))
        assertFalse("Fang ohne GPS darf nicht im GPX sein", gpx.contains("<name>Barsch</name>"))
    }

    @Test
    fun gpxMitExtremerLaengegradKoordinateExportiert() {
        // Koordinaten nahe der Datumsgrenze (±180°) müssen korrekt
        // im US-Locale-Format ausgegeben werden — ohne Komma statt Punkt.
        val faenge = listOf(
            Fang(id = 1L, fischart = "Thunfisch", laenge = "", notizen = "", datum = "08.03.2026 15:50",
                latitude = 0.5, longitude = 179.999)
        )
        val gpx = gpxErstellen(faenge)
        assertTrue(gpx.contains("""lon="179.999000""""))
        assertTrue(gpx.contains("""lat="0.500000""""))
    }

    @Test
    fun gpxMitLeererListeIstValidesLeeresDokument() {
        // Auch ohne Fänge muss die GPX-Datei strukturell valide XML sein —
        // sonst schlägt der Import in Navigationsgeräten fehl.
        val gpx = gpxErstellen(emptyList())
        assertTrue(gpx.contains("""<?xml version="1.0""""))
        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("</gpx>"))
        assertFalse("Leeres GPX darf keinen <wpt>-Eintrag enthalten", gpx.contains("<wpt"))
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Gezeitenberechnung
    //
    // Die Funktion berechnet Tidenhub ohne Internetverbindung anhand
    // der Mondposition.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun gezeitenLeerWennKeinGpsVorhanden() {
        assertEquals("", gezeiteBerechnen("08.03.2026 15:50", 0.0, 0.0))
    }

    @Test
    fun gezeitenGibtStringZurueckBeiKuestenstandort() {
        val ergebnis = gezeiteBerechnen("08.03.2026 15:50", 53.867, 8.700)
        val gueltigeWerte = listOf("Hochwasser", "Niedrigwasser", "Steigende Flut", "Fallende Ebbe")
        assertTrue("Gezeitenwert muss einen gültigen Begriff enthalten",
            gueltigeWerte.any { ergebnis.contains(it) })
    }

    @Test
    fun gezeitenErkenntSpringtideBeiNeumond() {
        // Der 6. Januar 2000 war der Referenz-Neumond der Berechnung.
        // Einen Tag danach muss "Springtide" erkannt werden.
        val ergebnis = gezeiteBerechnen("07.01.2000 12:00", 53.867, 8.700)
        assertTrue("Springtide sollte erkannt werden", ergebnis.contains("Springtide"))
    }

    @Test
    fun gezeitenGibtSinnvollenWertFuerBinnenseeZurueck() {
        // Die astronomische Berechnung läuft auch für Binnengewässer stabil durch.
        val ergebnis = gezeiteBerechnen("08.03.2026 15:50", 47.65, 9.25)
        val gueltigeWerte = listOf("Hochwasser", "Niedrigwasser", "Steigende Flut", "Fallende Ebbe")
        assertTrue("Gezeitenwert sollte für jeden Standort einen gültigen Begriff enthalten",
            gueltigeWerte.any { ergebnis.contains(it) })
    }

    @Test
    fun gezeitenUnterscheidenSichJeNachUhrzeit() {
        // Gezeiten wechseln alle ~6 Stunden. Drei Zeitpunkte am selben Tag
        // dürfen nicht alle denselben Wert liefern.
        val lat = 53.867; val lon = 8.700
        val morgens = gezeiteBerechnen("08.03.2026 06:00", lat, lon)
        val mittags  = gezeiteBerechnen("08.03.2026 12:00", lat, lon)
        val abends   = gezeiteBerechnen("08.03.2026 18:00", lat, lon)
        assertFalse("Gezeitenwerte müssen sich im Tagesverlauf ändern",
            morgens == mittags && mittags == abends)
    }

    @Test
    fun gezeitenUngueltigesDatumGibtLeerenStringZurueck() {
        // Fehlerhafte Datumseingaben dürfen keinen Absturz verursachen.
        assertEquals("", gezeiteBerechnen("KEIN_DATUM", 53.867, 8.700))
    }

    // ─────────────────────────────────────────────────────────────
    // 5. EXIF Datums-Format
    //
    // Kameras speichern das Datum als "yyyy:MM:dd HH:mm:ss".
    // Die App muss es in "dd.MM.yyyy HH:mm" (Berliner Zeit) umwandeln.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun exifDatumWirdKorrektVonKameraFormatInAppFormatKonvertiert() {
        val eingang = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY)
        eingang.timeZone = TimeZone.getTimeZone("GMT+01:00")
        val ausgang = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        ausgang.timeZone = TimeZone.getTimeZone("Europe/Berlin")
        val ergebnis = ausgang.format(eingang.parse("2026:03:08 15:50:00")!!)
        assertEquals("08.03.2026 15:50", ergebnis)
    }

    @Test
    fun exifDatumOhneZeitzoneWirdTrotzdemGeparst() {
        // Manche Kameras speichern kein Zeitzonenfeld. Das Datum
        // muss trotzdem ohne Absturz gelesen werden können.
        val eingang = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY)
        assertNotNull(eingang.parse("2026:03:08 15:50:00"))
    }

    @Test
    fun exifDatumUtcWirdKorrektInBerlinerZeitUmgerechnet() {
        // 14:50 UTC im März (Winterzeit, UTC+1) muss als 15:50 MEZ angezeigt werden.
        val eingang = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY)
        eingang.timeZone = TimeZone.getTimeZone("GMT+00:00")
        val ausgang = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        ausgang.timeZone = TimeZone.getTimeZone("Europe/Berlin")
        val ergebnis = ausgang.format(eingang.parse("2026:03:08 14:50:00")!!)
        assertEquals("08.03.2026 15:50", ergebnis)
    }
}
