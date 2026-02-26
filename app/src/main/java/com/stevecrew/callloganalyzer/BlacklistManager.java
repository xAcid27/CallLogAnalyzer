package com.stevecrew.callloganalyzer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class BlacklistManager {
    
    private static final String PREFS_NAME = "call_log_blacklist";
    private static final String KEY_BLACKLIST = "blacklisted_numbers";
    
    private final SharedPreferences prefs;
    private final Set<String> blacklistedNumbers;
    
    public BlacklistManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        blacklistedNumbers = new HashSet<>(prefs.getStringSet(KEY_BLACKLIST, new HashSet<>()));
    }
    
    public void addNumber(String number) {
        String normalized = normalizeNumber(number);
        blacklistedNumbers.add(normalized);
        saveToPrefs();
    }
    
    public void removeNumber(String number) {
        String normalized = normalizeNumber(number);
        blacklistedNumbers.remove(normalized);
        saveToPrefs();
    }
    
    public boolean isBlacklisted(String number) {
        String normalized = normalizeNumber(number);
        for (String blacklisted : blacklistedNumbers) {
            // Check if either contains the other (handles different formats)
            if (normalized.contains(blacklisted) || blacklisted.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
    
    public Set<String> getBlacklistedNumbers() {
        return new HashSet<>(blacklistedNumbers);
    }
    
    public void clear() {
        blacklistedNumbers.clear();
        saveToPrefs();
    }
    
    private String normalizeNumber(String number) {
        // Remove all non-digit characters except +
        return number.replaceAll("[^0-9+]", "");
    }
    
    private void saveToPrefs() {
        prefs.edit().putStringSet(KEY_BLACKLIST, blacklistedNumbers).apply();
    }
}
