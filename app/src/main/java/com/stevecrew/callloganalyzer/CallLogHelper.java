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
    
    private final Context context;
    private final List<CallLogEntry> allCalls;
    private final List<CallLogEntry> filteredCalls;
    private int currentPeriod = PERIOD_ALL;

    public CallLogHelper(Context context) {
        this.context = context;
        this.allCalls = new ArrayList<>();
        this.filteredCalls = new ArrayList<>();
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
    
    private void applyFilter() {
        filteredCalls.clear();
        
        if (currentPeriod == PERIOD_ALL) {
            filteredCalls.addAll(allCalls);
            return;
        }
        
        long cutoffTime = System.currentTimeMillis();
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
        
        for (CallLogEntry entry : allCalls) {
            if (entry.getTimestamp() >= cutoffTime) {
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
            if (entry.getType() == CallLogEntry.TYPE_INCOMING) count++;
        }
        return count;
    }

    public int getOutgoingCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (entry.getType() == CallLogEntry.TYPE_OUTGOING) count++;
        }
        return count;
    }

    public int getMissedCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (entry.getType() == CallLogEntry.TYPE_MISSED) count++;
        }
        return count;
    }

    public int getRejectedCount() {
        int count = 0;
        for (CallLogEntry entry : filteredCalls) {
            if (entry.getType() == CallLogEntry.TYPE_REJECTED) count++;
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
