package com.stevecrew.callloganalyzer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Hauptaktivität der CallLogAnalyzer App.
 * 
 * Struktur:
 * - TabLayout oben mit zwei Tabs (Übersicht / Alle Anrufe)
 * - FragmentContainer der je nach Tab das passende Fragment zeigt
 * 
 * Verantwortlichkeiten:
 * - Permission-Handling (READ_CALL_LOG, READ_CONTACTS)
 * - Initialisierung von CallLogHelper und BlacklistManager
 * - Tab-Navigation zwischen Fragments
 * - Detail-Dialog für einzelne Nummern (showCallDetailsForNumber)
 * 
 * Lifecycle:
 * - onCreate: UI aufbauen, Permissions prüfen, Daten laden
 * - onDestroy: Observer stoppen (Memory Leak vermeiden!)
 */
public class MainActivity extends AppCompatActivity {

    // Request-Code für Permission-Dialog (beliebige Zahl, muss nur eindeutig sein)
    private static final int PERMISSION_REQUEST_CODE = 100;

    // === Kern-Komponenten ===
    private CallLogHelper callLogHelper;      // Zugriff auf Anrufdaten
    private BlacklistManager blacklistManager; // Verwaltung ausgeblendeter Nummern
    
    // === UI Fragments ===
    private OverviewFragment overviewFragment;   // Tab 1: Statistiken & Charts
    private AllCallsFragment allCallsFragment;   // Tab 2: Anrufliste

    /**
     * App-Start: UI aufbauen und Daten laden.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // === Manager initialisieren ===
        blacklistManager = new BlacklistManager(this);
        callLogHelper = new CallLogHelper(this);
        callLogHelper.setBlacklistManager(blacklistManager);

        // === Fragments erstellen ===
        overviewFragment = new OverviewFragment();
        allCallsFragment = new AllCallsFragment();

        // === Tab-Navigation einrichten ===
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText("📊 Übersicht"));
        tabLayout.addTab(tabLayout.newTab().setText("📋 Alle Anrufe"));
        
        // Tab-Wechsel: Fragment austauschen
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment fragment = tab.getPosition() == 0 ? overviewFragment : allCallsFragment;
                getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // === Permission prüfen und Daten laden ===
        if (checkPermission()) {
            loadData();
            // Standard: Übersicht-Tab anzeigen
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, overviewFragment)
                .commit();
        } else {
            // Noch keine Permission → User fragen
            requestPermission();
        }
    }

    /**
     * Prüft ob READ_CALL_LOG Permission vorhanden ist.
     */
    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Fordert die benötigten Permissions an.
     * Zeigt den System-Dialog "App möchte auf Anrufliste zugreifen".
     */
    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
            new String[]{
                Manifest.permission.READ_CALL_LOG,  // Hauptpermission für Anrufdaten
                Manifest.permission.READ_CONTACTS   // Für Kontaktnamen-Auflösung
            },
            PERMISSION_REQUEST_CODE);
    }

    /**
     * Callback wenn User auf Permission-Dialog antwortet.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission erteilt → Daten laden und UI zeigen
                loadData();
                getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, overviewFragment)
                    .commit();
            } else {
                // Permission verweigert → App kann nicht funktionieren
                Toast.makeText(this, "Permission denied. Cannot read call log.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Lädt die Anrufdaten und richtet Live-Updates ein.
     * Wird aufgerufen nachdem Permission erteilt wurde.
     */
    private void loadData() {
        // Initiales Laden der Anrufdaten aus der Datenbank
        callLogHelper.loadCallLog();
        
        // === Live-Updates einrichten ===
        // Callback registrieren: Wird aufgerufen wenn ein neuer Anruf ins Log kommt
        callLogHelper.setOnCallLogChangedListener(() -> {
            // Beide Tabs aktualisieren damit Stats & Liste aktuell sind
            if (overviewFragment != null) overviewFragment.updateUI();
            if (allCallsFragment != null) allCallsFragment.updateUI();
        });
        
        // Observer starten - ab jetzt werden Änderungen automatisch erkannt
        callLogHelper.startObserving();
    }
    
    /**
     * App wird beendet: Aufräumen!
     * 
     * WICHTIG: Observer stoppen um Memory Leaks zu vermeiden.
     * Der Observer hält eine Referenz auf den Context - wenn wir ihn
     * nicht deregistrieren, kann die Activity nicht garbage-collected werden.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callLogHelper != null) {
            callLogHelper.stopObserving();
        }
    }

    // === Getter für Fragments ===
    
    public CallLogHelper getCallLogHelper() {
        return callLogHelper;
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    /**
     * Zeigt einen Detail-Dialog für alle Anrufe einer bestimmten Nummer.
     * 
     * Wird aufgerufen wenn User auf einen Eintrag in der Top-Liste oder
     * Anrufliste tippt. Zeigt:
     * - Zusammenfassung (Anzahl, Gesamtdauer, Typen)
     * - Liste der letzten 50 Anrufe mit dieser Nummer
     * - Button zum Ausblenden der Nummer
     * 
     * @param number Telefonnummer für die Details angezeigt werden
     * @param sortBy "calls" oder "duration" - wie die Liste sortiert wird
     */
    public void showCallDetailsForNumber(String number, String sortBy) {
        // Kontaktname holen (oder Nummer falls unbekannt)
        String contactName = callLogHelper.getContactNameForNumber(number);
        
        // Alle Anrufe mit dieser Nummer sammeln
        List<CallLogEntry> calls = new ArrayList<>();
        for (CallLogEntry entry : callLogHelper.getAllCalls()) {
            if (entry.getNumber().equals(number)) {
                calls.add(entry);
            }
        }

        // Nach Dauer sortieren wenn gewünscht (für "Top Duration" Liste)
        if (sortBy.equals("duration")) {
            calls.sort((a, b) -> Long.compare(b.getDuration(), a.getDuration()));
        }

        // === Statistik berechnen ===
        int totalCalls = calls.size();
        long totalDuration = 0;
        int incoming = 0, outgoing = 0, missed = 0;
        for (CallLogEntry call : calls) {
            totalDuration += call.getDuration();
            switch (call.getType()) {
                case CallLogEntry.TYPE_INCOMING: incoming++; break;
                case CallLogEntry.TYPE_OUTGOING: outgoing++; break;
                case CallLogEntry.TYPE_MISSED: missed++; break;
            }
        }

        // === Dialog-Layout aufbauen (programmatisch) ===
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 24, 48, 24);

        // Header mit Telefonnummer
        TextView headerText = new TextView(this);
        headerText.setText("📱  " + number);
        headerText.setTextColor(Color.parseColor("#B3B3B3"));
        headerText.setTextSize(13);
        mainLayout.addView(headerText);

        // Zusammenfassung
        TextView summaryText = new TextView(this);
        String summary = String.format(Locale.getDefault(),
            "\n📊 Gesamt: %d Anrufe  ·  %s\n" +
            "     📥 %d  📤 %d  ❌ %d\n",
            totalCalls, formatDuration(totalDuration),
            incoming, outgoing, missed);
        summaryText.setText(summary);
        summaryText.setTextColor(Color.parseColor("#E0E0E0"));
        summaryText.setTextSize(14);
        summaryText.setPadding(0, 0, 0, 24);
        mainLayout.addView(summaryText);

        // Trennlinie
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(Color.parseColor("#404040"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2));
        mainLayout.addView(divider);

        // === Anruf-Liste (max 50 Einträge) ===
        int count = 0;
        for (CallLogEntry call : calls) {
            count++;
            // Limit auf 50 Einträge für Performance
            if (count > 50) {
                TextView moreText = new TextView(this);
                moreText.setText("\n... und " + (calls.size() - 50) + " weitere Anrufe");
                moreText.setTextColor(Color.parseColor("#808080"));
                moreText.setTextSize(13);
                mainLayout.addView(moreText);
                break;
            }

            // Zeile für einen Anruf
            LinearLayout entryLayout = new LinearLayout(this);
            entryLayout.setOrientation(LinearLayout.HORIZONTAL);
            entryLayout.setPadding(0, 20, 0, 20);

            // Typ-Emoji
            TextView typeText = new TextView(this);
            typeText.setText(getCallTypeEmoji(call.getType()));
            typeText.setTextSize(18);
            typeText.setPadding(0, 0, 24, 0);
            entryLayout.addView(typeText);

            // Datum & Uhrzeit
            LinearLayout dateLayout = new LinearLayout(this);
            dateLayout.setOrientation(LinearLayout.VERTICAL);
            dateLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView dateText = new TextView(this);
            dateText.setText(sdfDate.format(new Date(call.getTimestamp())));
            dateText.setTextColor(Color.parseColor("#E0E0E0"));
            dateText.setTextSize(14);
            dateLayout.addView(dateText);

            TextView timeText = new TextView(this);
            timeText.setText(sdfTime.format(new Date(call.getTimestamp())));
            timeText.setTextColor(Color.parseColor("#808080"));
            timeText.setTextSize(12);
            dateLayout.addView(timeText);

            entryLayout.addView(dateLayout);

            // Dauer
            TextView durationText = new TextView(this);
            durationText.setText(formatDuration(call.getDuration()));
            durationText.setTextColor(Color.parseColor("#4FC3F7"));
            durationText.setTextSize(14);
            durationText.setGravity(android.view.Gravity.END);
            entryLayout.addView(durationText);

            mainLayout.addView(entryLayout);

            // Trennlinie zwischen Einträgen
            android.view.View entryDivider = new android.view.View(this);
            entryDivider.setBackgroundColor(Color.parseColor("#333333"));
            entryDivider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
            mainLayout.addView(entryDivider);
        }

        // Scrollbar für lange Listen
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(mainLayout);

        // === Dialog anzeigen ===
        new AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle(contactName)
            .setView(scrollView)
            .setPositiveButton("Ausblenden", (dialog, which) -> {
                // Nummer zur Blacklist hinzufügen
                blacklistManager.addNumber(number);
                // Filter neu anwenden und UI aktualisieren
                callLogHelper.setTimePeriod(callLogHelper.getCurrentPeriod());
                if (overviewFragment != null) overviewFragment.updateUI();
                if (allCallsFragment != null) allCallsFragment.updateUI();
                Toast.makeText(this, "✓ " + number + " ausgeblendet", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Schließen", null)
            .show();
    }

    /**
     * Gibt das passende Emoji für einen Anruftyp zurück.
     */
    private String getCallTypeEmoji(int type) {
        switch (type) {
            case CallLogEntry.TYPE_INCOMING: return "📥";  // Eingehend
            case CallLogEntry.TYPE_OUTGOING: return "📤";  // Ausgehend
            case CallLogEntry.TYPE_MISSED: return "❌";    // Verpasst
            case CallLogEntry.TYPE_REJECTED: return "🚫";  // Abgelehnt
            default: return "📞";
        }
    }

    /**
     * Formatiert Sekunden als lesbaren Dauer-String.
     * Beispiele: "5s", "3m 45s", "1h 23m"
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        else if (minutes > 0) return String.format(Locale.getDefault(), "%dm %ds", minutes, secs);
        return String.format(Locale.getDefault(), "%ds", secs);
    }
}
