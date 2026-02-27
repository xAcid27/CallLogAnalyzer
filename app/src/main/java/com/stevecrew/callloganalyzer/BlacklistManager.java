package com.stevecrew.callloganalyzer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Verwaltet ausgeblendete Telefonnummern (Blacklist).
 * 
 * Ermöglicht es dem Nutzer, bestimmte Nummern aus der Statistik auszublenden.
 * Praktisch für:
 * - Spam-Nummern die die Statistik verfälschen
 * - Geschäftliche Nummern die man privat nicht sehen will
 * - Hotlines oder Servicenummern
 * 
 * Die Blacklist wird in SharedPreferences gespeichert und bleibt
 * auch nach App-Neustart erhalten.
 */
public class BlacklistManager {
    
    // SharedPreferences Schlüssel
    private static final String PREFS_NAME = "call_log_blacklist";
    private static final String KEY_BLACKLIST = "blacklisted_numbers";
    
    private final SharedPreferences prefs;
    private final Set<String> blacklistedNumbers;  // Lokale Kopie für schnellen Zugriff
    
    /**
     * Erstellt einen neuen BlacklistManager.
     * Lädt automatisch die gespeicherte Blacklist aus SharedPreferences.
     * 
     * @param context Android Context für SharedPreferences-Zugriff
     */
    public BlacklistManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Kopie erstellen um Original-Set nicht zu modifizieren
        blacklistedNumbers = new HashSet<>(prefs.getStringSet(KEY_BLACKLIST, new HashSet<>()));
    }
    
    /**
     * Fügt eine Nummer zur Blacklist hinzu.
     * Die Nummer wird normalisiert (nur Ziffern + Plus behalten).
     * 
     * @param number Telefonnummer zum Ausblenden
     */
    public void addNumber(String number) {
        String normalized = normalizeNumber(number);
        blacklistedNumbers.add(normalized);
        saveToPrefs();
    }
    
    /**
     * Entfernt eine Nummer von der Blacklist.
     * 
     * @param number Telefonnummer die wieder angezeigt werden soll
     */
    public void removeNumber(String number) {
        String normalized = normalizeNumber(number);
        blacklistedNumbers.remove(normalized);
        saveToPrefs();
    }
    
    /**
     * Prüft ob eine Nummer auf der Blacklist steht.
     * 
     * Verwendet "contains"-Logik um verschiedene Nummernformate zu matchen:
     * - +491234567890
     * - 01234567890
     * - 1234567890
     * 
     * @param number Zu prüfende Telefonnummer
     * @return true wenn die Nummer ausgeblendet werden soll
     */
    public boolean isBlacklisted(String number) {
        String normalized = normalizeNumber(number);
        for (String blacklisted : blacklistedNumbers) {
            // Flexibler Vergleich: Prüft ob eine Nummer die andere enthält
            // Damit matchen +49123... und 0123... auf dieselbe Nummer
            if (normalized.contains(blacklisted) || blacklisted.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gibt eine Kopie aller ausgeblendeten Nummern zurück.
     * Kopie um unbeabsichtigte Änderungen zu verhindern.
     */
    public Set<String> getBlacklistedNumbers() {
        return new HashSet<>(blacklistedNumbers);
    }
    
    /**
     * Löscht die gesamte Blacklist.
     * Alle Nummern werden wieder in der Statistik angezeigt.
     */
    public void clear() {
        blacklistedNumbers.clear();
        saveToPrefs();
    }
    
    /**
     * Normalisiert eine Telefonnummer für konsistenten Vergleich.
     * Entfernt alle Zeichen außer Ziffern und Plus.
     * 
     * Beispiel: "+49 (123) 456-7890" → "+491234567890"
     */
    private String normalizeNumber(String number) {
        return number.replaceAll("[^0-9+]", "");
    }
    
    /**
     * Speichert die aktuelle Blacklist in SharedPreferences.
     * Verwendet apply() für asynchrones Speichern (blockiert nicht die UI).
     */
    private void saveToPrefs() {
        prefs.edit().putStringSet(KEY_BLACKLIST, blacklistedNumbers).apply();
    }
}
