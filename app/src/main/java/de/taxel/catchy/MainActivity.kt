package de.taxel.catchy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

private const val TAG = "Catchy"

// Hält die aktuellen Wetterdaten zu einem Fang
data class Wetter(
    val temperatur: Double = 0.0,
    val wind: Double = 0.0,
    val luftdruck: Double = 0.0,
    val bewoelkung: Int = 0
)

// Repräsentiert einen einzelnen Fangeintrag mit allen zugehörigen Daten
data class Fang(
    val id: Long = System.currentTimeMillis(), // Eindeutige ID, basierend auf dem Zeitstempel der Erstellung
    val fischart: String,
    val laenge: String,
    val notizen: String,
    val datum: String,                         // Format: "dd.MM.yyyy HH:mm"
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val temperatur: Double = 0.0,
    val wind: Double = 0.0,
    val luftdruck: Double = 0.0,
    val bewoelkung: Int = 0,                   // Bewölkung in Prozent (0–100)
    val fotoPfad: String = "",                 // Absoluter Dateipfad zum Foto, leer wenn keins vorhanden
    val gezeiten: String = "",                 // Gezeitenstand zum Fangzeitpunkt (astronomische Näherung)
    val mondphase: String = ""                 // Mondphase zum Fangzeitpunkt (astronomische Näherung)
)

// Lädt alle gespeicherten Fänge aus den SharedPreferences und gibt sie absteigend nach Datum sortiert zurück
fun faengeladen(context: Context): List<Fang> {
    val prefs = context.getSharedPreferences("angelapp", Context.MODE_PRIVATE)
    val json = prefs.getString("faenge", "[]") ?: "[]"
    val array = JSONArray(json)
    val liste = mutableListOf<Fang>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        // optDouble/optInt verwenden, damit ältere Einträge ohne diese Felder nicht crashen
        liste.add(Fang(
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
            gezeiten = obj.optString("gezeiten", ""),
            mondphase = obj.optString("mondphase", "")
        ))
    }
    // Neueste Fänge zuerst; bei ungültigem Datum wird epoch 0 als Fallback verwendet
    return liste.sortedByDescending {
        try {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).parse(it.datum)
        } catch (@Suppress("SwallowedException") e: java.text.ParseException) { Date(0) }
    }
}

// Hängt einen neuen Fang an die bestehende JSON-Liste in den SharedPreferences an
fun fangspeichern(context: Context, fang: Fang) {
    val prefs = context.getSharedPreferences("angelapp", Context.MODE_PRIVATE)
    val json = prefs.getString("faenge", "[]") ?: "[]"
    val array = JSONArray(json)
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
    obj.put("mondphase", fang.mondphase)
    array.put(obj)
    prefs.edit().putString("faenge", array.toString()).apply()
}

// Ersetzt einen bestehenden Fang (gleiche ID) durch die aktualisierte Version
fun fangAktualisieren(context: Context, fang: Fang) {
    val prefs = context.getSharedPreferences("angelapp", Context.MODE_PRIVATE)
    val json = prefs.getString("faenge", "[]") ?: "[]"
    val array = JSONArray(json)
    val neu = JSONArray()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        if (obj.getLong("id") == fang.id) { // Treffer: alten Eintrag durch neuen ersetzen
            val updated = JSONObject()
            updated.put("id", fang.id)
            updated.put("fischart", fang.fischart)
            updated.put("laenge", fang.laenge)
            updated.put("notizen", fang.notizen)
            updated.put("datum", fang.datum)
            updated.put("latitude", fang.latitude)
            updated.put("longitude", fang.longitude)
            updated.put("temperatur", fang.temperatur)
            updated.put("wind", fang.wind)
            updated.put("luftdruck", fang.luftdruck)
            updated.put("bewoelkung", fang.bewoelkung)
            updated.put("fotoPfad", fang.fotoPfad)
            updated.put("gezeiten", fang.gezeiten)
            updated.put("mondphase", fang.mondphase)
            neu.put(updated)
        } else neu.put(obj)
    }
    prefs.edit().putString("faenge", neu.toString()).apply()
}

// Entfernt den Fang mit der angegebenen ID aus den SharedPreferences
fun fangloeschen(context: Context, id: Long) {
    val prefs = context.getSharedPreferences("angelapp", Context.MODE_PRIVATE)
    val json = prefs.getString("faenge", "[]") ?: "[]"
    val array = JSONArray(json)
    val neu = JSONArray()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        if (obj.getLong("id") != id) neu.put(obj)
    }
    prefs.edit().putString("faenge", neu.toString()).apply()
}

// Exportiert alle Fänge als JSON-Datei ins externe Dokumentenverzeichnis und gibt eine teilbare URI zurück
fun datenExportieren(context: Context): Uri? {
    return try {
        val prefs = context.getSharedPreferences("angelapp", Context.MODE_PRIVATE)
        val json = prefs.getString("faenge", "[]") ?: "[]"
        val zeitstempel = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
        val datei = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "catchy_backup_${zeitstempel}.json")
        datei.writeText(json)
        FileProvider.getUriForFile(context, "de.taxel.catchy.fileprovider", datei)
    } catch (e: java.io.IOException) { android.util.Log.e(TAG, "Export fehlgeschlagen", e); null }
}

// Importiert Fänge aus einer JSON-Datei. Bereits vorhandene IDs werden übersprungen (kein Duplikat).
// Rückgabe: Anzahl neu importierter Fänge, 0 wenn keine neuen, -1 bei Fehler
@Suppress("TooGenericExceptionCaught")
fun datenImportieren(context: Context, uri: Uri): Int {
    return try {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return 0
        val array = JSONArray(json)
        val prefs = context.getSharedPreferences("angelapp", Context.MODE_PRIVATE)
        val bestehend = JSONArray(prefs.getString("faenge", "[]") ?: "[]")
        // Bestehende IDs in ein Set laden für schnellen Duplikat-Check
        val bestehendeIds = mutableSetOf<Long>()
        for (i in 0 until bestehend.length()) bestehendeIds.add(bestehend.getJSONObject(i).getLong("id"))
        var importiert = 0
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (!bestehendeIds.contains(obj.getLong("id"))) {
                bestehend.put(obj)
                importiert++
            }
        }
        prefs.edit().putString("faenge", bestehend.toString()).apply()
        importiert
    } catch (e: Exception) { android.util.Log.e(TAG, "Import fehlgeschlagen", e); -1 }
}

// Exportiert alle Fänge mit GPS-Koordinaten als GPX-Waypoint-Datei und gibt eine teilbare URI zurück
fun gpxExportieren(context: Context, faenge: List<Fang>): Uri? {
    return try {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Catchy" xmlns="http://www.topografix.com/GPX/1/1">""")
        // Nur Fänge mit gültigem GPS-Eintrag exportieren
        val fangeMitGps = faenge.filter { it.latitude != 0.0 }
        for (fang in fangeMitGps) {
            val lat = String.format(Locale.US, "%.6f", fang.latitude)
            val lon = String.format(Locale.US, "%.6f", fang.longitude)
            val name = fang.fischart.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val beschreibung = buildString {
                append(fang.datum)
                if (fang.laenge.isNotBlank()) append(" | ${fang.laenge} cm")
                if (fang.temperatur != 0.0) append(" | ${fang.temperatur}°C")
                if (fang.wind != 0.0) append(" | Wind ${fang.wind} m/s")
            }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sb.appendLine("""  <wpt lat="$lat" lon="$lon">""")
            sb.appendLine("""    <name>$name</name>""")
            sb.appendLine("""    <desc>$beschreibung</desc>""")
            sb.appendLine("""    <sym>Fishing Hot Spot Facility</sym>""")
            sb.appendLine("""  </wpt>""")
        }
        sb.appendLine("""</gpx>""")
        val zeitstempel = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
        val datei = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "catchy_fangplaetze_${zeitstempel}.gpx")
        datei.writeText(sb.toString())
        FileProvider.getUriForFile(context, "de.taxel.catchy.fileprovider", datei)
    } catch (e: java.io.IOException) { android.util.Log.e(TAG, "GPX Export fehlgeschlagen", e); null }
}

// Ruft das aktuelle Wetter für die angegebenen Koordinaten von der Open-Meteo-API ab (Hintergrund-Thread)
@Suppress("TooGenericExceptionCaught")
fun wetterAbrufen(lat: Double, lon: Double, onErgebnis: (Wetter?) -> Unit) {
    thread {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon&" +
                "current=temperature_2m,wind_speed_10m,surface_pressure,cloud_cover&wind_speed_unit=ms"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            onErgebnis(Wetter(
                temperatur = current.getDouble("temperature_2m"),
                wind = current.getDouble("wind_speed_10m"),
                luftdruck = current.getDouble("surface_pressure"),
                bewoelkung = current.getInt("cloud_cover")
            ))
        } catch (e: Exception) { android.util.Log.e(TAG, "Wetter abrufen fehlgeschlagen", e); onErgebnis(null) }
    }
}

// Ruft historische Wetterdaten für ein bestimmtes Datum von der Open-Meteo-Archiv-API ab (Hintergrund-Thread).
// Index 12 entspricht dem Stundenwert um 12:00 Uhr Mittags als repräsentativer Tageswert.
@Suppress("TooGenericExceptionCaught")
fun historischesWetterAbrufen(lat: Double, lon: Double, datum: String, onErgebnis: (Wetter?) -> Unit) {
    thread {
        try {
            // Datum von "dd.MM.yyyy HH:mm" in "yyyy-MM-dd" für die API umformatieren
            val datumFormatiert = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).parse(datum) ?: Date()
            )
            val url = "https://archive-api.open-meteo.com/v1/archive?" +
                "latitude=$lat&longitude=$lon&" +
                "start_date=$datumFormatiert&end_date=$datumFormatiert&" +
                "hourly=temperature_2m,wind_speed_10m,surface_pressure,cloud_cover&wind_speed_unit=ms"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val hourly = json.getJSONObject("hourly")
            onErgebnis(Wetter(
                temperatur = hourly.getJSONArray("temperature_2m").getDouble(12),
                wind = hourly.getJSONArray("wind_speed_10m").getDouble(12),
                luftdruck = hourly.getJSONArray("surface_pressure").getDouble(12),
                bewoelkung = hourly.getJSONArray("cloud_cover").getInt(12)
            ))
        } catch (e: Exception) { android.util.Log.e(TAG, "Historisches Wetter abrufen fehlgeschlagen", e); onErgebnis(null) }
    }
}

// Liest Datum und GPS-Koordinaten aus den EXIF-Daten eines Fotos.
// Rückgabe: Triple(datum, latitude, longitude) — bei fehlenden Daten leerer String bzw. 0.0
// Berechnet den Gezeitenstand zum angegebenen Zeitpunkt und Standort.
// Verwendet eine astronomische Näherung (Mondphase + semi-diurnale Periode).
// Genauigkeit: ±1–3 Stunden je nach Standort (kein API-Key benötigt, funktioniert offline).
// Gibt "" zurück wenn kein GPS vorhanden.
fun gezeiteBerechnen(datum: String, lat: Double, lon: Double): String {
    if (lat == 0.0 && lon == 0.0) return ""
    return try {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        sdf.timeZone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val date = sdf.parse(datum) ?: return ""
        val t = date.time / 1000.0

        // Mondphase: Tage seit bekanntem Neumond (6. Jan 2000, 18:14 UTC)
        val refNeumond = 947182440.0
        val lunarPeriodSec = 29.53058867 * 86400.0
        var mondAlterTage = ((t - refNeumond) % lunarPeriodSec) / 86400.0
        if (mondAlterTage < 0) mondAlterTage += 29.53058867

        // Springtide (Voll-/Neumond) oder Niptide (Viertel)
        val tideTyp = when {
            mondAlterTage <= 1.5 || mondAlterTage >= 28.0 -> "Springtide"
            mondAlterTage in 13.5..15.5 -> "Springtide"
            mondAlterTage in 6.5..8.5 -> "Niptide"
            mondAlterTage in 21.0..23.0 -> "Niptide"
            else -> ""
        }

        // Semi-diurnale Gezeitenphase (T = 12h 25min 14s = 44714s)
        // Mittlerer Mondtag = 24h 50min 28s = 89428s → Phasenoffset nach Längengrad
        // Phase: 0 = Hochwasser, 0.5 = Niedrigwasser, 1 = Hochwasser
        val tidePeriodSec = 44714.0
        val mondTagSec = 89428.0
        val lonOffset = (lon / 360.0) * mondTagSec
        val normPhase = ((t + lonOffset) % tidePeriodSec).let { if (it < 0) it + tidePeriodSec else it } / tidePeriodSec

        val (sekBis, ereignis) = if (normPhase < 0.5) {
            Pair((0.5 - normPhase) * tidePeriodSec, "Ebbe")
        } else {
            Pair((1.0 - normPhase) * tidePeriodSec, "Flut")
        }

        val stunden = Math.round(sekBis / 3600.0).toInt()
        val stand = when {
            stunden == 0 -> if (ereignis == "Flut") "Hochwasser" else "Niedrigwasser"
            stunden == 1 -> "1 Stunde bis $ereignis"
            else -> "$stunden Stunden bis $ereignis"
        }

        if (tideTyp.isNotBlank()) "$stand · $tideTyp" else stand
    } catch (@Suppress("SwallowedException") e: java.text.ParseException) { "" }
}

// Berechnet Tage bis zum nächsten Vollmond oder Neumond (astronomische Näherung, kein Internet nötig)
fun mondphaseBerechnen(datum: String): String {
    return try {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        sdf.timeZone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val date = sdf.parse(datum) ?: return ""
        val t = date.time / 1000.0

        // Mondphase: Tage seit bekanntem Neumond (6. Jan 2000, 18:14 UTC)
        val refNeumond = 947182440.0
        val lunarPeriodSec = 29.53058867 * 86400.0
        var mondAlterTage = ((t - refNeumond) % lunarPeriodSec) / 86400.0
        if (mondAlterTage < 0) mondAlterTage += 29.53058867

        // Vollmond bei Tag ~14.77, Neumond bei Tag 0/29.53
        val bisVollmond = 14.77 - mondAlterTage
        val bisNeumond  = if (mondAlterTage < 14.77) mondAlterTage else 29.53058867 - mondAlterTage

        when {
            mondAlterTage < 1.0 || mondAlterTage > 28.53 -> "Neumond"
            mondAlterTage in 14.27..15.27                 -> "Vollmond"
            bisVollmond > 0 -> "${bisVollmond.toInt() + 1} Tage bis Vollmond"
            else            -> "${bisNeumond.toInt() + 1} Tage bis Neumond"
        }
    } catch (@Suppress("SwallowedException") e: java.text.ParseException) { "" }
}

@Suppress("TooGenericExceptionCaught")
fun exifDatenLesen(context: Context, uri: Uri): Triple<String, Double, Double> {
    try {
        // Ab Android 10 (Q) wird das Original-Asset benötigt, um EXIF-GPS-Daten lesen zu können
        val photoUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                MediaStore.setRequireOriginal(uri)
            } catch (e: Exception) { android.util.Log.e(TAG, "setRequireOriginal fehlgeschlagen", e); uri }
        } else uri
        // Fallback auf normale URI falls setRequireOriginal fehlschlägt
        val stream = try {
            context.contentResolver.openInputStream(photoUri)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "openInputStream fehlgeschlagen", e)
            context.contentResolver.openInputStream(uri)
        } ?: return Triple("", 0.0, 0.0)
        val exif = ExifInterface(stream)
        val latLon = FloatArray(2)
        val hatGps = exif.getLatLong(latLon)
        val lat = if (hatGps) latLon[0].toDouble() else 0.0
        val lon = if (hatGps) latLon[1].toDouble() else 0.0
        // TAG_DATETIME_ORIGINAL bevorzugen (Aufnahmezeitpunkt), TAG_DATETIME als Fallback
        val datumRoh = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME) ?: ""
        // Zeitzone aus EXIF auslesen, um korrekte Berliner Ortszeit zu berechnen
        val offsetRoh = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME) ?: ""
        val datum = if (datumRoh.isNotBlank()) {
            try {
                val eingang = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY)
                if (offsetRoh.isNotBlank()) eingang.timeZone = java.util.TimeZone.getTimeZone("GMT$offsetRoh")
                val ausgang = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
                ausgang.timeZone = java.util.TimeZone.getTimeZone("Europe/Berlin")
                ausgang.format(eingang.parse(datumRoh) ?: Date())
            } catch (e: Exception) {
                android.util.Log.e(TAG, "EXIF Datum parsen fehlgeschlagen", e)
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
            }
        } else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
        stream.close()
        return Triple(datum, lat, lon)
    } catch (e: Exception) { android.util.Log.e(TAG, "EXIF lesen fehlgeschlagen", e); return Triple("", 0.0, 0.0) }
}

// Erstellt eine leere temporäre JPG-Datei im externen Bilderverzeichnis für die Kamera-Aufnahme
fun fotoDateiErstellen(context: Context): File {
    val zeitstempel = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
    return File.createTempFile("FANG_${zeitstempel}_", ".jpg", context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))
}

// Kopiert ein Bild von einer Content-URI in das app-eigene Bilderverzeichnis und gibt den absoluten Pfad zurück.
// suffix verhindert Namenskollisionen beim gleichzeitigen Import mehrerer Fotos.
@Suppress("TooGenericExceptionCaught")
fun uriZuPfad(context: Context, uri: Uri, suffix: Int = 0): String {
    val maxSeite = 1920
    return try {
        val zeitstempel = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
        val zielDatei = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMPORT_${zeitstempel}_${suffix}.jpg")

        // Originalgröße auslesen ohne Pixel zu laden
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }

        // inSampleSize: grobe Vorverkleinerung im Speicher bevor das volle Bild geladen wird
        var sampleSize = 1
        var w = bounds.outWidth; var h = bounds.outHeight
        while (w > maxSeite * 2 || h > maxSeite * 2) { sampleSize *= 2; w /= 2; h /= 2 }

        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        } ?: return ""

        // Feinskalierung auf maxSeite px (längste Seite), Seitenverhältnis bleibt erhalten
        val skaliert = if (bitmap.width > maxSeite || bitmap.height > maxSeite) {
            val faktor = maxSeite.toFloat() / maxOf(bitmap.width, bitmap.height)
            val nw = (bitmap.width * faktor).toInt()
            val nh = (bitmap.height * faktor).toInt()
            android.graphics.Bitmap.createScaledBitmap(bitmap, nw, nh, true).also { bitmap.recycle() }
        } else bitmap

        zielDatei.outputStream().use { skaliert.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        skaliert.recycle()
        zielDatei.absolutePath
    } catch (e: Exception) { android.util.Log.e(TAG, "URI zu Pfad fehlgeschlagen", e); "" }
}

// Erkennt die Fischart auf einem Foto mithilfe des lokalen ML Kit TFLite-Modells (model.tflite in Assets).
// Gibt den erkannten Label-Text zurück, oder einen leeren String wenn nichts erkannt wurde.
@Suppress("TooGenericExceptionCaught")
fun fischartErkennen(bildPfad: String, onErgebnis: (String) -> Unit) {
    thread {
        try {
            val modellOptionen = com.google.mlkit.common.model.LocalModel.Builder()
                .setAssetFilePath("model.tflite")
                .build()
            val optionen = com.google.mlkit.vision.label.custom.CustomImageLabelerOptions.Builder(modellOptionen)
                .setConfidenceThreshold(0.5f)
                .setMaxResultCount(1)
                .build()
            val labeler = com.google.mlkit.vision.label.ImageLabeling.getClient(optionen)
            val bitmap = android.graphics.BitmapFactory.decodeFile(bildPfad)
            val bild = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            labeler.process(bild)
                .addOnSuccessListener { labels ->
                    if (labels.isNotEmpty()) onErgebnis(labels[0].text) else onErgebnis("")
                }
                .addOnFailureListener { onErgebnis("") }
        } catch (e: Exception) { android.util.Log.e(TAG, "Fischart erkennen fehlgeschlagen", e); onErgebnis("") }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        setContent { MaterialTheme { AngelApp() } }
    }
}

// Haupt-Composable: verwaltet die Navigation zwischen den Screens der App
@Composable
fun AngelApp() {
    var screen by remember { mutableStateOf("liste") }
    var aktualisierung by remember { mutableStateOf(0) }
    var kartenFang by remember { mutableStateOf<Fang?>(null) }
    var bearbeitenFang by remember { mutableStateOf<Fang?>(null) }
    var vorherKartenScreen by remember { mutableStateOf("liste") }
    var scrollZuFangId by remember { mutableStateOf<Long?>(null) }
    var scrollZuBearbeiteterFangId by remember { mutableStateOf<Long?>(null) }
    when (screen) {
        "karte" -> key(aktualisierung) {
            FangKarte(zurueck = { screen = vorherKartenScreen }, zentriereFang = kartenFang)
        }
        "bearbeiten" -> bearbeitenFang?.let { fang ->
            FangBearbeiten(fang = fang, zeigeListeFuer = { fangId ->
                scrollZuBearbeiteterFangId = fangId; aktualisierung++; screen = "liste"
            })
        }
        "aibuddy" -> AiBuddyScreen(zurueck = { screen = "liste" })
        "erfassung" -> FangErfassungScreen(
            zeigeListeAn = { aktualisierung++; screen = "liste" },
            zeigeKarteAn = { vorherKartenScreen = "erfassung"; aktualisierung++; screen = "karte" }
        )
        else -> key(aktualisierung) {
            FangListe(
                zeigeErfassungAn = { screen = "erfassung" },
                zeigeKarteFuer = { fang ->
                    kartenFang = fang; scrollZuFangId = fang.id; vorherKartenScreen = "liste"; screen = "karte"
                },
                zeigeBearbeitenFuer = { fang -> bearbeitenFang = fang; screen = "bearbeiten" },
                zeigeAiBuddyAn = { screen = "aibuddy" },
                scrollZuFangId = scrollZuBearbeiteterFangId ?: scrollZuFangId,
                onScrollZuFangIdVerbraucht = { scrollZuBearbeiteterFangId = null; scrollZuFangId = null }
            )
        }
    }
}

// Zeigt GPS, Wetter, Gezeiten und Mondphase als Statusinformationen an
@Composable
private fun UmweltStatusPanel(datum: String, gpsStatus: String, wetterStatus: String, aktuellePosition: Location?) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GPS:", fontWeight = FontWeight.Medium)
                Text(gpsStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wetter:", fontWeight = FontWeight.Medium)
                Text(wetterStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            val gezeitenStatus = if (aktuellePosition != null)
                gezeiteBerechnen(datum, aktuellePosition.latitude, aktuellePosition.longitude)
            else ""
            if (gezeitenStatus.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tide:", fontWeight = FontWeight.Medium)
                    Text(gezeitenStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            val mondphaseStatus = mondphaseBerechnen(datum)
            if (mondphaseStatus.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mond:", fontWeight = FontWeight.Medium)
                    Text(mondphaseStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
    }
}

// Screen zum Erfassen eines neuen Fangs mit Foto, GPS, Wetter und Fischart-Erkennung
@Composable
fun FangErfassungScreen(zeigeListeAn: () -> Unit, zeigeKarteAn: () -> Unit) {
    val context = LocalContext.current
    var fischart by remember { mutableStateOf("") }
    var laenge by remember { mutableStateOf("") }
    var notizen by remember { mutableStateOf("") }
    var gespeichert by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf("wird erfasst...") }
    var wetterStatus by remember { mutableStateOf("wartet auf GPS...") }
    var aktuellePosition by remember { mutableStateOf<Location?>(null) }
    var aktuellesWetter by remember { mutableStateOf<Wetter?>(null) }
    var fotoUri by remember { mutableStateOf<Uri?>(null) }
    var fotoPfad by remember { mutableStateOf("") }
    var tempFotoDatei: File? by remember { mutableStateOf(null) }
    val datum = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val kameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { erfolg ->
        if (erfolg && tempFotoDatei != null) {
            fotoUri = Uri.fromFile(tempFotoDatei)
            fotoPfad = tempFotoDatei!!.absolutePath
            fischart = "wird erkannt..."
            fischartErkennen(fotoPfad) { erkannteArt ->
                fischart = if (erkannteArt.isNotBlank()) erkannteArt else ""
            }
        }
    }

    val kameraBerechtigungsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt ->
        if (erlaubt) {
            val datei = fotoDateiErstellen(context)
            tempFotoDatei = datei
            val uri = FileProvider.getUriForFile(context, "de.taxel.catchy.fileprovider", datei)
            kameraLauncher.launch(uri)
        }
    }

    fun fotoAufnehmen() {
        val hatBerechtigung =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hatBerechtigung) {
            val datei = fotoDateiErstellen(context)
            tempFotoDatei = datei
            val uri = FileProvider.getUriForFile(context, "de.taxel.catchy.fileprovider", datei)
            kameraLauncher.launch(uri)
        } else kameraBerechtigungsLauncher.launch(Manifest.permission.CAMERA)
    }

    fun gpsUndWetterAbrufen() {
        val hatBerechtigung =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hatBerechtigung) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                if (location != null) {
                    aktuellePosition = location
                    gpsStatus = "${String.format(Locale.US, "%.5f", location.latitude)}, " +
                        String.format(Locale.US, "%.5f", location.longitude)
                    wetterStatus = "wird geladen..."
                    wetterAbrufen(location.latitude, location.longitude) { wetter ->
                        aktuellesWetter = wetter
                        wetterStatus = if (wetter != null)
                            "${wetter.temperatur}°C  |  Wind ${wetter.wind} m/s  |  ${wetter.bewoelkung}% Bewölkung"
                        else "Nicht verfügbar"
                    }
                } else { gpsStatus = "Kein Signal"; wetterStatus = "Kein GPS" }
            }
        }
    }

    val berechtigungsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { berechtigungen ->
        if (berechtigungen[Manifest.permission.ACCESS_FINE_LOCATION] == true) gpsUndWetterAbrufen()
        else gpsStatus = "Berechtigung verweigert"
    }

    LaunchedEffect(Unit) {
        val hatBerechtigung =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hatBerechtigung) gpsUndWetterAbrufen()
        else berechtigungsLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fang erfassen", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = zeigeKarteAn) { Text("Karte") }
                Button(onClick = zeigeListeAn) { Text("Zurück") }
            }
        }
        val angezeigtesDatum = datum
        Text(angezeigtesDatum, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { fotoAufnehmen() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text(if (fotoUri != null) "Neu aufnehmen" else "Foto aufnehmen") }
        }
        if (fotoUri != null) {
            Image(painter = rememberAsyncImagePainter(fotoUri), contentDescription = "Fang Foto",
                modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
        }
        OutlinedTextField(
            value = fischart, onValueChange = { fischart = it },
            label = { Text("Fischart") },
            placeholder = { Text("z.B. Hecht, Zander, Barsch...") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = laenge, onValueChange = { laenge = it },
            label = { Text("Länge (cm)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = notizen, onValueChange = { notizen = it },
            label = { Text("Notizen") },
            placeholder = { Text("Köder, Gewässer, Besonderheiten...") },
            modifier = Modifier.fillMaxWidth(), minLines = 3
        )
        UmweltStatusPanel(datum, gpsStatus, wetterStatus, aktuellePosition)
        Button(
            onClick = {
                val verwendetesDatum = datum
                val lat = aktuellePosition?.latitude ?: 0.0
                val lon = aktuellePosition?.longitude ?: 0.0
                fangspeichern(context, Fang(
                    fischart = fischart, laenge = laenge, notizen = notizen, datum = verwendetesDatum,
                    latitude = lat,
                    longitude = lon,
                    temperatur = aktuellesWetter?.temperatur ?: 0.0,
                    wind = aktuellesWetter?.wind ?: 0.0,
                    luftdruck = aktuellesWetter?.luftdruck ?: 0.0,
                    bewoelkung = aktuellesWetter?.bewoelkung ?: 0,
                    fotoPfad = fotoPfad,
                    gezeiten = gezeiteBerechnen(verwendetesDatum, lat, lon),
                    mondphase = mondphaseBerechnen(verwendetesDatum)
                ))
                gespeichert = true
                fischart = ""; laenge = ""; notizen = ""; fotoUri = null; fotoPfad = ""
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = fischart.isNotBlank()
        ) { Text("Fang speichern", fontSize = 16.sp) }
        if (gespeichert) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "Fang gespeichert!", modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Zeigt den Button zum Neuladen der Wetterdaten und den aktuellen Wetterstatus an
@Composable
private fun WetterNeuLadenPanel(
    latText: String, lonText: String, datum: String,
    fallbackLat: Double, fallbackLon: Double,
    wetterNeuLaden: String,
    onWetterAktualisiert: (String) -> Unit
) {
    Button(
        onClick = {
            val lat = latText.toDoubleOrNull() ?: fallbackLat
            val lon = lonText.toDoubleOrNull() ?: fallbackLon
            onWetterAktualisiert("wird geladen...")
            historischesWetterAbrufen(lat, lon, datum) { wetter ->
                onWetterAktualisiert(if (wetter != null)
                    "Wetter aktualisiert: ${wetter.temperatur}°C  |  ${wetter.wind} m/s  |  ${wetter.bewoelkung}% Bewölkung"
                else "Wetter nicht verfügbar")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) { Text("Wetterdaten neu laden") }
    if (wetterNeuLaden.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                wetterNeuLaden, modifier = Modifier.padding(12.dp),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Screen zum Bearbeiten eines bestehenden Fangs — alle Felder inklusive GPS und Wetter sind änderbar
@Composable
fun FangBearbeiten(fang: Fang, zeigeListeFuer: (Long) -> Unit) {
    val context = LocalContext.current
    var fischart by remember { mutableStateOf(fang.fischart) }
    var laenge by remember { mutableStateOf(fang.laenge) }
    var notizen by remember { mutableStateOf(fang.notizen) }
    var datum by remember { mutableStateOf(fang.datum) }
    var latText by remember { mutableStateOf(if (fang.latitude != 0.0) String.format(Locale.US, "%.6f", fang.latitude) else "") }
    var lonText by remember { mutableStateOf(if (fang.longitude != 0.0) String.format(Locale.US, "%.6f", fang.longitude) else "") }
    var fotoUri by remember { mutableStateOf<Uri?>(if (fang.fotoPfad.isNotBlank()) Uri.fromFile(File(fang.fotoPfad)) else null) }
    var fotoPfad by remember { mutableStateOf(fang.fotoPfad) }
    var tempFotoDatei: File? by remember { mutableStateOf(null) }
    var wetterNeuLaden by remember { mutableStateOf("") }

    val kameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { erfolg ->
        if (erfolg && tempFotoDatei != null) {
            fotoUri = Uri.fromFile(tempFotoDatei)
            fotoPfad = tempFotoDatei!!.absolutePath
        }
    }

    val kameraBerechtigungsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erlaubt ->
        if (erlaubt) {
            val datei = fotoDateiErstellen(context)
            tempFotoDatei = datei
            val uri = FileProvider.getUriForFile(context, "de.taxel.catchy.fileprovider", datei)
            kameraLauncher.launch(uri)
        }
    }

    val galerieLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (exifDatum, lat, lon) = exifDatenLesen(context, uri)
            fotoPfad = uriZuPfad(context, uri)
            fotoUri = Uri.fromFile(File(fotoPfad))
            if (exifDatum.isNotBlank()) datum = exifDatum
            if (lat != 0.0) { latText = String.format(Locale.US, "%.6f", lat); lonText = String.format(Locale.US, "%.6f", lon) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fang bearbeiten", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { zeigeListeFuer(-1L) }) { Text("Abbrechen") }
        }
        if (fotoUri != null) {
            Image(painter = rememberAsyncImagePainter(fotoUri), contentDescription = "Fang Foto",
                modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val hatBerechtigung =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hatBerechtigung) {
                        val datei = fotoDateiErstellen(context)
                        tempFotoDatei = datei
                        val uri = FileProvider.getUriForFile(context, "de.taxel.catchy.fileprovider", datei)
                        kameraLauncher.launch(uri)
                    } else kameraBerechtigungsLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text("Neues Foto") }
            Button(
                onClick = { galerieLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text("Aus Galerie") }
        }
        OutlinedTextField(
            value = fischart, onValueChange = { fischart = it },
            label = { Text("Fischart") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = laenge, onValueChange = { laenge = it },
            label = { Text("Länge (cm)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = notizen, onValueChange = { notizen = it },
            label = { Text("Notizen") },
            modifier = Modifier.fillMaxWidth(), minLines = 3
        )
        OutlinedTextField(
            value = datum, onValueChange = { datum = it },
            label = { Text("Datum (TT.MM.JJJJ HH:MM)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText, onValueChange = { latText = it },
                label = { Text("Breitengrad") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            OutlinedTextField(
                value = lonText, onValueChange = { lonText = it },
                label = { Text("Längengrad") },
                modifier = Modifier.weight(1f), singleLine = true
            )
        }
        WetterNeuLadenPanel(
            latText = latText, lonText = lonText, datum = datum,
            fallbackLat = fang.latitude, fallbackLon = fang.longitude,
            wetterNeuLaden = wetterNeuLaden,
            onWetterAktualisiert = { wetterNeuLaden = it }
        )
        Button(
            onClick = {
                val lat = latText.toDoubleOrNull() ?: fang.latitude
                val lon = lonText.toDoubleOrNull() ?: fang.longitude
                val wetterObjekt = if (wetterNeuLaden.startsWith("Wetter aktualisiert")) {
                    val teile = wetterNeuLaden.removePrefix("Wetter aktualisiert: ").split("  |  ")
                    val temp = teile.getOrNull(0)?.removeSuffix("°C")?.trim()?.toDoubleOrNull()
                    val windWert = teile.getOrNull(1)?.removeSuffix(" m/s")?.trim()?.toDoubleOrNull()
                    val bew = teile.getOrNull(2)?.removeSuffix("% Bewölkung")?.trim()?.toIntOrNull()
                    if (temp != null && windWert != null && bew != null)
                        Wetter(temperatur = temp, wind = windWert, luftdruck = fang.luftdruck, bewoelkung = bew)
                    else null
                } else null
                fangAktualisieren(context, fang.copy(
                    fischart = fischart, laenge = laenge, notizen = notizen, datum = datum,
                    latitude = lat, longitude = lon,
                    temperatur = wetterObjekt?.temperatur ?: fang.temperatur,
                    wind = wetterObjekt?.wind ?: fang.wind,
                    luftdruck = wetterObjekt?.luftdruck ?: fang.luftdruck,
                    bewoelkung = wetterObjekt?.bewoelkung ?: fang.bewoelkung,
                    fotoPfad = fotoPfad,
                    gezeiten = gezeiteBerechnen(datum, lat, lon),
                    mondphase = mondphaseBerechnen(datum)
                ))
                zeigeListeFuer(fang.id)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = fischart.isNotBlank()
        ) { Text("Änderungen speichern", fontSize = 16.sp) }
    }
}

// Zeigt einen einzelnen Fang in der Liste mit Foto, Metadaten und Aktionsbuttons an
@Composable
private fun FangListeEintrag(fang: Fang, onBearbeiten: () -> Unit, onLoeschen: () -> Unit, onKarte: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (fang.fotoPfad.isNotBlank()) {
                var fotoVollbild by remember { mutableStateOf(false) }
                Image(
                    painter = rememberAsyncImagePainter(File(fang.fotoPfad)),
                    contentDescription = "Fang Foto",
                    modifier = Modifier.fillMaxWidth().height(180.dp).clickable { fotoVollbild = true },
                    contentScale = ContentScale.Crop
                )
                if (fotoVollbild) {
                    Dialog(
                        onDismissRequest = { fotoVollbild = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().clickable { fotoVollbild = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(File(fang.fotoPfad)),
                                contentDescription = "Fang Foto Vollbild",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(fang.fischart, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(fang.datum, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fang.laenge.isNotBlank()) Text("Länge: ${fang.laenge} cm", fontSize = 14.sp)
            if (fang.notizen.isNotBlank()) Text("Notizen: ${fang.notizen}", fontSize = 14.sp)
            if (fang.temperatur != 0.0) Text(
                "${fang.temperatur}°C  |  Wind ${fang.wind} m/s  |  ${fang.bewoelkung}% Bewölkung",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (fang.gezeiten.isNotBlank()) Text(
                "Tide: ${fang.gezeiten}", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (fang.mondphase.isNotBlank()) Text(
                "Mond: ${fang.mondphase}", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (fang.latitude != 0.0) Text(
                "GPS: ${String.format(Locale.US, "%.4f", fang.latitude)}, " +
                    "${String.format(Locale.US, "%.4f", fang.longitude)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onBearbeiten) { Text("Bearbeiten") }
                TextButton(onClick = onLoeschen) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                if (fang.latitude != 0.0) TextButton(onClick = onKarte) { Text("Karte") }
            }
        }
    }
}

// Screen mit der scrollbaren Liste aller Fänge; bietet Backup/Import/GPX-Export und Navigation zur Karte
@Composable
fun FangListe(
    zeigeErfassungAn: () -> Unit,
    zeigeKarteFuer: (Fang) -> Unit,
    zeigeBearbeitenFuer: (Fang) -> Unit,
    zeigeAiBuddyAn: () -> Unit,
    // Nach Rückkehr von der Karte: ID des Fangs, zu dem gescrollt werden soll
    scrollZuFangId: Long? = null,
    onScrollZuFangIdVerbraucht: () -> Unit = {}
) {
    val context = LocalContext.current
    var faenge by remember { mutableStateOf(faengeladen(context)) }
    // Zustand der LazyColumn — wird für programmatisches Scrollen benötigt
    val listState = rememberLazyListState()

    // Scrollt zur angeforderten Fang-Position, sobald von der Karte zurückgekehrt wird
    LaunchedEffect(scrollZuFangId) {
        if (scrollZuFangId != null) {
            val index = faenge.indexOfFirst { it.id == scrollZuFangId }
            if (index >= 0) listState.animateScrollToItem(index)
            onScrollZuFangIdVerbraucht()
        }
    }
    var exportMeldung by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val anzahl = datenImportieren(context, uri)
            exportMeldung = when {
                anzahl > 0 -> "$anzahl neue Fänge importiert"
                anzahl == 0 -> "Keine neuen Fänge gefunden"
                else -> "Import fehlgeschlagen"
            }
            faenge = faengeladen(context)
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val datum = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
    var pendingGalerieUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    fun galerieImportStarten(uris: List<Uri>) {
        var erfolgreich = 0
        var fehlgeschlagen = 0
        var mitExif = 0
        var mitGps = 0

        for ((index, uri) in uris.withIndex()) {
            val (exifDatum, lat, lon) = exifDatenLesen(context, uri)
            val pfad = uriZuPfad(context, uri, index)
            val verwendetesDatum = if (exifDatum.isNotBlank()) exifDatum else datum
            if (pfad.isNotBlank()) {
                erfolgreich++
                if (exifDatum.isNotBlank()) mitExif++
                if (lat != 0.0) mitGps++
                val fangId = System.currentTimeMillis() + index
                thread {
                    val wetter: Wetter? = if (lat != 0.0) {
                        try {
                            val datumFormatiert = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).parse(verwendetesDatum) ?: Date()
                            )
                            val url = "https://archive-api.open-meteo.com/v1/archive?" +
                                "latitude=${String.format(Locale.US, "%.6f", lat)}" +
                                "&longitude=${String.format(Locale.US, "%.6f", lon)}" +
                                "&start_date=$datumFormatiert&end_date=$datumFormatiert" +
                                "&hourly=temperature_2m,wind_speed_10m,surface_pressure,cloud_cover&wind_speed_unit=ms"
                            val response = URL(url).readText()
                            val json = JSONObject(response)
                            val hourly = json.getJSONObject("hourly")
                            Wetter(
                                temperatur = hourly.getJSONArray("temperature_2m").getDouble(12),
                                wind = hourly.getJSONArray("wind_speed_10m").getDouble(12),
                                luftdruck = hourly.getJSONArray("surface_pressure").getDouble(12),
                                bewoelkung = hourly.getJSONArray("cloud_cover").getInt(12)
                            )
                        } catch (e: java.io.IOException) {
                            android.util.Log.e(TAG, "Wetter laden fehlgeschlagen", e); null
                        } catch (e: org.json.JSONException) {
                            android.util.Log.e(TAG, "Wetter JSON fehlgeschlagen", e); null
                        }
                    } else null
                    fangspeichern(context, Fang(
                        id = fangId,
                        fischart = "wird erkannt...",
                        laenge = "",
                        notizen = "Aus Galerie importiert",
                        datum = verwendetesDatum,
                        latitude = lat,
                        longitude = lon,
                        temperatur = wetter?.temperatur ?: 0.0,
                        wind = wetter?.wind ?: 0.0,
                        luftdruck = wetter?.luftdruck ?: 0.0,
                        bewoelkung = wetter?.bewoelkung ?: 0,
                        fotoPfad = pfad,
                        gezeiten = gezeiteBerechnen(verwendetesDatum, lat, lon),
                        mondphase = mondphaseBerechnen(verwendetesDatum)
                    ))
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        faenge = faengeladen(context)
                    }
                    fischartErkennen(pfad) { erkannteArt ->
                        val gespeicherter = faengeladen(context).find { it.id == fangId }
                        if (gespeicherter != null) {
                            fangAktualisieren(context, gespeicherter.copy(
                                fischart = if (erkannteArt.isNotBlank()) erkannteArt else "Unbekannt"
                            ))
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            faenge = faengeladen(context)
                        }
                    }
                }
            } else {
                fehlgeschlagen++
            }
        }

        val gesamt = uris.size
        exportMeldung = buildString {
            if (gesamt == 1) {
                append(if (erfolgreich == 1) "Foto importiert" else "Import fehlgeschlagen")
            } else {
                append("$erfolgreich von $gesamt Fotos importiert")
                if (fehlgeschlagen > 0) append(", $fehlgeschlagen fehlgeschlagen")
            }
            append(" · EXIF-Datum: $mitExif/$gesamt · GPS: $mitGps/$gesamt")
        }
    }

    val mediaBerechtigungsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val uris = pendingGalerieUris
        if (uris.isNotEmpty()) {
            pendingGalerieUris = emptyList()
            galerieImportStarten(uris)
        }
    }

    val galerieLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val mediaPermGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_MEDIA_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                if (mediaPermGranted) {
                    pendingGalerieUris = uris
                    mediaBerechtigungsLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    return@rememberLauncherForActivityResult
                }
            }
            galerieImportStarten(uris)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Meine Fänge", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Box {
                TextButton(onClick = { menuExpanded = true }) { Text("☰", fontSize = 22.sp) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Backup") }, onClick = {
                        menuExpanded = false
                        val uri = datenExportieren(context)
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Backup teilen"))
                        } else exportMeldung = "Export fehlgeschlagen"
                    })
                    DropdownMenuItem(text = { Text("Import") }, onClick = {
                        menuExpanded = false
                        importLauncher.launch("application/json")
                    })
                    DropdownMenuItem(text = { Text("GPX Export") }, onClick = {
                        menuExpanded = false
                        val uri = gpxExportieren(context, faenge)
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/gpx+xml"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "GPX teilen"))
                        } else exportMeldung = "GPX Export fehlgeschlagen"
                    })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { zeigeErfassungAn() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text("📷", fontSize = 20.sp) }
            Button(
                onClick = { galerieLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text("🖼️", fontSize = 20.sp) }
            Button(
                onClick = zeigeAiBuddyAn,
                modifier = Modifier.weight(2f)
            ) { Text("AI Buddy") }
        }
        if (exportMeldung.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    exportMeldung, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (faenge.isEmpty()) {
            Text("Noch keine Fänge erfasst.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 32.dp))
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(faenge, key = { it.id }) { fang ->
                    FangListeEintrag(
                        fang = fang,
                        onBearbeiten = { zeigeBearbeitenFuer(fang) },
                        onLoeschen = { fangloeschen(context, fang.id); faenge = faengeladen(context) },
                        onKarte = { zeigeKarteFuer(fang) }
                    )
                }
            }
        }
    }
}

// Ruft eine Angelempfehlung von der Gemini API ab (Hintergrund-Thread).
fun geminiEmpfehlungAbrufen(apiKey: String, prompt: String, onErgebnis: (String?) -> Unit) {
    if (apiKey.isBlank()) {
        onErgebnis("Fehler: Kein API-Key konfiguriert (local.properties)")
        return
    }
    thread {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
            val body = JSONObject().put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.write(body.toString().toByteArray())
            val statusCode = conn.responseCode
            if (statusCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "(kein Body)"
                android.util.Log.e(TAG, "Gemini HTTP $statusCode: $errorBody")
                onErgebnis("Fehler: HTTP $statusCode\n$errorBody")
                return@thread
            }
            val response = conn.inputStream.bufferedReader().readText()
            val text = JSONObject(response)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text")
            onErgebnis(text)
        } catch (e: java.io.IOException) {
            android.util.Log.e(TAG, "Gemini API fehlgeschlagen", e)
            onErgebnis("Netzwerkfehler: ${e.message}")
        } catch (e: org.json.JSONException) {
            android.util.Log.e(TAG, "Gemini JSON fehlgeschlagen", e)
            onErgebnis("JSON-Fehler: ${e.message}")
        }
    }
}

// Erstellt den Prompt für die Angelempfehlung aus aktuellen Bedingungen und Fanghistorie.
fun angelEmpfehlungPromptErstellen(
    datum: String, lat: Double, lon: Double,
    wetter: Wetter?, mondphase: String, gezeiten: String, letzeFaenge: List<Fang>
): String = buildString {
    appendLine("Du bist ein erfahrener Angelexperte für europäisches Spinnfischen.")
    appendLine("Gib konkrete, praxisnahe Empfehlungen für den heutigen Angeltag auf Deutsch (max. 250 Wörter).")
    appendLine("\n## Aktuelle Bedingungen")
    appendLine("Datum/Zeit: $datum")
    appendLine("Position: ${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)}")
    wetter?.let {
        appendLine("Temperatur: ${it.temperatur}°C | Wind: ${it.wind} m/s | Luftdruck: ${it.luftdruck} hPa | Bewölkung: ${it.bewoelkung}%")
    }
    if (mondphase.isNotBlank()) appendLine("Mondphase: $mondphase")
    if (gezeiten.isNotBlank()) appendLine("Gezeiten: $gezeiten")
    appendLine("\n## Zielarten\nZander, Hecht, Barsch, Rapfen, Forellenartige")
    if (letzeFaenge.isNotEmpty()) {
        appendLine("\n## Meine letzten Fänge")
        letzeFaenge.take(10).forEach { f ->
            append("- ${f.fischart}")
            if (f.laenge.isNotBlank()) append(", ${f.laenge} cm")
            append(", ${f.datum}")
            if (f.temperatur != 0.0) append(", ${f.temperatur}°C, Wind ${f.wind} m/s")
            appendLine()
        }
    }
    appendLine("\n## Bitte empfehle:")
    appendLine("1. Aktivitätsbewertung der Zielarten")
    appendLine("2. Beste Zeitfenster heute")
    appendLine("3. Technik und Tiefe")
    appendLine("4. Köder")
    appendLine("5. Besonderheiten aus meiner Fanghistorie")
}

// AI Buddy Screen — KI-basierte Angelempfehlungen (Issue #11)
@Composable
fun AiBuddyScreen(zurueck: () -> Unit) {
    val context = LocalContext.current
    var gpsStatus by remember { mutableStateOf("wird ermittelt...") }
    var aktuellePosition by remember { mutableStateOf<Location?>(null) }
    var aktuellesWetter by remember { mutableStateOf<Wetter?>(null) }
    var empfehlung by remember { mutableStateOf("") }
    var ladeStatus by remember { mutableStateOf("") }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val datum = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date()) }

    LaunchedEffect(Unit) {
        val hatBerechtigung = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hatBerechtigung) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        aktuellePosition = loc
                        gpsStatus = "${String.format(Locale.US, "%.4f", loc.latitude)}, ${String.format(Locale.US, "%.4f", loc.longitude)}"
                        wetterAbrufen(loc.latitude, loc.longitude) { wetter -> aktuellesWetter = wetter }
                    } else gpsStatus = "Kein Signal"
                }
        } else gpsStatus = "Berechtigung fehlt"
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI Buddy", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = zurueck) { Text("Zurück") }
        }
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("GPS: $gpsStatus", fontSize = 13.sp)
                val wetterText = if (aktuellesWetter != null) {
                    "${aktuellesWetter!!.temperatur}°C · ${aktuellesWetter!!.wind} m/s · ${aktuellesWetter!!.luftdruck} hPa"
                } else "wird geladen..."
                Text("Wetter: $wetterText", fontSize = 13.sp)
            }
        }
        Button(
            onClick = {
                val pos = aktuellePosition ?: return@Button
                ladeStatus = "Empfehlung wird erstellt..."
                empfehlung = ""
                val faenge = faengeladen(context)
                val prompt = angelEmpfehlungPromptErstellen(
                    datum, pos.latitude, pos.longitude, aktuellesWetter,
                    mondphaseBerechnen(datum), gezeiteBerechnen(datum, pos.latitude, pos.longitude), faenge
                )
                geminiEmpfehlungAbrufen(BuildConfig.GEMINI_API_KEY, prompt) { ergebnis ->
                    empfehlung = ergebnis ?: "Empfehlung konnte nicht geladen werden."
                    ladeStatus = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = aktuellePosition != null
        ) { Text(if (aktuellePosition == null) "Warte auf GPS..." else "Empfehlung anfordern") }
        if (ladeStatus.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(ladeStatus, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (empfehlung.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(empfehlung, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

// Wandelt einen Kompassgrad (0-360) in eine Himmelsrichtung um
fun bearingZuHimmelsrichtung(grad: Float): String {
    val richtungen = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
    return richtungen[((grad + 22.5f) / 45f).toInt().mod(8)]
}

// Screen mit einer OpenStreetMap-Karte aller Fangspots als Marker.
// zentriereFang: wenn gesetzt, wird die Karte auf diesen Fang gezoomt (Zoom 15), sonst Übersicht (Zoom 12)
@Composable
fun FangKarte(zurueck: () -> Unit, zentriereFang: Fang? = null) {
    val context = LocalContext.current
    val faenge = remember { faengeladen(context).filter { it.latitude != 0.0 } }
    var meinePosition by remember { mutableStateOf<Location?>(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val selectedFangState = remember { mutableStateOf<Fang?>(null) }
    var selectedFang by selectedFangState
    var lastDistance by remember { mutableStateOf<Float?>(null) }
    var distanzTrend by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val hatBerechtigung =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) { meinePosition = result.lastLocation }
        }
        if (hatBerechtigung) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1000L).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())
        }
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    LaunchedEffect(meinePosition, selectedFang) {
        val fang = selectedFang ?: run { lastDistance = null; distanzTrend = ""; return@LaunchedEffect }
        val pos = meinePosition ?: return@LaunchedEffect
        val results = FloatArray(1)
        Location.distanceBetween(pos.latitude, pos.longitude, fang.latitude, fang.longitude, results)
        if (pos.accuracy <= 20f) {
            lastDistance?.let { letzteDistanz ->
                distanzTrend = when {
                    results[0] < letzteDistanz - 20f -> "↓ näher"
                    results[0] > letzteDistanz + 20f -> "↑ weiter weg"
                    else -> distanzTrend
                }
            }
        }
        lastDistance = results[0]
    }
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        if (faenge.isEmpty() && meinePosition == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Noch keine Fangspots mit GPS.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        val zielFang = zentriereFang ?: faenge.firstOrNull()
                        controller.setZoom(if (zentriereFang != null) 15.0 else 12.0)
                        zielFang?.let { controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
                        faenge.forEach { fang ->
                            val marker = Marker(this)
                            marker.position = GeoPoint(fang.latitude, fang.longitude)
                            marker.title = fang.fischart
                            
                            // Distanz berechnen, falls Standort verfügbar
                            val distanzText = meinePosition?.let { pos ->
                                val ergebnisse = FloatArray(1)
                                Location.distanceBetween(
                                    pos.latitude, pos.longitude, fang.latitude, fang.longitude, ergebnisse
                                )
                                val meter = ergebnisse[0]
                                if (meter >= 1000) " | Distanz: ${String.format(Locale.US, "%.1f", meter / 1000)} km"
                                else " | Distanz: ${meter.toInt()} m"
                            } ?: ""
                            
                            marker.snippet = "${fang.datum}  |  ${fang.temperatur}°C$distanzText"
                            marker.setOnMarkerClickListener { _, _ ->
                                selectedFangState.value = fang
                                true
                            }
                            overlays.add(marker)
                        }
                    }
                },
                update = { mapView ->
                    // Entferne alten Standort-Marker (falls vorhanden) und füge neuen hinzu
                    mapView.overlays.removeAll { it is Marker && (it as Marker).title == "Mein Standort" }
                    meinePosition?.let { pos ->
                        val meinMarker = Marker(mapView)
                        meinMarker.position = GeoPoint(pos.latitude, pos.longitude)
                        meinMarker.title = "Mein Standort"
                        meinMarker.snippet =
                            "${String.format(Locale.US, "%.5f", pos.latitude)}, ${String.format(Locale.US, "%.5f", pos.longitude)}"
                        meinMarker.setIcon(ContextCompat.getDrawable(mapView.context, android.R.drawable.ic_menu_mylocation))
                        mapView.overlays.add(meinMarker)
                        mapView.invalidate()
                        
                        // Snippets der Fang-Marker aktualisieren, um Distanz anzuzeigen
                        mapView.overlays.filterIsInstance<Marker>().forEach { marker ->
                            if (marker.title != "Mein Standort") {
                                val fang = faenge.find {
                                    it.fischart == marker.title && it.latitude == marker.position.latitude
                                }
                                if (fang != null) {
                                    val ergebnisse = FloatArray(1)
                                    Location.distanceBetween(
                                        pos.latitude, pos.longitude, fang.latitude, fang.longitude, ergebnisse
                                    )
                                    val meter = ergebnisse[0]
                                    val distText = if (meter >= 1000) " | Distanz: ${String.format(Locale.US, "%.1f", meter / 1000)} km"
                                    else " | Distanz: ${meter.toInt()} m"
                                    marker.snippet = "${fang.datum}  |  ${fang.temperatur}°C$distText"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fangspots", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Button(onClick = zurueck) { Text("Zurück") }
            }
        }
        selectedFang?.let { fang ->
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fang.fotoPfad.isNotBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(File(fang.fotoPfad)),
                            contentDescription = "Fang Foto",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fang.fischart, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            TextButton(onClick = { selectedFang = null }) { Text("✕") }
                        }
                        if (fang.laenge.isNotBlank()) Text(
                            "Länge: ${fang.laenge} cm", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(fang.datum, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        meinePosition?.let { pos ->
                            val results = FloatArray(2)
                            Location.distanceBetween(
                                pos.latitude, pos.longitude, fang.latitude, fang.longitude, results
                            )
                            val distanzText = if (results[0] >= 1000)
                                "${String.format(Locale.US, "%.1f", results[0] / 1000)} km"
                            else "${results[0].toInt()} m"
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${bearingZuHimmelsrichtung(results[1])} · $distanzText",
                                    fontSize = 15.sp, fontWeight = FontWeight.Medium
                                )
                                if (distanzTrend.isNotBlank()) Text(
                                    distanzTrend, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } ?: Text(
                            "Standort wird ermittelt...",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
