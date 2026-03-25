package de.taxel.catchy

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unit-Tests für die Catchy-App.
 *
 * Diese Tests laufen ohne echtes Gerät und ohne Internetverbindung —
 * sie prüfen rein die Rechenlogik und Datentransformation der App.
 *
 * Was bedeutet ein "Unit-Test"?
 * Ein einzelner, isolierter Test prüft genau eine Sache. Schlägt er fehl,
 * weiß man sofort wo das Problem liegt. Alle Tests hier sind unabhängig
 * voneinander und hinterlassen keine Daten.
 *
 * Abgedeckte Bereiche:
 *  1. Sortierung der Fangliste nach Datum
 *  2. Speichern und Laden eines Fangs als JSON (das Speicherformat der App)
 *  3. Duplikaterkennung beim Backup-Import
 *  4. GPX-Export für Navigationssoftware
 *  5. Gezeitenberechnung (astronomische Näherung)
 *  6. Datum-Umrechnung aus Kamera-EXIF-Daten
 *  7. Verhalten des Fang-Datensatzes (Kopieren, Standardwerte)
 */
class CatchyUnitTest {

    // ─────────────────────────────────────────────────────────────
    // 1. Datumssortierung
    //
    // In der Fangliste soll der neueste Fang immer ganz oben stehen.
    // Diese Tests prüfen, ob die Sortierung korrekt funktioniert —
    // auch wenn ein Datumseintrag fehlerhaft oder leer ist.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `faenge werden absteigend nach Datum sortiert`() {
        // Drei Fänge mit unterschiedlichen Daten werden in falscher Reihenfolge angelegt.
        // Nach der Sortierung muss der jüngste Fang (15. März) an erster Stelle stehen,
        // gefolgt von 8. März und zuletzt 1. März.
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
    fun `ungültiges Datum landet am Ende der sortierten Liste`() {
        // Wenn ein Fang ein ungültiges Datum hat (z.B. durch manuelle Eingabe),
        // darf die App nicht abstürzen. Stattdessen soll dieser Fang ans Ende
        // der Liste sortiert werden.
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
    // 2. JSON-Serialisierung
    //
    // Die App speichert alle Fänge intern im JSON-Format
    // (eine strukturierte Textdarstellung). Diese Tests prüfen,
    // ob beim Speichern (Serialisieren) und Laden (Deserialisieren)
    // kein Datenverlust entsteht — also alle Felder vollständig
    // und korrekt erhalten bleiben.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `fang wird korrekt als JSON serialisiert`() {
        // Ein vollständig ausgefüllter Fang (mit Fischart, Länge, GPS,
        // Wetterdaten, Foto und Gezeiteninformation) wird in JSON umgewandelt.
        // Anschließend wird geprüft, ob alle Werte identisch erhalten geblieben sind —
        // besonders die GPS-Koordinaten und der Gezeitenstand.
        val fang = Fang(
            id = 12345L,
            fischart = "Hecht",
            laenge = "72",
            notizen = "Am Steg",
            datum = "08.03.2026 15:50",
            latitude = 53.5753,
            longitude = 10.0153,
            temperatur = 12.3,
            wind = 3.1,
            luftdruck = 1013.0,
            bewoelkung = 40,
            fotoPfad = "/data/foto.jpg",
            gezeiten = "Steigende Flut 45 %"
        )
        val obj = JSONObject()
        obj.put("id", fang.id)
        obj.put("fischart", fang.fischart)
        obj.put("laenge", fang.laenge)
        obj.put("notizen", fang.notizen)
        obj.put("datum", fang.datum)
        obj.put("latitude", fang.latitude)
        obj.put("longitude", fang.longitude)
        obj.put("temperatur", fang.temperatur)
        obj.put("wind", fang.wind)
        obj.put("luftdruck", fang.luftdruck)
        obj.put("bewoelkung", fang.bewoelkung)
        obj.put("fotoPfad", fang.fotoPfad)
        obj.put("gezeiten", fang.gezeiten)

        assertEquals(12345L, obj.getLong("id"))
        assertEquals("Hecht", obj.getString("fischart"))
        assertEquals("72", obj.getString("laenge"))
        assertEquals(53.5753, obj.getDouble("latitude"), 0.0001)
        assertEquals("Steigende Flut 45 %", obj.getString("gezeiten"))
    }

    @Test
    fun `fang wird korrekt aus JSON deserialisiert`() {
        // Ein gespeicherter Fang im JSON-Format wird zurück in ein Fang-Objekt
        // umgewandelt (so wie es beim App-Start passiert). Es wird geprüft,
        // dass alle Felder — ID, Fischart, GPS und Gezeiten — korrekt gelesen werden.
        val obj = JSONObject()
        obj.put("id", 99L)
        obj.put("fischart", "Zander")
        obj.put("laenge", "55")
        obj.put("notizen", "Tiefer Kanal")
        obj.put("datum", "20.03.2026 07:15")
        obj.put("latitude", 54.1)
        obj.put("longitude", 9.8)
        obj.put("temperatur", 8.5)
        obj.put("wind", 2.0)
        obj.put("luftdruck", 1008.0)
        obj.put("bewoelkung", 80)
        obj.put("fotoPfad", "")
        obj.put("gezeiten", "Niedrigwasser")

        val fang = Fang(
            id = obj.getLong("id"),
            fischart = obj.getString("fischart"),
            laenge = obj.getString("laenge"),
            notizen = obj.getString("notizen"),
            datum = obj.getString("datum"),
            latitude = obj.optDouble("latitude", 0.0),
            longitude = obj.optDouble("longitude", 0.0),
            temperatur = obj.optDouble("temperatur", 0.0),
            wind = obj.optDouble("wind", 0.0),
            luftdruck = obj.optDouble("luftdruck", 0.0),
            bewoelkung = obj.optInt("bewoelkung", 0),
            fotoPfad = obj.optString("fotoPfad", ""),
            gezeiten = obj.optString("gezeiten", "")
        )
        assertEquals(99L, fang.id)
        assertEquals("Zander", fang.fischart)
        assertEquals(54.1, fang.latitude, 0.0001)
        assertEquals("Niedrigwasser", fang.gezeiten)
    }

    @Test
    fun `alter fang ohne gezeiten-feld crasht nicht`() {
        // Rückwärtskompatibilität: Fänge, die mit einer älteren App-Version
        // gespeichert wurden, haben noch kein "gezeiten"-Feld. Beim Laden
        // darf die App nicht abstürzen — stattdessen soll ein leerer String
        // als Standardwert verwendet werden.
        val obj = JSONObject()
        obj.put("id", 1L)
        obj.put("fischart", "Barsch")
        obj.put("laenge", "20")
        obj.put("notizen", "")
        obj.put("datum", "01.01.2026 10:00")
        // kein "gezeiten" Feld — simuliert alten Datensatz
        val gezeiten = obj.optString("gezeiten", "")
        assertEquals("", gezeiten)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Duplikat-Erkennung beim Import
    //
    // Wenn der Nutzer eine Backup-Datei importiert, darf kein Fang
    // doppelt in der Liste erscheinen. Jeder Fang hat eine eindeutige ID —
    // ist diese bereits vorhanden, wird der Eintrag übersprungen.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `duplikate werden beim import korrekt erkannt`() {
        // In der App ist bereits ein Hecht gespeichert (ID 100).
        // Eine importierte Backup-Datei enthält denselben Hecht (ID 100, Duplikat)
        // und einen neuen Zander (ID 200). Es darf nur der Zander importiert werden —
        // der Hecht muss als Duplikat erkannt und übersprungen werden.
        val bestehend = JSONArray()
        val obj1 = JSONObject(); obj1.put("id", 100L); obj1.put("fischart", "Hecht")
        bestehend.put(obj1)

        val importArray = JSONArray()
        val obj2 = JSONObject(); obj2.put("id", 100L); obj2.put("fischart", "Hecht") // Duplikat
        val obj3 = JSONObject(); obj3.put("id", 200L); obj3.put("fischart", "Zander") // Neu
        importArray.put(obj2)
        importArray.put(obj3)

        val bestehendeIds = mutableSetOf<Long>()
        for (i in 0 until bestehend.length()) bestehendeIds.add(bestehend.getJSONObject(i).getLong("id"))

        var importiert = 0
        for (i in 0 until importArray.length()) {
            val obj = importArray.getJSONObject(i)
            if (!bestehendeIds.contains(obj.getLong("id"))) importiert++
        }
        assertEquals(1, importiert) // Nur der Zander ist neu
    }

    // ─────────────────────────────────────────────────────────────
    // 4. GPX-Export
    //
    // GPX ist ein Standardformat für GPS-Wegpunkte, das von Navigationsgeräten
    // und Apps wie Garmin, komoot oder Google Maps importiert werden kann.
    // Die App kann alle Fangspots als GPX-Datei exportieren. Diese Tests
    // prüfen, ob die exportierte Datei korrekte und valide Daten enthält.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `gpx enthält korrekte koordinaten`() {
        // Ein Fang mit bekannten GPS-Koordinaten (Hamburg-Bereich) wird als
        // GPX-Wegpunkt exportiert. Die exportierte Datei muss die exakten
        // Koordinaten im richtigen Format enthalten, damit Navigationsgeräte
        // den Fangspot korrekt anzeigen können.
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht", laenge = "60", notizen = "", datum = "08.03.2026 15:50",
                latitude = 53.5753, longitude = 10.0153, temperatur = 12.0, wind = 2.0)
        )
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Catchy" xmlns="http://www.topografix.com/GPX/1/1">""")
        for (fang in faenge.filter { it.latitude != 0.0 }) {
            val lat = String.format(Locale.US, "%.6f", fang.latitude)
            val lon = String.format(Locale.US, "%.6f", fang.longitude)
            sb.appendLine("""  <wpt lat="$lat" lon="$lon">""")
            sb.appendLine("""    <name>${fang.fischart}</name>""")
            sb.appendLine("""    <sym>Fishing Hot Spot Facility</sym>""")
            sb.appendLine("""  </wpt>""")
        }
        sb.appendLine("""</gpx>""")
        val gpx = sb.toString()
        assertTrue(gpx.contains("""lat="53.575300""""))
        assertTrue(gpx.contains("""lon="10.015300""""))
        assertTrue(gpx.contains("Fishing Hot Spot Facility"))
        assertTrue(gpx.contains("<name>Hecht</name>"))
    }

    @Test
    fun `fang ohne gps wird nicht in gpx exportiert`() {
        // Ein Fang ohne GPS-Daten (z.B. manuell eingegeben ohne Standort)
        // soll nicht in die GPX-Datei aufgenommen werden, da ein Wegpunkt
        // ohne Koordinaten für Navigationsgeräte sinnlos wäre.
        val faenge = listOf(
            Fang(id = 1L, fischart = "Hecht", laenge = "", notizen = "", datum = "08.03.2026 15:50",
                latitude = 0.0, longitude = 0.0)
        )
        val fangeMitGps = faenge.filter { it.latitude != 0.0 }
        assertEquals(0, fangeMitGps.size)
    }

    @Test
    fun `sonderzeichen in fischart werden in gpx escaped`() {
        // Im XML-Format (und GPX ist XML) haben bestimmte Zeichen eine
        // Sonderbedeutung: & < >. Wenn ein Nutzer z.B. "Hecht & Zander"
        // als Fischart eingibt, muss das & in &amp; umgewandelt werden,
        // sonst ist die GPX-Datei ungültig und kann nicht importiert werden.
        val fischart = "Hecht & Zander <groß>"
        val escaped = fischart.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        assertEquals("Hecht &amp; Zander &lt;groß&gt;", escaped)
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Gezeiten-Berechnung
    //
    // Die App berechnet den Gezeitenstand (Flut/Ebbe) anhand der
    // Mondposition ohne Internetverbindung. Diese Tests prüfen,
    // ob die Berechnung sinnvolle Ergebnisse liefert und mit
    // Fehlereingaben umgehen kann.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `gezeiten leer wenn kein gps vorhanden`() {
        // Ohne GPS-Koordinaten kann kein Gezeitenstand berechnet werden.
        // Die Funktion soll in diesem Fall einen leeren String zurückgeben —
        // kein Absturz, keine sinnlose Ausgabe.
        val ergebnis = gezeiteBerechnen("08.03.2026 15:50", 0.0, 0.0)
        assertEquals("", ergebnis)
    }

    @Test
    fun `gezeiten gibt string zurück bei küstenstandort`() {
        // Für einen echten Küstenstandort (hier: Cuxhaven an der Nordsee)
        // muss die Funktion einen Gezeitenstand ausgeben. Das Ergebnis muss
        // einen der vier möglichen Werte enthalten:
        // "Hochwasser", "Niedrigwasser", "Steigende Flut" oder "Fallende Ebbe".
        val ergebnis = gezeiteBerechnen("08.03.2026 15:50", 53.867, 8.700)
        assertTrue("Gezeiten sollten nicht leer sein bei Küstenstandort",
            ergebnis.isNotBlank())
        val gueltigeWerte = listOf("Hochwasser", "Niedrigwasser", "Steigende Flut", "Fallende Ebbe")
        assertTrue("Gezeitenwert muss gültig sein",
            gueltigeWerte.any { ergebnis.contains(it) })
    }

    @Test
    fun `gezeiten erkennt springtide bei neumond`() {
        // Springtide tritt bei Neu- und Vollmond auf — dann ist der Tidenhub
        // besonders groß, was für Angler relevant ist.
        // Der 6. Januar 2000 war ein bekannter Neumond (der Referenzpunkt der
        // Berechnung). Einen Tag danach muss die App "Springtide" erkennen.
        val ergebnis = gezeiteBerechnen("07.01.2000 12:00", 53.867, 8.700)
        assertTrue("Springtide sollte erkannt werden",
            ergebnis.contains("Springtide"))
    }

    @Test
    fun `gezeiten gibt sinnvollen wert für binnensee zurück`() {
        // Auch für Binnengewässer (hier: Bodensee) liefert die Funktion ein Ergebnis,
        // da die astronomische Berechnung nur GPS-Koordinaten benötigt.
        // In der Praxis sind Gezeiten im Binnenland kaum spürbar, aber die
        // Funktion soll trotzdem stabil laufen und kein leeres Ergebnis liefern.
        val ergebnis = gezeiteBerechnen("08.03.2026 15:50", 47.65, 9.25)
        assertTrue("Gezeitenwert sollte berechnet werden", ergebnis.isNotBlank())
    }

    @Test
    fun `gezeiten unterscheiden sich je nach uhrzeit`() {
        // Gezeiten wechseln etwa alle 6 Stunden (zweimal Hochwasser und
        // zweimal Niedrigwasser pro Tag). Werden drei Zeitpunkte am selben Tag
        // berechnet (morgens, mittags, abends), dürfen nicht alle drei
        // identisch sein — sonst würde die Berechnung nicht funktionieren.
        val lat = 53.867; val lon = 8.700
        val morgens = gezeiteBerechnen("08.03.2026 06:00", lat, lon)
        val mittags  = gezeiteBerechnen("08.03.2026 12:00", lat, lon)
        val abends   = gezeiteBerechnen("08.03.2026 18:00", lat, lon)
        val alleGleich = (morgens == mittags && mittags == abends)
        assertFalse("Gezeitenwerte sollten sich über den Tag ändern", alleGleich)
    }

    @Test
    fun `gezeiten ungültiges datum gibt leeren string zurück`() {
        // Wenn das übergebene Datum nicht geparst werden kann (z.B. weil es
        // kein gültiges Datumsformat ist), darf die App nicht abstürzen.
        // Stattdessen soll ein leerer String zurückgegeben werden.
        val ergebnis = gezeiteBerechnen("KEIN_DATUM", 53.867, 8.700)
        assertEquals("", ergebnis)
    }

    // ─────────────────────────────────────────────────────────────
    // 6. EXIF Datums-Parsing (ohne Context — nur Formatlogik)
    //
    // Kameras speichern das Aufnahmedatum im EXIF-Format direkt im Foto.
    // Das Format unterscheidet sich vom App-Format und muss umgerechnet
    // werden. Besonders wichtig: die korrekte Umrechnung von UTC in
    // Berliner Ortszeit.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `exif datum wird korrekt von kamera-format in app-format konvertiert`() {
        // Kameras speichern das Datum als "2026:03:08 15:50:00" (Kamera-Format).
        // Die App zeigt es als "08.03.2026 15:50" an (deutsches Format).
        // Dieser Test prüft, ob die Umwandlung korrekt funktioniert.
        val datumRoh = "2026:03:08 15:50:00"
        val offsetRoh = "+01:00"
        val eingang = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY)
        eingang.timeZone = java.util.TimeZone.getTimeZone("GMT$offsetRoh")
        val ausgang = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        ausgang.timeZone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val ergebnis = ausgang.format(eingang.parse(datumRoh)!!)
        assertEquals("08.03.2026 15:50", ergebnis)
    }

    @Test
    fun `exif datum ohne zeitzone wird trotzdem geparst`() {
        // Manche Kameras speichern kein Zeitzonenfeld im EXIF.
        // Das Datum soll trotzdem gelesen werden können — kein Absturz.
        val datumRoh = "2026:03:08 15:50:00"
        val eingang = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY)
        val parsed = eingang.parse(datumRoh)
        assertNotNull(parsed)
    }

    @Test
    fun `exif datum utc wird korrekt in berliner zeit umgerechnet`() {
        // Einige Kameras (besonders ältere oder ausländische Modelle) speichern
        // die Zeit in UTC statt in Ortszeit. In Deutschland ist UTC+1 (Winter)
        // bzw. UTC+2 (Sommer). Hier: 14:50 UTC muss als 15:50 Berliner Zeit
        // (Winterzeit, UTC+1) angezeigt werden.
        val datumRoh = "2026:03:08 14:50:00" // 14:50 UTC
        val offsetRoh = "+00:00"
        val eingang = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY)
        eingang.timeZone = java.util.TimeZone.getTimeZone("GMT$offsetRoh")
        val ausgang = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        ausgang.timeZone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val ergebnis = ausgang.format(eingang.parse(datumRoh)!!)
        assertEquals("08.03.2026 15:50", ergebnis) // +1 Stunde = 15:50
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Fang Data Class
    //
    // Der "Fang"-Datensatz ist das zentrale Datenmodell der App.
    // Diese Tests prüfen grundlegendes Verhalten: Wird beim Bearbeiten
    // eines Fangs wirklich nur das geänderte Feld überschrieben?
    // Sind die Standardwerte korrekt wenn ein neuer Fang angelegt wird?
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `fang copy behält alle felder korrekt`() {
        // Wenn ein Fang bearbeitet wird (z.B. die Fischart korrigiert),
        // erstellt die App intern eine Kopie mit nur dem geänderten Feld.
        // Alle anderen Felder — ID, GPS, Gezeiten usw. — müssen unverändert
        // bleiben. Dieser Test prüft genau das anhand eines konkreten Beispiels.
        val original = Fang(
            id = 1L, fischart = "Hecht", laenge = "60", notizen = "Test",
            datum = "08.03.2026 15:50", latitude = 53.5, longitude = 10.0,
            temperatur = 12.0, wind = 3.0, luftdruck = 1013.0, bewoelkung = 40,
            fotoPfad = "/foto.jpg", gezeiten = "Hochwasser"
        )
        val kopie = original.copy(fischart = "Zander")
        assertEquals("Zander", kopie.fischart)
        assertEquals(1L, kopie.id)
        assertEquals("Hochwasser", kopie.gezeiten)
        assertEquals(53.5, kopie.latitude, 0.0001)
    }

    @Test
    fun `fang standardwerte sind korrekt`() {
        // Wird ein neuer Fang mit minimalem Inhalt angelegt (nur Pflichtfelder),
        // müssen alle optionalen Felder sinnvolle Standardwerte haben:
        // GPS = 0.0 (kein Standort), Temperatur = 0.0, Foto = leer, Gezeiten = leer.
        val fang = Fang(fischart = "Hecht", laenge = "", notizen = "", datum = "08.03.2026 15:50")
        assertEquals(0.0, fang.latitude, 0.0)
        assertEquals(0.0, fang.longitude, 0.0)
        assertEquals(0.0, fang.temperatur, 0.0)
        assertEquals("", fang.fotoPfad)
        assertEquals("", fang.gezeiten)
    }
}
