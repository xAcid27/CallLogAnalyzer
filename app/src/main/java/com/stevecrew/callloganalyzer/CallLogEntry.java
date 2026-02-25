package com.stevecrew.callloganalyzer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallLogEntry {
    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;
    public static final int TYPE_MISSED = 3;
    public static final int TYPE_REJECTED = 5;

    private String number;
    private String contactName;
    private int type;
    private long duration; // in seconds
    private long timestamp;

    public CallLogEntry(String number, String contactName, int type, long duration, long timestamp) {
        this.number = number;
        this.contactName = contactName != null ? contactName : "";
        this.type = type;
        this.duration = duration;
        this.timestamp = timestamp;
    }

    public String getNumber() { return number; }
    public String getContactName() { return contactName; }
    public int getType() { return type; }
    public long getDuration() { return duration; }
    public long getTimestamp() { return timestamp; }

    public String getTypeString() {
        switch (type) {
            case TYPE_INCOMING: return "Incoming";
            case TYPE_OUTGOING: return "Outgoing";
            case TYPE_MISSED: return "Missed";
            case TYPE_REJECTED: return "Rejected";
            default: return "Unknown";
        }
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getDisplayName() {
        return contactName.isEmpty() ? number : contactName;
    }

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
