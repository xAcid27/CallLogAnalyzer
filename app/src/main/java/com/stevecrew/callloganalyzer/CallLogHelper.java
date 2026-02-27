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

public class CallLogHelper {
    
    /**
     * Callback-Interface für Änderungen im Anrufprotokoll.
     * Wird aufgerufen wenn ein neuer Anruf hinzukommt oder sich das Log ändert.
     */
    public interface OnCallLogChangedListener {
        void onCallLogChanged();
    }
    
    // Time period constants
    public static final int PERIOD_ALL = 0;
    public static final int PERIOD_7_DAYS = 1;
    public static final int PERIOD_30_DAYS = 2;
    public static final int PERIOD_3_MONTHS = 3;
    public static final int PERIOD_6_MONTHS = 4;
    public static final int PERIOD_1_YEAR = 5;
    
    // Numbers that should always count as outgoing
    private static final String[] ALWAYS_OUTGOING = {
        "+49355691034",
        "49355691034",
        "0355691034"
    };
    
    private final Context context;
    private final List<CallLogEntry> allCalls;
    private final List<CallLogEntry> filteredCalls;
    private int currentPeriod = PERIOD_ALL;
    private BlacklistManager blacklistManager;
    
    // === Live-Update Komponenten ===
    // Observer der auf Änderungen im CallLog reagiert (z.B. neuer Anruf)
    private CallLogObserver callLogObserver;
    // Listener der benachrichtigt wird wenn sich Daten ändern (für UI-Updates)
    private OnCallLogChangedListener changeListener;
    // Handler für Main-Thread um UI-Updates sicher auszuführen
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
    
    public void setBlacklistManager(BlacklistManager manager) {
        this.blacklistManager = manager;
    }
    
    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    public void loadCallLog() {
        allCalls.clear();
        
        String[] projection = {
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        };

        Cursor cursor = context.getContentResolver().query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                long duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                long date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));

                allCalls.add(new CallLogEntry(number, name, type, duration, date));
            }
            cursor.close();
        }
        
        applyFilter();
    }
    
    public void setTimePeriod(int period) {
        this.currentPeriod = period;
        applyFilter();
    }
    
    public int getCurrentPeriod() {
        return currentPeriod;
    }
    
    private boolean isAlwaysOutgoing(String number) {
        String normalized = number.replaceAll("[^0-9+]", "");
        for (String outgoingNum : ALWAYS_OUTGOING) {
            if (normalized.contains(outgoingNum) || outgoingNum.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
    
    private int getEffectiveType(CallLogEntry entry) {
        if (isAlwaysOutgoing(entry.getNumber())) {
            return CallLogEntry.TYPE_OUTGOING;
        }
        return entry.getType();
    }
    
    private void applyFilter() {
        filteredCalls.clear();
        
        long cutoffTime = 0;
        if (currentPeriod != PERIOD_ALL) {
            cutoffTime = System.currentTimeMillis();
            switch (currentPeriod) {
                case PERIOD_7_DAYS:
                    cutoffTime -= 7L * 24 * 60 * 60 * 1000;
                    break;
                case PERIOD_30_DAYS:
                    cutoffTime -= 30L * 24 * 60 * 60 * 1000;
                    break;
                case PERIOD_3_MONTHS:
                    cutoffTime -= 90L * 24 * 60 * 60 * 1000;
                    break;
                case PERIOD_6_MONTHS:
                    cutoffTime -= 180L * 24 * 60 * 60 * 1000;
                    break;
                case PERIOD_1_YEAR:
                    cutoffTime -= 365L * 24 * 60 * 60 * 1000;
                    break;
            }
        }
        
        for (CallLogEntry entry : allCalls) {
            // Skip blacklisted numbers
            if (blacklistManager != null && blacklistManager.isBlacklisted(entry.getNumber())) {
                continue;
            }
            
            // Apply time filter
            if (currentPeriod == PERIOD_ALL || entry.getTimestamp() >= cutoffTime) {
                filteredCalls.add(entry);
            }
        }
    }

    public List<CallLogEntry> getAllCalls() {
        return filteredCalls;
    }
    
    public List<CallLogEntry> getAllCallsUnfiltered() {
        return allCalls;
    }

    public int getIncomingCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_INCOMING) count++;
        }
        return count;
    }

    public int getOutgoingCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_OUTGOING) count++;
        }
        return count;
    }

    public int getMissedCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_MISSED) count++;
        }
        return count;
    }

    public int getRejectedCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (getEffectiveType(entry) == CallLogEntry.TYPE_REJECTED) count++;
        }
        return count;
    }

    // Top 10 by call frequency
    public List<Map.Entry<String, Integer>> getTopCallers(int limit) {
        Map<String, Integer> callerCount = new HashMap<>();
        
        for (CallLogEntry entry : filteredCalls) {
            callerCount.compute(entry.getNumber(), (k, v) -> (v == null) ? 1 : v + 1);
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(callerCount.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // Top 10 by total call duration
    public List<Map.Entry<String, Long>> getTopDuration(int limit) {
        Map<String, Long> callerDuration = new HashMap<>();
        
        for (CallLogEntry entry : filteredCalls) {
            long duration = entry.getDuration();
            callerDuration.compute(entry.getNumber(), (k, v) -> (v == null) ? duration : v + duration);
        }

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(callerDuration.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public String getContactNameForNumber(String number) {
        for (CallLogEntry entry : filteredCalls) {
            if (entry.getNumber().equals(number) && !entry.getContactName().isEmpty()) {
                return entry.getContactName();
            }
        }
        return number;
    }
}
