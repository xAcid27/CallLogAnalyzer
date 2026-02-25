package com.stevecrew.callloganalyzer;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallLogHelper {
    
    private final Context context;
    private final List<CallLogEntry> allCalls;

    public CallLogHelper(Context context) {
        this.context = context;
        this.allCalls = new ArrayList<>();
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
    }

    public List<CallLogEntry> getAllCalls() {
        return allCalls;
    }

    public int getIncomingCount() {
        int count = 0;
        for (CallLogEntry entry : allCalls) {
            if (entry.getType() == CallLogEntry.TYPE_INCOMING) count++;
        }
        return count;
    }

    public int getOutgoingCount() {
        int count = 0;
        for (CallLogEntry entry : allCalls) {
            if (entry.getType() == CallLogEntry.TYPE_OUTGOING) count++;
        }
        return count;
    }

    public int getMissedCount() {
        int count = 0;
        for (CallLogEntry entry : allCalls) {
            if (entry.getType() == CallLogEntry.TYPE_MISSED) count++;
        }
        return count;
    }

    public int getRejectedCount() {
        int count = 0;
        for (CallLogEntry entry : allCalls) {
            if (entry.getType() == CallLogEntry.TYPE_REJECTED) count++;
        }
        return count;
    }

    // Top 10 by call frequency
    public List<Map.Entry<String, Integer>> getTopCallers(int limit) {
        Map<String, Integer> callerCount = new HashMap<>();
        
        for (CallLogEntry entry : allCalls) {
            callerCount.compute(entry.getNumber(), (k, v) -> (v == null) ? 1 : v + 1);
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(callerCount.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // Top 10 by total call duration
    public List<Map.Entry<String, Long>> getTopDuration(int limit) {
        Map<String, Long> callerDuration = new HashMap<>();
        
        for (CallLogEntry entry : allCalls) {
            long duration = entry.getDuration();
            callerDuration.compute(entry.getNumber(), (k, v) -> (v == null) ? duration : v + duration);
        }

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(callerDuration.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public String getContactNameForNumber(String number) {
        for (CallLogEntry entry : allCalls) {
            if (entry.getNumber().equals(number) && !entry.getContactName().isEmpty()) {
                return entry.getContactName();
            }
        }
        return number;
    }
}
