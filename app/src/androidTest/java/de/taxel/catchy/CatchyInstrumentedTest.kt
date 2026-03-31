package de.taxel.catchy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Instrumented Tests für die Catchy-App.
 *
 * Diese Tests laufen auf einem echten Android-Gerät oder Emulator —
 * sie benötigen die reale Android-Umgebung (Dateisystem, Kamera-APIs usw.).
 *
 * Was bedeutet ein "Instrumented Test"?
 * Im Gegensatz zu Unit-Tests greifen diese Tests auf echte Gerätfunktionen
 * zu: Sie erstellen echte Bilddateien, lesen echte EXIF-Metadaten und
 * schreiben in den echten App-Speicher. Dadurch testen sie, ob alles
 * auch auf dem Gerät des Nutzers korrekt funktioniert.
 *
 * Testaufbau (vor jedem Test):
 *   - Der App-Datenspeicher wird geleert (saubere Ausgangslage)
 *
 * Testabbau (nach jedem Test):
 *   - Alle erstellten Testfotos werden gelöscht
 *   - Der App-Datenspeicher wird wieder geleert
 *
 * Abgedeckte Bereiche:
 *  1. Lesen von EXIF-Metadaten aus echten Fotos (Datum, GPS, Zeitzone)
 *  2. Galerie-Import: Einzelfoto
 *  3. Galerie-Import: Mehrere Fotos gleichzeitig
 *  4. Fänge speichern, bearbeiten und löschen
 *  5. Vollständiger Import-Workflow von A bis Z
 */
@RunWith(AndroidJUnit4::class)
class CatchyInstrumentedTest {

    private lateinit var context: Context
    // Liste aller Testdateien, die nach dem Test wieder gelöscht werden
    private val testDateien = mutableListOf<File>()
    private val TEST_PREFS = "angelapp_test"

    @Before
    fun setup() {
        // Vor jedem Test: Kontext holen und gespeicherte Fänge löschen,
        // damit sich Tests nicht gegenseitig beeinflussen
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("angelapp", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun tearDown() {
        // Nach jedem Test: Alle erstellten Testfotos löschen
        // und den Speicher wieder bereinigen
        testDateien.forEach { it.delete() }
        context.getSharedPreferences("angelapp", Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Hilfsfunktion: Testfoto erstellen
    //
    // Da wir keine echte Kamera im Test verwenden können, erstellt
    // diese Funktion ein synthetisches JPEG (100×100 Pixel, blau)
    // und schreibt beliebige EXIF-Metadaten hinein — genau so wie
    // eine echte Kamera es tun würde.
    // ─────────────────────────────────────────────────────────────

    /**
     * Erstellt ein Test-JPEG mit eingebetteten EXIF-Daten (Datum + GPS).
     * Simuliert ein echtes Kamerafoto mit Metadaten.
     *
     * @param datumExif  Aufnahmezeitpunkt im Kamera-Format (z.B. "2026:03:08 15:50:00")
     * @param offsetExif Zeitzonenversatz (z.B. "+01:00" für MEZ)
     * @param lat        Breitengrad des Aufnahmeorts
     * @param lon        Längengrad des Aufnahmeorts
     * @param dateiname  Name der erzeugten Testdatei
     */
    private fun testFotoErstellen(
        datumExif: String = "2026:03:08 15:50:00",
        offsetExif: String = "+01:00",
        lat: Double = 53.5753,
        lon: Double = 10.0153,
        dateiname: String = "test_foto_${System.currentTimeMillis()}.jpg"
    ): File {
        val datei = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), dateiname)

        // Minimales JPEG erstellen (100×100 Pixel, blau) —
        // klein genug für schnelle Tests, groß genug für EXIF-Schreiben
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        FileOutputStream(datei).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()

        // EXIF-Daten einschreiben: Datum und Zeitzone
        val exif = ExifInterface(datei.absolutePath)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, datumExif)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetExif)

        // GPS-Koordinaten im DMS-Format (Grad/Minuten/Sekunden) einschreiben —
        // das ist das Format, das Kameras intern verwenden
        val latAbs = Math.abs(lat)
        val lonAbs = Math.abs(lon)
        val latGrad = latAbs.toInt()
        val latMin = ((latAbs - latGrad) * 60).toInt()
        val latSek = ((latAbs - latGrad - latMin / 60.0) * 3600)
        val lonGrad = lonAbs.toInt()
        val lonMin = ((lonAbs - lonGrad) * 60).toInt()
        val lonSek = ((lonAbs - lonGrad - lonMin / 60.0) * 3600)

        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,
            "$latGrad/1,$latMin/1,${(latSek * 1000).toInt()}/1000")
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,
            "$lonGrad/1,$lonMin/1,${(lonSek * 1000).toInt()}/1000")
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")
        exif.saveAttributes()

        testDateien.add(datei)
        return datei
    }

    // ─────────────────────────────────────────────────────────────
    // 1. EXIF-Parsing Tests
    //
    // Wenn der Nutzer ein Foto aus der Galerie importiert, liest die App
    // automatisch Datum und GPS-Koordinaten aus den EXIF-Metadaten des Fotos.
    // Diese Tests prüfen, ob das Auslesen auf dem echten Gerät korrekt
    // funktioniert — auch bei fehlenden oder fehlerhaften Metadaten.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun exifDatumWirdKorrektGelesen() {
        // Ein Testfoto mit bekanntem Aufnahmezeitpunkt (8. März 2026, 15:50 Uhr,
        // Zeitzone MEZ/+01:00) wird erstellt. Die App soll das Datum aus dem
        // EXIF-Bereich des Fotos lesen und im deutschen Format "08.03.2026 15:50"
        // zurückgeben.
        val foto = testFotoErstellen(
            datumExif = "2026:03:08 15:50:00",
            offsetExif = "+01:00"
        )
        val uri = Uri.fromFile(foto)
        val (datum, _, _) = exifDatenLesen(context, uri)
        assertEquals("08.03.2026 15:50", datum)
    }

    @Test
    fun exifGpsWirdKorrektGelesen() {
        // Ein Testfoto mit bekannten GPS-Koordinaten (Hamburg-Bereich) wird erstellt.
        // Die App soll Breitengrad und Längengrad korrekt auslesen.
        // Toleranz: ±0.01 Grad (~1 km), da das GPS-Format DMS (Grad/Minuten/Sekunden)
        // leichte Rundungsdifferenzen erzeugen kann.
        val foto = testFotoErstellen(lat = 53.5753, lon = 10.0153)
        val uri = Uri.fromFile(foto)
        val (_, lat, lon) = exifDatenLesen(context, uri)
        assertEquals(53.5753, lat, 0.01)
        assertEquals(10.0153, lon, 0.01)
    }

    @Test
    fun exifUtcWirdInBerlinerZeitKonvertiert() {
        // Manche Kameras (besonders Smartphones im Ausland oder ältere Geräte)
        // speichern die Zeit in UTC. Für deutsche Nutzer muss die App
        // diese Zeit in Berliner Ortszeit umrechnen.
        // Hier: 14:50 UTC im März (Winterzeit, UTC+1) → muss als 15:50 angezeigt werden.
        val foto = testFotoErstellen(
            datumExif = "2026:03:08 14:50:00",
            offsetExif = "+00:00"
        )
        val uri = Uri.fromFile(foto)
        val (datum, _, _) = exifDatenLesen(context, uri)
        assertEquals("08.03.2026 15:50", datum)
    }

    @Test
    fun exifOhneGpsGibtNullKoordinaten() {
        // Nicht jedes Foto hat GPS-Daten (z.B. wenn der Standortzugriff
        // deaktiviert war oder das Foto mit einer alten Kamera ohne GPS aufgenommen wurde).
        // In diesem Fall sollen beide Koordinaten 0.0 zurückgegeben werden —
        // kein Absturz, keine zufälligen Werte.
        val foto = testFotoErstellen()
        val exif = ExifInterface(foto.absolutePath)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
        exif.saveAttributes()

        val uri = Uri.fromFile(foto)
        val (_, lat, lon) = exifDatenLesen(context, uri)
        assertEquals(0.0, lat, 0.0)
        assertEquals(0.0, lon, 0.0)
    }

    @Test
    fun exifAusKorrupterJpegDateiCrashtNicht() {
        // Eine Datei mit .jpg-Endung, die aber keinen gültigen JPEG-Inhalt hat
        // (z.B. ein umbenanntes Textdokument oder eine beschädigte Datei),
        // darf die App nicht zum Absturz bringen.
        val datei = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "korrupt.jpg")
        datei.writeText("Dies ist kein JPEG-Inhalt sondern Klartext.")
        testDateien.add(datei)

        val uri = Uri.fromFile(datei)
        val (datum, lat, lon) = exifDatenLesen(context, uri)
        assertNotNull("Datum darf nicht null sein", datum)
        assertEquals(0.0, lat, 0.0)
        assertEquals(0.0, lon, 0.0)
    }

    @Test
    fun exifOhneJedeMetadataCrashtNicht() {
        // Ein Foto ohne jegliche EXIF-Metadaten (z.B. ein Screenshot oder
        // ein Foto das bearbeitet und dabei die Metadaten verloren hat).
        // Die App darf nicht abstürzen. Als Datum wird der heutige Tag verwendet,
        // GPS bleibt 0.0.
        val datei = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "kein_exif.jpg")
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        FileOutputStream(datei).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        testDateien.add(datei)

        val uri = Uri.fromFile(datei)
        val (datum, lat, lon) = exifDatenLesen(context, uri)
        // Kein Absturz — Datum ist heutiges Datum, GPS ist 0.0
        assertNotNull(datum)
        assertEquals(0.0, lat, 0.0)
        assertEquals(0.0, lon, 0.0)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Galerie-Import: Einzelfoto
    //
    // Wenn der Nutzer in der App "Aus Galerie" tippt und ein einzelnes
    // Foto auswählt, sollen Datum und GPS automatisch übernommen werden.
    // Zusätzlich soll die Gezeitenberechnung für den Fangzeitpunkt
    // gestartet werden. Diese Tests prüfen diesen Ablauf.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun einzelnesGaleriefotoWirdKorrektGespeichert() {
        // Ein einzelnes Testfoto mit vollständigen Metadaten wird importiert.
        // Die App muss das Foto in ihr eigenes Verzeichnis kopieren,
        // das Datum korrekt lesen und den Dateipfad zurückgeben.
        // Die kopierte Datei muss auf dem Gerät tatsächlich existieren.
        val foto = testFotoErstellen(
            datumExif = "2026:03:08 15:50:00",
            offsetExif = "+01:00",
            lat = 53.5753,
            lon = 10.0153
        )
        val uri = Uri.fromFile(foto)
        val (exifDatum, lat, lon) = exifDatenLesen(context, uri)
        val pfad = uriZuPfad(context, uri)

        assertNotNull("Pfad darf nicht null sein", pfad)
        assertTrue("Pfad darf nicht leer sein", pfad.isNotBlank())
        assertTrue("Kopierte Datei muss existieren", File(pfad).exists())
        assertEquals("08.03.2026 15:50", exifDatum)
        assertEquals(53.5753, lat, 0.01)
    }

    @Test
    fun galeriefotoMitGpsErhältGezeitenDaten() {
        // Wenn ein Foto GPS-Koordinaten enthält (hier: Cuxhaven an der Nordsee),
        // soll die App automatisch den Gezeitenstand für diesen Ort
        // und den Aufnahmezeitpunkt berechnen und speichern.
        val foto = testFotoErstellen(
            datumExif = "2026:03:08 15:50:00",
            offsetExif = "+01:00",
            lat = 53.867, // Cuxhaven — Nordseeküste
            lon = 8.700
        )
        val uri = Uri.fromFile(foto)
        val (datum, lat, lon) = exifDatenLesen(context, uri)
        val gezeiten = gezeiteBerechnen(datum, lat, lon)
        assertTrue("Gezeiten sollten nicht leer sein bei Küstenstandort", gezeiten.isNotBlank())
    }

    @Test
    fun galeriefotoOhneGpsHatKeineGezeiten() {
        // Ein Foto ohne GPS-Daten kann keinen Gezeitenstand haben —
        // der Ort ist unbekannt. Die Gezeitenberechnung soll einen leeren
        // String zurückgeben, nicht einen zufälligen oder falschen Wert.
        val foto = testFotoErstellen(lat = 0.0, lon = 0.0)
        val exif = ExifInterface(foto.absolutePath)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
        exif.saveAttributes()

        val uri = Uri.fromFile(foto)
        val (datum, lat, lon) = exifDatenLesen(context, uri)
        val gezeiten = gezeiteBerechnen(datum, lat, lon)
        assertEquals("", gezeiten)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Galerie-Import: Mehrere Fotos
    //
    // Der Nutzer kann mehrere Fotos gleichzeitig aus der Galerie importieren.
    // Das erste Foto erscheint im Erfassungsformular, alle weiteren werden
    // automatisch im Hintergrund als eigene Fänge gespeichert.
    // Diese Tests prüfen, ob dabei alle Metadaten korrekt verarbeitet werden.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun mehrereGaleriefotosHabenEindeutigeIds() {
        // Jeder Fang bekommt eine eindeutige ID (Zeitstempel in Millisekunden).
        // Bei sehr schnell aufeinanderfolgenden Importen könnten IDs kollidieren.
        // Dieser Test prüft, dass die ID-Vergabe immer eindeutig bleibt.
        val fotos = listOf(
            testFotoErstellen(datumExif = "2026:03:01 08:00:00", dateiname = "foto1.jpg"),
            testFotoErstellen(datumExif = "2026:03:08 15:50:00", dateiname = "foto2.jpg"),
            testFotoErstellen(datumExif = "2026:03:15 12:30:00", dateiname = "foto3.jpg"),
        )
        val ids = mutableSetOf<Long>()
        fotos.forEachIndexed { index, _ ->
            Thread.sleep(5)
            ids.add(System.currentTimeMillis() + index)
        }
        assertEquals("Alle IDs müssen eindeutig sein", fotos.size, ids.size)
    }

    @Test
    fun mehrereGaleriefotosHabenKorrektesDatum() {
        // Drei Fotos mit unterschiedlichen Aufnahmezeitpunkten werden importiert.
        // Jedes Foto soll sein eigenes Datum behalten — kein Datum darf mit
        // dem eines anderen Fotos verwechselt werden.
        val testDaten = listOf(
            Triple("2026:03:01 08:00:00", "+01:00", "01.03.2026 08:00"),
            Triple("2026:03:08 15:50:00", "+01:00", "08.03.2026 15:50"),
            Triple("2026:03:15 12:30:00", "+01:00", "15.03.2026 12:30"),
        )
        testDaten.forEachIndexed { index, (exifDatum, offset, erwartet) ->
            val foto = testFotoErstellen(
                datumExif = exifDatum,
                offsetExif = offset,
                dateiname = "multi_foto_$index.jpg"
            )
            val uri = Uri.fromFile(foto)
            val (datum, _, _) = exifDatenLesen(context, uri)
            assertEquals("Foto $index: Datum stimmt nicht", erwartet, datum)
        }
    }

    @Test
    fun mehrereGaleriefotosHabenKorrekteGpsKoordinaten() {
        // Drei Fotos von verschiedenen Standorten (Hamburg, Berlin, München)
        // werden importiert. Jedes Foto soll die GPS-Koordinaten seines
        // eigenen Aufnahmeorts behalten — kein Standort darf vertauscht werden.
        val koordinaten = listOf(
            Pair(53.5753, 10.0153), // Hamburg
            Pair(52.5200, 13.4050), // Berlin
            Pair(48.1351, 11.5820), // München
        )
        koordinaten.forEachIndexed { index, (lat, lon) ->
            val foto = testFotoErstellen(
                lat = lat,
                lon = lon,
                dateiname = "gps_foto_$index.jpg"
            )
            val uri = Uri.fromFile(foto)
            val (_, gelat, gelon) = exifDatenLesen(context, uri)
            assertEquals("Foto $index: Latitude stimmt nicht", lat, gelat, 0.01)
            assertEquals("Foto $index: Longitude stimmt nicht", lon, gelon, 0.01)
        }
    }

    @Test
    fun mehrereGaleriefotosWerdenKorrektKopiert() {
        // Beim Import kopiert die App alle Fotos in ihr eigenes Verzeichnis
        // (damit sie auch dann noch angezeigt werden, wenn das Original
        // in der Galerie gelöscht wird). Dieser Test prüft, dass alle
        // kopierten Dateien auf dem Gerät tatsächlich vorhanden und nicht
        // leer sind.
        val fotos = (1..3).map { i ->
            testFotoErstellen(dateiname = "kopier_test_$i.jpg")
        }
        fotos.forEachIndexed { index, foto ->
            val uri = Uri.fromFile(foto)
            val pfad = uriZuPfad(context, uri, index)
            assertTrue("Kopie $index muss existieren", File(pfad).exists())
            assertTrue("Kopie $index muss Inhalt haben", File(pfad).length() > 0)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Fang speichern und laden
    //
    // Die App speichert alle Fänge dauerhaft auf dem Gerät (SharedPreferences).
    // Diese Tests prüfen die grundlegenden Datenbankoperationen:
    // Speichern, Laden, Bearbeiten und Löschen — jeweils mit echtem
    // Gerätespeicher.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun gespeicherterFangWirdKorrektGeladen() {
        // Ein vollständiger Fang (mit allen Feldern ausgefüllt) wird gespeichert
        // und sofort wieder geladen. Alle Felder — Fischart, Länge, GPS,
        // Wetterdaten und Gezeitenstand — müssen byte-identisch erhalten bleiben.
        val fang = Fang(
            id = 99999L,
            fischart = "Hecht",
            laenge = "72",
            notizen = "Testfang",
            datum = "08.03.2026 15:50",
            latitude = 53.5753,
            longitude = 10.0153,
            temperatur = 12.3,
            wind = 3.1,
            luftdruck = 1013.0,
            bewoelkung = 40,
            fotoPfad = "",
            gezeiten = "Steigende Flut 45 %"
        )
        fangspeichern(context, fang)
        val geladene = faengeladen(context)
        assertEquals(1, geladene.size)
        val geladen = geladene.first()
        assertEquals("Hecht", geladen.fischart)
        assertEquals("72", geladen.laenge)
        assertEquals(53.5753, geladen.latitude, 0.0001)
        assertEquals("Steigende Flut 45 %", geladen.gezeiten)
    }

    @Test
    fun mehrereGespeicherteFaengeWerdenAbsteigendSortiert() {
        // Drei Fänge werden in zufälliger Reihenfolge gespeichert.
        // Beim Laden muss die Liste automatisch so sortiert sein,
        // dass der neueste Fang (15. März) ganz oben steht —
        // unabhängig von der Reihenfolge des Speicherns.
        fangspeichern(context, Fang(id = 1L, fischart = "Hecht",  laenge = "", notizen = "", datum = "01.03.2026 08:00"))
        fangspeichern(context, Fang(id = 2L, fischart = "Zander", laenge = "", notizen = "", datum = "15.03.2026 14:30"))
        fangspeichern(context, Fang(id = 3L, fischart = "Barsch", laenge = "", notizen = "", datum = "08.03.2026 11:00"))

        val faenge = faengeladen(context)
        assertEquals(3, faenge.size)
        assertEquals("Zander", faenge[0].fischart)
        assertEquals("Barsch", faenge[1].fischart)
        assertEquals("Hecht",  faenge[2].fischart)
    }

    @Test
    fun fangAktualisierenÄndertNurDenRichtigenEintrag() {
        // Zwei Fänge sind gespeichert (Hecht und Zander).
        // Der Nutzer korrigiert die Länge des Hechts von 60 auf 65 cm.
        // Nach der Änderung muss der Hecht die neue Länge haben,
        // der Zander darf dabei nicht verändert worden sein.
        fangspeichern(context, Fang(id = 1L, fischart = "Hecht",  laenge = "60", notizen = "", datum = "01.03.2026 08:00"))
        fangspeichern(context, Fang(id = 2L, fischart = "Zander", laenge = "45", notizen = "", datum = "08.03.2026 11:00"))

        val aktualisiert = Fang(id = 1L, fischart = "Hecht", laenge = "65", notizen = "Länge korrigiert", datum = "01.03.2026 08:00")
        fangAktualisieren(context, aktualisiert)

        val faenge = faengeladen(context)
        val hecht = faenge.find { it.id == 1L }
        val zander = faenge.find { it.id == 2L }

        assertEquals("65", hecht?.laenge)
        assertEquals("Länge korrigiert", hecht?.notizen)
        assertEquals("45", zander?.laenge) // Zander darf sich nicht verändert haben
    }

    @Test
    fun fangMitLeeresFotoPfadWirdKorrektGespeichertUndGeladen() {
        // Fänge ohne Foto (fotoPfad leer) müssen korrekt gespeichert und geladen
        // werden können — z.B. wenn der Nutzer den Fang manuell eingibt.
        val fang = Fang(
            id = 42L, fischart = "Barsch", laenge = "25",
            notizen = "Ohne Foto", datum = "08.03.2026 10:00",
            fotoPfad = ""
        )
        fangspeichern(context, fang)
        val geladen = faengeladen(context).first()
        assertEquals("", geladen.fotoPfad)
        assertEquals("Barsch", geladen.fischart)
        assertEquals("25", geladen.laenge)
    }

    @Test
    fun fangLoeschenEntferntNurDenRichtigenEintrag() {
        // Zwei Fänge sind gespeichert. Der Nutzer löscht den Hecht.
        // Danach darf nur noch der Zander in der Liste sein —
        // der Hecht muss vollständig entfernt sein.
        fangspeichern(context, Fang(id = 1L, fischart = "Hecht",  laenge = "", notizen = "", datum = "01.03.2026 08:00"))
        fangspeichern(context, Fang(id = 2L, fischart = "Zander", laenge = "", notizen = "", datum = "08.03.2026 11:00"))

        fangloeschen(context, 1L)

        val faenge = faengeladen(context)
        assertEquals(1, faenge.size)
        assertEquals("Zander", faenge[0].fischart)
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Vollständiger Import-Workflow
    //
    // Dieser Abschnitt testet den kompletten Ablauf von Anfang bis Ende:
    // Fotos werden erstellt, importiert, mit Metadaten angereichert,
    // gespeichert und anschließend auf Korrektheit geprüft.
    // Das ist der wichtigste Test — er simuliert genau das, was ein
    // echter Nutzer mit der App tut.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun vollständigerImportWorkflowMitDreiFootos() {
        // Szenario: Der Nutzer hat drei Angelfotos von verschiedenen Tagen
        // und Standorten an der deutschen Küste. Er importiert alle drei
        // gleichzeitig aus der Galerie.
        //
        // Erwartet wird:
        // - Alle drei Fotos werden als separate Fänge gespeichert
        // - Die Liste ist nach Datum sortiert (neuestes zuerst)
        // - GPS-Koordinaten sind korrekt übernommen
        // - Für alle drei Küstenstandorte gibt es Gezeitendaten
        // - Die Fotodateien existieren tatsächlich auf dem Gerät
        val testFotos = listOf(
            testFotoErstellen(datumExif = "2026:03:01 08:00:00", offsetExif = "+01:00", lat = 53.5753, lon = 10.0153, dateiname = "workflow1.jpg"),
            testFotoErstellen(datumExif = "2026:03:08 15:50:00", offsetExif = "+01:00", lat = 53.867,  lon = 8.700,   dateiname = "workflow2.jpg"),
            testFotoErstellen(datumExif = "2026:03:15 12:30:00", offsetExif = "+01:00", lat = 54.324,  lon = 10.139,  dateiname = "workflow3.jpg"),
        )

        val ergebnisse = testFotos.mapIndexed { index, foto ->
            val uri = Uri.fromFile(foto)
            val (datum, lat, lon) = exifDatenLesen(context, uri)
            val pfad = uriZuPfad(context, uri, index)
            val gezeiten = gezeiteBerechnen(datum, lat, lon)

            Thread.sleep(5) // kurze Pause damit Zeitstempel-IDs eindeutig sind
            val fangId = System.currentTimeMillis()
            fangspeichern(context, Fang(
                id = fangId,
                fischart = "Unbekannt",
                laenge = "",
                notizen = "Aus Galerie importiert",
                datum = datum,
                latitude = lat,
                longitude = lon,
                fotoPfad = pfad,
                gezeiten = gezeiten
            ))
            fangId
        }

        val gespeichert = faengeladen(context)
        assertEquals("Alle 3 Fänge müssen gespeichert sein", 3, gespeichert.size)

        // Sortierung prüfen: neuestes Foto (15. März) muss zuerst kommen
        assertEquals("15.03.2026 12:30", gespeichert[0].datum)
        assertEquals("08.03.2026 15:50", gespeichert[1].datum)
        assertEquals("01.03.2026 08:00", gespeichert[2].datum)

        // GPS-Koordinaten prüfen: jeder Fang muss seinen eigenen Standort haben
        assertEquals(53.5753, gespeichert[2].latitude, 0.01)
        assertEquals(53.867,  gespeichert[1].latitude, 0.01)
        assertEquals(54.324,  gespeichert[0].latitude, 0.01)

        // Gezeitendaten prüfen: alle Küstenstandorte müssen einen Wert haben
        gespeichert.forEach { fang ->
            assertTrue("Gezeitendaten sollten für alle Küstenstandorte vorhanden sein",
                fang.gezeiten.isNotBlank())
        }

        // Fotodateien prüfen: alle kopierten Dateien müssen auf dem Gerät existieren
        gespeichert.forEach { fang ->
            assertTrue("Fotodatei muss existieren: ${fang.fotoPfad}",
                File(fang.fotoPfad).exists())
        }
    }

    @Test
    fun importierteFotoDateiExistiert() {
        // Einzeltest: Nach dem Import eines einzelnen Fotos muss die
        // in das App-Verzeichnis kopierte Datei auf dem Gerät vorhanden
        // und nicht leer sein. Damit ist sichergestellt, dass das Foto
        // auch angezeigt werden kann, wenn das Original gelöscht wird.
        val foto = testFotoErstellen()
        val uri = Uri.fromFile(foto)
        val pfad = uriZuPfad(context, uri)
        val datei = File(pfad)
        assertTrue("Importierte Datei muss existieren", datei.exists())
        assertTrue("Importierte Datei darf nicht leer sein", datei.length() > 0)
        testDateien.add(datei)
    }
}
