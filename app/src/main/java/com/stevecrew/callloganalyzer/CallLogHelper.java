package com.stevecrew.callloganalyzer;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zentrale Klasse für den Zugriff auf das Android Anrufprotokoll.
 * 
 * Hauptfunktionen:
 * - Lädt Anrufdaten aus der Android CallLog-Datenbank
 * - Filtert nach Zeitraum (7 Tage, 30 Tage, etc.)
 * - Filtert ausgeblendete Nummern (Blacklist)
 * - Berechnet Statistiken (Top Caller, längste Gespräche)
 * - Beobachtet Änderungen für Live-Updates (ContentObserver)
 * 
 * Verwendung:
 * 1. CallLogHelper erstellen mit Context
 * 2. loadCallLog() aufrufen nach Permission-Grant
 * 3. startObserving() für Live-Updates
 * 4. stopObserving() in onDestroy() nicht vergessen!
 */
public class CallLogHelper {
    
    /**
     * Callback-Interface für Änderungen im Anrufprotokoll.
     * Wird aufgerufen wenn ein neuer Anruf hinzukommt oder sich das Log ändert.
     */
    public interface OnCallLogChangedListener {
        void onCallLogChanged();
    }
    
    // === Zeitraum-Konstanten für Filter ===
    public static final int PERIOD_ALL = 0;       // Alle Anrufe
    public static final int PERIOD_7_DAYS = 1;    // Letzte 7 Tage
    public static final int PERIOD_30_DAYS = 2;   // Letzte 30 Tage
    public static final int PERIOD_3_MONTHS = 3;  // Letzte 3 Monate
    public static final int PERIOD_6_MONTHS = 4;  // Letzte 6 Monate
    public static final int PERIOD_1_YEAR = 5;    // Letztes Jahr
    
    /**
     * Nummern die IMMER als ausgehend gezählt werden sollen.
     * 
     * Hintergrund: Manche Nummern (z.B. Festnetz-Durchwahl) werden vom
     * System manchmal falsch als "eingehend" markiert wenn man selbst
     * von dieser Nummer anruft. Hier können solche Nummern korrigiert werden.
     */
    private static final String[] ALWAYS_OUTGOING = {
        "+49355691034",
        "49355691034",
        "0355691034"
    };
    
    // === Kern-Daten ===
    private final Context context;
    private final List<CallLogEntry> allCalls;       // Alle geladenen Anrufe (ungefiltert)
    private final List<CallLogEntry> filteredCalls;  // Gefilterte Anrufe (nach Zeit & Blacklist)
    private int currentPeriod = PERIOD_ALL;          // Aktuell ausgewählter Zeitraum
    private BlacklistManager blacklistManager;
    
    // === Live-Update Komponenten ===
    // Observer der auf Änderungen im CallLog reagiert (z.B. neuer Anruf)
    private CallLogObserver callLogObserver;
    // Listener der benachrichtigt wird wenn sich Daten ändern (für UI-Updates)
    private OnCallLogChangedListener changeListener;
    // Handler für Main-Thread um UI-Updates sicher auszuführen
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Erstellt einen neuen CallLogHelper.
     * 
     * @param context Android Context (wird für ContentResolver benötigt)
     */
    public CallLogHelper(Context context) {
        this.context = context;
        this.allCalls = new ArrayList<>();
        this.filteredCalls = new ArrayList<>();
        this.blacklistManager = new BlacklistManager(context);
    }
    
    /**
     * Setzt den Listener der bei Änderungen im Anrufprotokoll benachrichtigt wird.
     * Typischerweise wird hier die UI-Update-Logik übergeben.
     * 
     * @param listener Callback der bei Änderungen aufgerufen wird
     */
    public void setOnCallLogChangedListener(OnCallLogChangedListener listener) {
        this.changeListener = listener;
    }
    
    /**
     * Startet die Überwachung des Anrufprotokolls.
     * 
     * Registriert einen ContentObserver auf CallLog.Calls.CONTENT_URI.
     * Ab jetzt wird bei jedem neuen Anruf (eingehend/ausgehend/verpasst)
     * automatisch onChange() aufgerufen → Daten neu laden → UI updaten.
     * 
     * Aufruf: Nach Permission-Grant in MainActivity.loadData()
     */
    public void startObserving() {
        if (callLogObserver == null) {
            callLogObserver = new CallLogObserver(mainHandler);
            // true = auch bei Änderungen in Unter-URIs benachrichtigen
            context.getContentResolver().registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver
            );
        }
    }
    
    /**
     * Stoppt die Überwachung des Anrufprotokolls.
     * 
     * WICHTIG: Muss in onDestroy() aufgerufen werden!
     * Sonst: Memory Leak, da der Observer eine Referenz auf Context hält.
     */
    public void stopObserving() {
        if (callLogObserver != null) {
            context.getContentResolver().unregisterContentObserver(callLogObserver);
            callLogObserver = null;
        }
    }
    
    /**
     * ContentObserver der auf Änderungen im Android CallLog reagiert.
     * 
     * Funktionsweise:
     * 1. Android erkennt Änderung im CallLog (neuer Anruf beendet)
     * 2. ContentResolver benachrichtigt alle registrierten Observer
     * 3. onChange() wird aufgerufen
     * 4. Wir laden die Daten neu und informieren die UI
     */
    private class CallLogObserver extends ContentObserver {
        public CallLogObserver(Handler handler) {
            super(handler);
        }
        
        @Override
        public void onChange(boolean selfChange) {
            // Ältere Android-Versionen rufen diese Methode ohne URI auf
            onChange(selfChange, null);
        }
        
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            // Anrufliste neu aus der Datenbank laden
            loadCallLog();
            
            // UI auf dem Main-Thread aktualisieren (wichtig für Android!)
            if (changeListener != null) {
                mainHandler.post(() -> changeListener.onCallLogChanged());
            }
        }
    }
    
    /**
     * Setzt den BlacklistManager (für Dependency Injection).
     */
    public void setBlacklistManager(BlacklistManager manager) {
        this.blacklistManager = manager;
    }
    
    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    /**
     * Lädt alle Anrufe aus der Android CallLog-Datenbank.
     * 
     * Verwendet ContentResolver um auf CallLog.Calls zuzugreifen.
     * Benötigt READ_CALL_LOG Permission!
     * 
     * Nach dem Laden wird automatisch applyFilter() aufgerufen
     * um Zeitraum- und Blacklist-Filter anzuwenden.
     */
    public void loadCallLog() {
        allCalls.clear();
        
        // Welche Spalten wollen wir laden?
        String[] projection = {
            CallLog.Calls.NUMBER,       // Telefonnummer
            CallLog.Calls.CACHED_NAME,  // Kontaktname (cached vom System)
            CallLog.Calls.TYPE,         // Anruftyp (1=incoming, 2=outgoing, 3=missed, 5=rejected)
            CallLog.Calls.DURATION,     // Dauer in Sekunden
            CallLog.Calls.DATE          // Timestamp in Millisekunden
        };

        // Query ausführen, sortiert nach Datum (neueste zuerst)
        Cursor cursor = context.getContentResolver().query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,    // selection (WHERE) - null = alle
            null,    // selectionArgs
            CallLog.Calls.DATE + " DESC"  // Sortierung
        );

        // Cursor durchlaufen und CallLogEntry-Objekte erstellen
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                long duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                long date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));

                allCalls.add(new CallLogEntry(number, name, type, duration, date));
            }
            cursor.close();  // Cursor immer schließen!
        }
        
        // Filter anwenden (Zeitraum + Blacklist)
        applyFilter();
    }
    
    /**
     * Setzt den Zeitraum-Filter und wendet ihn an.
     * 
     * @param period Eine der PERIOD_* Konstanten
     */
    public void setTimePeriod(int period) {
        this.currentPeriod = period;
        applyFilter();
    }
    
    public int getCurrentPeriod() {
        return currentPeriod;
    }
    
    /**
     * Prüft ob eine Nummer zu den "immer ausgehend" Nummern gehört.
     * Siehe ALWAYS_OUTGOING Array für Erklärung.
     */
    private boolean isAlwaysOutgoing(String number) {
        String normalized = number.replaceAll("[^0-9+]", "");
        for (String outgoingNum : ALWAYS_OUTGOING) {
            if (normalized.contains(outgoingNum) || outgoingNum.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gibt den effektiven Anruftyp zurück.
     * Korrigiert den Typ für Nummern in ALWAYS_OUTGOING.
     */
    private int getEffectiveType(CallLogEntry entry) {
        if (isAlwaysOutgoing(entry.getNumber())) {
            return CallLogEntry.TYPE_OUTGOING;
        }
        return entry.getType();
    }
    
    /**
     * Wendet Zeitraum- und Blacklist-Filter auf die Anrufliste an.
     * 
     * Füllt filteredCalls mit allen Anrufen die:
     * - Nicht auf der Blacklist stehen
     * - Im ausgewählten Zeitraum liegen
     */
    private void applyFilter() {
        filteredCalls.clear();
        
        // Cutoff-Zeit berechnen basierend auf gewähltem Zeitraum
        long cutoffTime = 0;
        if (currentPeriod != PERIOD_ALL) {
            cutoffTime = System.currentTimeMillis();
            switch (currentPeriod) {
                case PERIOD_7_DAYS:
                    cutoffTime -= 7L * 24 * 60 * 60 * 1000;    // 7 Tage in ms
                    break;
                case PERIOD_30_DAYS:
                    cutoffTime -= 30L * 24 * 60 * 60 * 1000;   // 30 Tage in ms
                    break;
                case PERIOD_3_MONTHS:
                    cutoffTime -= 90L * 24 * 60 * 60 * 1000;   // ~3 Monate
                    break;
                case PERIOD_6_MONTHS:
                    cutoffTime -= 180L * 24 * 60 * 60 * 1000;  // ~6 Monate
                    break;
                case PERIOD_1_YEAR:
                    cutoffTime -= 365L * 24 * 60 * 60 * 1000;  // ~1 Jahr
                    break;
            }
        }
        
        // Jeden Anruf prüfen
        for (CallLogEntry entry : allCalls) {
            // Blacklist-Check: Ausgeblendete Nummern überspringen
            if (blacklistManager != null && blacklistManager.isBlacklisted(entry.getNumber())) {
                continue;
            }
            
            // Zeit-Check: Nur Anrufe im gewählten Zeitraum
            if (currentPeriod == PERIOD_ALL || entry.getTimestamp() >= cutoffTime) {
                filteredCalls.add(entry);
            }
        }
    }

    /**
     * Gibt die gefilterte Anrufliste zurück.
     * Diese Liste wird in der UI angezeigt.
     */
    public List<CallLogEntry> getAllCalls() {
        return filteredCalls;
    }
    
    /**
     * Gibt die ungefilterte Anrufliste zurück.
     * Enthält alle Anrufe, auch ausgeblendete.
     */
    public List<CallLogEntry> getAllCallsUnfiltered() {
        return allCalls;
    }

    // === Statistik-Methoden ===
    // Zählen Anrufe nach Typ (mit Korrektur für ALWAYS_OUTGOING)

    /** Anzahl eingehender Anrufe */
    public int getIncomingCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_INCOMING) count++;
        }
        return count;
    }

    /** Anzahl ausgehender Anrufe */
    public int getOutgoingCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_OUTGOING) count++;
        }
        return count;
    }

    /** Anzahl verpasster Anrufe */
    public int getMissedCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_MISSED) count++;
        }
        return count;
    }

    /** Anzahl abgelehnter Anrufe */
    public int getRejectedCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_REJECTED) count++;
        }
        return count;
    }

    /**
     * Gibt die Top-Anrufer nach Anzahl Anrufe zurück.
     * 
     * @param limit Maximale Anzahl Ergebnisse (z.B. 10 für Top 10)
     * @return Liste von (Nummer → Anzahl) Paaren, sortiert nach Anzahl
     */
    public List<Map.Entry<String, Integer>> getTopCallers(int limit) {
        // Anrufe pro Nummer zählen
        Map<String, Integer> callerCount = new HashMap<>();
        
        for (CallLogEntry entry : filteredCalls) {
            // compute: Wenn Key nicht existiert → 1, sonst +1
            callerCount.compute(entry.getNumber(), (k, v) -> (v == null) ? 1 : v + 1);
        }

        // Nach Anzahl sortieren (absteigend)
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(callerCount.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        // Nur die Top N zurückgeben
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Gibt die Top-Anrufer nach Gesprächsdauer zurück.
     * 
     * @param limit Maximale Anzahl Ergebnisse
     * @return Liste von (Nummer → Gesamtdauer in Sekunden) Paaren
     */
    public List<Map.Entry<String, Long>> getTopDuration(int limit) {
        // Gesamtdauer pro Nummer summieren
        Map<String, Long> callerDuration = new HashMap<>();
        
        for (CallLogEntry entry : filteredCalls) {
            long duration = entry.getDuration();
            callerDuration.compute(entry.getNumber(), (k, v) -> (v == null) ? duration : v + duration);
        }

        // Nach Dauer sortieren (absteigend)
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(callerDuration.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Sucht den Kontaktnamen für eine Telefonnummer.
     * 
     * Durchsucht die gefilterte Liste nach einem Eintrag mit dieser Nummer
     * der einen Kontaktnamen hat. Gibt die Nummer selbst zurück wenn
     * kein Name gefunden wird.
     * 
     * @param number Telefonnummer
     * @return Kontaktname oder die Nummer wenn unbekannt
     */
    public String getContactNameForNumber(String number) {
        for (CallLogEntry entry : filteredCalls) {
            if (entry.getNumber().equals(number) && !entry.getContactName().isEmpty()) {
                return entry.getContactName();
            }
        }
        return number;  // Fallback: Nummer selbst zurückgeben
    }
}
