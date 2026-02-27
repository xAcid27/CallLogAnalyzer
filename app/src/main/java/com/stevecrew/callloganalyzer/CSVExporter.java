package com.stevecrew.callloganalyzer;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Exportiert Anrufdaten als CSV-Datei in den Downloads-Ordner.
 * 
 * Features:
 * - Automatischer Dateiname mit Timestamp (CallLog_20260227_143052.csv)
 * - Kompatibel mit Android 10+ (Scoped Storage via MediaStore)
 * - Fallback für Android 9 und älter (direkter Dateizugriff)
 * - CSV-konformes Escaping (Kommas, Anführungszeichen, Zeilenumbrüche)
 * 
 * CSV-Format:
 * Date/Time,Number,Contact Name,Type,Duration (seconds)
 */
public class CSVExporter {

    /**
     * Exportiert die Anrufliste als CSV-Datei.
     * 
     * @param context Android Context für Dateizugriff
     * @param calls Liste der zu exportierenden Anrufe
     * @return Pfad zur erstellten Datei, oder null bei Fehler
     */
    public static String exportToCSV(Context context, List<CallLogEntry> calls) {
        // Eindeutiger Dateiname mit aktuellem Timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "CallLog_" + timestamp + ".csv";
        
        // === CSV-Inhalt aufbauen ===
        StringBuilder csv = new StringBuilder();
        
        // Header-Zeile
        csv.append("Date/Time,Number,Contact Name,Type,Duration (seconds)\n");
        
        // Datenzeilen - jeder Anruf eine Zeile
        for (CallLogEntry entry : calls) {
            csv.append(escapeCSV(entry.getFormattedDate())).append(",");
            csv.append(escapeCSV(entry.getNumber())).append(",");
            csv.append(escapeCSV(entry.getContactName())).append(",");
            csv.append(escapeCSV(entry.getTypeString())).append(",");
            csv.append(entry.getDuration()).append("\n");
        }

        // === Datei speichern ===
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Scoped Storage
                // Direkter Zugriff auf Downloads nicht mehr erlaubt,
                // stattdessen MediaStore API verwenden
                return saveWithMediaStore(context, fileName, csv.toString());
            } else {
                // Android 9 und älter: Direkter Dateizugriff
                return saveWithDirectAccess(fileName, csv.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Speichert Datei über MediaStore API (Android 10+).
     * 
     * MediaStore ist Androids "Content Provider" für Mediendateien.
     * Vorteile:
     * - Keine WRITE_EXTERNAL_STORAGE Permission nötig
     * - Datei erscheint sofort im Downloads-Ordner
     * - Funktioniert auch mit Scoped Storage
     */
    private static String saveWithMediaStore(Context context, String fileName, String content) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        // Eintrag im MediaStore erstellen → gibt URI zurück
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            // OutputStream über URI öffnen und Daten schreiben
            OutputStream os = context.getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(content.getBytes());
                os.close();
                return "Downloads/" + fileName;
            }
        }
        return null;
    }
    
    /**
     * Speichert Datei mit direktem Dateizugriff (Android 9 und älter).
     * 
     * Klassischer Weg: Datei direkt im Downloads-Ordner erstellen.
     * Benötigt WRITE_EXTERNAL_STORAGE Permission.
     */
    private static String saveWithDirectAccess(String fileName, String content) throws Exception {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadsDir, fileName);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes());
        fos.close();
        return file.getAbsolutePath();
    }

    /**
     * Escaped einen String für CSV-Format.
     * 
     * CSV-Regeln:
     * - Felder mit Kommas müssen in Anführungszeichen
     * - Anführungszeichen im Text werden verdoppelt
     * - Zeilenumbrüche müssen auch escaped werden
     * 
     * Beispiel: 'Müller, Hans' → '"Müller, Hans"'
     * Beispiel: 'Er sagte "Hallo"' → '"Er sagte ""Hallo"""'
     */
    private static String escapeCSV(String value) {
        if (value == null) return "";
        // Nur escapen wenn nötig (Performance)
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
