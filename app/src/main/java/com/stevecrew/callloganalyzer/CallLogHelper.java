package com.stevecrew.callloganalyzer;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallLogHelper {
    
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

    public CallLogHelper(Context context) {
        this.context = context;
        this.allCalls = new ArrayList<>();
        this.filteredCalls = new ArrayList<>();
        this.blacklistManager = new BlacklistManager(context);
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
