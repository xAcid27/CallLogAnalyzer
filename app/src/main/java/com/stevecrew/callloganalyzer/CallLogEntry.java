package com.stevecrew.callloganalyzer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Datenklasse für einen einzelnen Anruf-Eintrag.
 * 
 * Repräsentiert eine Zeile aus dem Android CallLog mit allen relevanten Infos:
 * - Telefonnummer & Kontaktname
 * - Anruftyp (eingehend/ausgehend/verpasst/abgelehnt)
 * - Dauer in Sekunden
 * - Zeitstempel
 * 
 * Wird von CallLogHelper beim Laden der Anrufliste erstellt.
 */
public class CallLogEntry {
    
    // === Anruftyp-Konstanten (entsprechen Android CallLog.Calls.TYPE) ===
    public static final int TYPE_INCOMING = 1;  // Eingehender Anruf
    public static final int TYPE_OUTGOING = 2;  // Ausgehender Anruf
    public static final int TYPE_MISSED = 3;    // Verpasster Anruf
    public static final int TYPE_REJECTED = 5;  // Abgelehnter Anruf

    // === Anruf-Daten ===
    private String number;       // Telefonnummer (kann verschiedene Formate haben)
    private String contactName;  // Name aus Kontakten (leer wenn unbekannt)
    private int type;            // Einer der TYPE_* Konstanten
    private long duration;       // Dauer in Sekunden (0 bei verpassten Anrufen)
    private long timestamp;      // Unix-Timestamp in Millisekunden

    /**
     * Erstellt einen neuen Anruf-Eintrag.
     * 
     * @param number Telefonnummer
     * @param contactName Name aus Kontakten (kann null sein)
     * @param type Anruftyp (TYPE_INCOMING, TYPE_OUTGOING, etc.)
     * @param duration Anrufdauer in Sekunden
     * @param timestamp Zeitpunkt des Anrufs (Unix-Timestamp in ms)
     */
    public CallLogEntry(String number, String contactName, int type, long duration, long timestamp) {
        this.number = number;
        this.contactName = contactName != null ? contactName : "";
        this.type = type;
        this.duration = duration;
        this.timestamp = timestamp;
    }

    // === Getter-Methoden ===
    public String getNumber() { return number; }
    public String getContactName() { return contactName; }
    public int getType() { return type; }
    public long getDuration() { return duration; }
    public long getTimestamp() { return timestamp; }

    /**
     * Gibt den Anruftyp als lesbaren String zurück.
     * Für Debug-Zwecke und CSV-Export.
     */
    public String getTypeString() {
        switch (type) {
            case TYPE_INCOMING: return "Incoming";
            case TYPE_OUTGOING: return "Outgoing";
            case TYPE_MISSED: return "Missed";
            case TYPE_REJECTED: return "Rejected";
            default: return "Unknown";
        }
    }

    /**
     * Formatiert den Timestamp als lesbares Datum.
     * Format: "yyyy-MM-dd HH:mm:ss"
     */
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Gibt entweder den Kontaktnamen oder die Nummer zurück.
     * Für die Anzeige in der UI wenn Name unbekannt.
     */
    public String getDisplayName() {
        return contactName.isEmpty() ? number : contactName;
    }

    /**
     * Formatiert die Anrufdauer als lesbaren String.
     * Beispiele: "1:23:45" (mit Stunden) oder "12:34" (nur Minuten)
     */
    public String getFormattedDuration() {
        long hours = duration / 3600;
        long minutes = (duration % 3600) / 60;
        long seconds = duration % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
