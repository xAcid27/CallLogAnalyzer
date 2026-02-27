package com.stevecrew.callloganalyzer;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment für die Übersichts-Ansicht (Tab 1).
 * 
 * Zeigt:
 * - Statistik-Kacheln (Eingehend/Ausgehend/Verpasst/Abgelehnt)
 * - Pie-Chart mit Anrufverteilung
 * - Top 10 häufigste Anrufer
 * - Top 10 längste Gespräche
 * - Zeitraum-Filter (Dropdown)
 * - Export-Button (CSV)
 * - Blacklist-Button (Nummern ausblenden)
 * 
 * Verwendet MPAndroidChart Bibliothek für das Pie-Chart.
 */
public class OverviewFragment extends Fragment {

    // === UI-Elemente ===
    private TextView tvIncoming, tvOutgoing, tvMissed, tvRejected;  // Statistik-Kacheln
    private TextView tvTopCallers, tvTopDuration;                     // Top-Listen
    private TextView tvStatus, tvTotalCalls;                          // Status & Gesamt
    private Button btnExport, btnBlacklist;                           // Action-Buttons
    private PieChart pieChart;                                        // Chart
    private Spinner spinnerTimePeriod;                                // Zeitraum-Dropdown

    // === Farben für Anruftypen (Material Design) ===
    private final int COLOR_GREEN = Color.parseColor("#2E7D32");   // Eingehend
    private final int COLOR_BLUE = Color.parseColor("#1565C0");    // Ausgehend
    private final int COLOR_ORANGE = Color.parseColor("#EF6C00");  // Verpasst
    private final int COLOR_RED = Color.parseColor("#C62828");     // Abgelehnt

    // Cache für Top-Listen (für Click-Handler)
    private List<Map.Entry<String, Integer>> lastTopCallers;
    private List<Map.Entry<String, Long>> lastTopDuration;

    // Optionen für Zeitraum-Dropdown
    private final String[] timePeriodOptions = {
        "Alle Anrufe",
        "Letzte 7 Tage",
        "Letzte 30 Tage",
        "Letzte 3 Monate",
        "Letzte 6 Monate",
        "Letztes Jahr"
    };

    /**
     * Erstellt die View-Hierarchie des Fragments.
     * Wird aufgerufen wenn Fragment zum ersten Mal angezeigt wird.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

        // === UI-Elemente verbinden ===
        tvIncoming = view.findViewById(R.id.tvIncoming);
        tvOutgoing = view.findViewById(R.id.tvOutgoing);
        tvMissed = view.findViewById(R.id.tvMissed);
        tvRejected = view.findViewById(R.id.tvRejected);
        tvTopCallers = view.findViewById(R.id.tvTopCallers);
        tvTopDuration = view.findViewById(R.id.tvTopDuration);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvTotalCalls = view.findViewById(R.id.tvTotalCalls);
        btnExport = view.findViewById(R.id.btnExport);
        btnBlacklist = view.findViewById(R.id.btnBlacklist);
        pieChart = view.findViewById(R.id.pieChart);
        spinnerTimePeriod = view.findViewById(R.id.spinnerTimePeriod);

        // === Komponenten initialisieren ===
        setupPieChart();
        setupTimePeriodSpinner();

        // === Click-Handler für Buttons ===
        btnExport.setOnClickListener(v -> exportData());
        btnBlacklist.setOnClickListener(v -> showBlacklistDialog());

        return view;
    }

    /**
     * Wird aufgerufen wenn Fragment sichtbar wird.
     * Aktualisiert UI mit aktuellen Daten.
     */
    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    /**
     * Konfiguriert das Pie-Chart (MPAndroidChart).
     * 
     * Einstellungen:
     * - Donut-Style mit Loch in der Mitte
     * - Prozent-Anzeige
     * - Dark Theme Farben
     * - Animations
     */
    private void setupPieChart() {
        pieChart.setUsePercentValues(true);           // Prozent statt absolute Werte
        pieChart.getDescription().setEnabled(false);  // Keine Beschreibung
        
        // Donut-Stil: Loch in der Mitte
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.parseColor("#2D2D2D"));
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setTransparentCircleColor(Color.parseColor("#2D2D2D"));
        pieChart.setTransparentCircleAlpha(100);
        
        // Center Text (zeigt "Total X")
        pieChart.setDrawCenterText(true);
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setCenterTextSize(16f);
        
        // Interaktion
        pieChart.setRotationEnabled(true);           // Drehen erlaubt
        pieChart.setHighlightPerTapEnabled(true);    // Segment hervorheben bei Tap
        
        // Legende
        pieChart.getLegend().setEnabled(true);
        pieChart.getLegend().setTextColor(Color.parseColor("#B3B3B3"));
        pieChart.getLegend().setTextSize(12f);
        
        // Labels auf den Segmenten
        pieChart.setEntryLabelColor(Color.WHITE);
        pieChart.setEntryLabelTextSize(11f);
        
        // Animation beim ersten Anzeigen
        pieChart.animateY(800);
    }

    /**
     * Konfiguriert den Zeitraum-Filter Dropdown.
     * 
     * Verwendet Custom ArrayAdapter für Dark Theme Styling.
     */
    private void setupTimePeriodSpinner() {
        // Custom Adapter für Dark Theme
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_spinner_item, timePeriodOptions) {
            
            // Geschlossener Zustand (ausgewähltes Item)
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(15);
                return view;
            }

            // Dropdown-Liste
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(Color.WHITE);
                textView.setBackgroundColor(Color.parseColor("#3D3D3D"));
                textView.setPadding(24, 24, 24, 24);
                return view;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimePeriod.setAdapter(adapter);

        // Selection-Handler: Filter anwenden wenn Zeitraum geändert
        spinnerTimePeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null && activity.getCallLogHelper() != null) {
                    // Position entspricht PERIOD_* Konstante in CallLogHelper
                    activity.getCallLogHelper().setTimePeriod(position);
                    updateUI();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Aktualisiert alle UI-Elemente mit aktuellen Daten.
     * 
     * Wird aufgerufen bei:
     * - Fragment wird sichtbar (onResume)
     * - Zeitraum-Filter geändert
     * - Neuer Anruf erkannt (via ContentObserver)
     * - Blacklist geändert
     */
    public void updateUI() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        
        CallLogHelper callLogHelper = activity.getCallLogHelper();
        if (callLogHelper == null) return;

        // === Statistik-Werte holen ===
        int incoming = callLogHelper.getIncomingCount();
        int outgoing = callLogHelper.getOutgoingCount();
        int missed = callLogHelper.getMissedCount();
        int rejected = callLogHelper.getRejectedCount();
        int total = callLogHelper.getAllCalls().size();

        // === Kacheln aktualisieren ===
        tvIncoming.setText(formatNumber(incoming));
        tvOutgoing.setText(formatNumber(outgoing));
        tvMissed.setText(formatNumber(missed));
        tvRejected.setText(formatNumber(rejected));
        tvTotalCalls.setText(total + " calls");

        // === Pie-Chart aktualisieren ===
        updatePieChart(incoming, outgoing, missed, rejected);

        // === Top Callers Liste ===
        lastTopCallers = callLogHelper.getTopCallers(10);
        StringBuilder callerSb = new StringBuilder();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : lastTopCallers) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            // Namen kürzen wenn zu lang
            if (name.length() > 16) name = name.substring(0, 13) + "...";
            callerSb.append(String.format(Locale.getDefault(), "%s %s  ·  %d\n",
                    getRankPrefix(rank), name, entry.getValue()));
            rank++;
        }
        tvTopCallers.setText(callerSb.toString().trim());
        tvTopCallers.setOnClickListener(v -> showTopCallersDetail());

        // === Top Duration Liste ===
        lastTopDuration = callLogHelper.getTopDuration(10);
        StringBuilder durationSb = new StringBuilder();
        rank = 1;
        for (Map.Entry<String, Long> entry : lastTopDuration) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            if (name.length() > 16) name = name.substring(0, 13) + "...";
            durationSb.append(String.format(Locale.getDefault(), "%s %s  ·  %s\n",
                    getRankPrefix(rank), name, formatDuration(entry.getValue())));
            rank++;
        }
        tvTopDuration.setText(durationSb.toString().trim());
        tvTopDuration.setOnClickListener(v -> showTopDurationDetail());

        // Status-Text
        tvStatus.setText("✓ Last updated just now");
    }

    /**
     * Aktualisiert das Pie-Chart mit neuen Werten.
     */
    private void updatePieChart(int incoming, int outgoing, int missed, int rejected) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        // Nur Segmente mit Werten > 0 hinzufügen
        if (incoming > 0) { entries.add(new PieEntry(incoming, "Incoming")); colors.add(COLOR_GREEN); }
        if (outgoing > 0) { entries.add(new PieEntry(outgoing, "Outgoing")); colors.add(COLOR_BLUE); }
        if (missed > 0) { entries.add(new PieEntry(missed, "Missed")); colors.add(COLOR_ORANGE); }
        if (rejected > 0) { entries.add(new PieEntry(rejected, "Rejected")); colors.add(COLOR_RED); }

        // Keine Daten → "No data" anzeigen
        if (entries.isEmpty()) {
            pieChart.setData(null);
            pieChart.setCenterText("No data");
            pieChart.invalidate();
            return;
        }

        // DataSet konfigurieren
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);           // Abstand zwischen Segmenten
        dataSet.setSelectionShift(8f);       // Verschiebung bei Auswahl
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        // Data mit Prozent-Formatter
        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));

        // Chart aktualisieren
        pieChart.setData(data);
        pieChart.setCenterText("Total\n" + (incoming + outgoing + missed + rejected));
        pieChart.invalidate();  // Neuzeichnen erzwingen
    }

    /**
     * Formatiert große Zahlen kompakt (1234 → 1.2k).
     */
    private String formatNumber(int num) {
        if (num >= 1000) return String.format(Locale.getDefault(), "%.1fk", num / 1000.0);
        return String.valueOf(num);
    }

    /**
     * Gibt das Rang-Prefix zurück (🥇/🥈/🥉 oder "4.")
     */
    private String getRankPrefix(int rank) {
        switch (rank) {
            case 1: return "🥇";
            case 2: return "🥈";
            case 3: return "🥉";
            default: return String.format(Locale.getDefault(), "%2d.", rank);
        }
    }

    /**
     * Formatiert Sekunden als lesbaren Dauer-String.
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        else if (minutes > 0) return String.format(Locale.getDefault(), "%dm %ds", minutes, secs);
        return String.format(Locale.getDefault(), "%ds", secs);
    }

    /**
     * Zeigt Detail-Dialog für Top Callers.
     * Tap auf Eintrag öffnet Anruf-Details für diese Nummer.
     */
    private void showTopCallersDetail() {
        if (lastTopCallers == null || lastTopCallers.isEmpty()) return;
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        // Liste der Einträge für Dialog
        String[] items = new String[lastTopCallers.size()];
        for (int i = 0; i < lastTopCallers.size(); i++) {
            Map.Entry<String, Integer> entry = lastTopCallers.get(i);
            String name = activity.getCallLogHelper().getContactNameForNumber(entry.getKey());
            items[i] = getRankPrefix(i + 1) + " " + name + " (" + entry.getValue() + " Anrufe)";
        }

        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📞 Top Callers")
            .setItems(items, (dialog, which) -> {
                // Bei Tap: Detail-Dialog für diese Nummer öffnen
                String number = lastTopCallers.get(which).getKey();
                activity.showCallDetailsForNumber(number, "calls");
            })
            .setNegativeButton("Schließen", null)
            .show();
    }

    /**
     * Zeigt Detail-Dialog für Top Duration.
     * Tap auf Eintrag öffnet Anruf-Details (sortiert nach Dauer).
     */
    private void showTopDurationDetail() {
        if (lastTopDuration == null || lastTopDuration.isEmpty()) return;
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        String[] items = new String[lastTopDuration.size()];
        for (int i = 0; i < lastTopDuration.size(); i++) {
            Map.Entry<String, Long> entry = lastTopDuration.get(i);
            String name = activity.getCallLogHelper().getContactNameForNumber(entry.getKey());
            items[i] = getRankPrefix(i + 1) + " " + name + " (" + formatDuration(entry.getValue()) + ")";
        }

        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("⏱️ Longest Calls")
            .setItems(items, (dialog, which) -> {
                String number = lastTopDuration.get(which).getKey();
                // "duration" sortiert die Details nach Dauer statt Datum
                activity.showCallDetailsForNumber(number, "duration");
            })
            .setNegativeButton("Schließen", null)
            .show();
    }

    /**
     * Exportiert die Anrufliste als CSV-Datei.
     */
    private void exportData() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        
        // Prüfen ob Daten vorhanden
        if (activity.getCallLogHelper().getAllCalls().isEmpty()) {
            Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // Export durchführen
        String path = CSVExporter.exportToCSV(requireContext(), activity.getCallLogHelper().getAllCalls());
        if (path != null) {
            Toast.makeText(requireContext(), "✓ Exported to Downloads", Toast.LENGTH_LONG).show();
            tvStatus.setText("✓ Exported to " + path);
        } else {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Zeigt den Blacklist-Dialog zum Verwalten ausgeblendeter Nummern.
     * 
     * Features:
     * - Liste aktuell ausgeblendeter Nummern
     * - Eingabefeld zum Hinzufügen
     * - "Alle löschen" Button
     */
    private void showBlacklistDialog() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        
        BlacklistManager blacklistManager = activity.getBlacklistManager();

        // === Dialog-Layout aufbauen ===
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

        // Aktuelle Blacklist anzeigen
        java.util.Set<String> blacklisted = blacklistManager.getBlacklistedNumbers();

        TextView infoText = new TextView(requireContext());
        if (blacklisted.isEmpty()) {
            infoText.setText("Keine Nummern ausgeblendet.");
        } else {
            StringBuilder sb = new StringBuilder("Ausgeblendet:\n");
            for (String num : blacklisted) sb.append("• ").append(num).append("\n");
            infoText.setText(sb.toString().trim());
        }
        infoText.setTextColor(Color.parseColor("#B3B3B3"));
        infoText.setPadding(0, 0, 0, 24);
        layout.addView(infoText);

        // Eingabefeld für neue Nummer
        EditText input = new EditText(requireContext());
        input.setHint("Nummer eingeben...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#808080"));
        input.setBackgroundColor(Color.parseColor("#3D3D3D"));
        input.setPadding(24, 24, 24, 24);
        layout.addView(input);

        // === Dialog erstellen ===
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("🚫 Nummern ausblenden")
            .setView(layout)
            .setPositiveButton("Hinzufügen", (dialog, which) -> {
                String number = input.getText().toString().trim();
                if (!number.isEmpty()) {
                    // Nummer zur Blacklist hinzufügen
                    blacklistManager.addNumber(number);
                    // Filter neu anwenden
                    activity.getCallLogHelper().setTimePeriod(activity.getCallLogHelper().getCurrentPeriod());
                    updateUI();
                    Toast.makeText(requireContext(), "✓ " + number + " ausgeblendet", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Abbrechen", null);

        // "Alle löschen" nur anzeigen wenn Blacklist nicht leer
        if (!blacklisted.isEmpty()) {
            builder.setNeutralButton("Alle löschen", (dialog, which) -> {
                blacklistManager.clear();
                activity.getCallLogHelper().setTimePeriod(activity.getCallLogHelper().getCurrentPeriod());
                updateUI();
                Toast.makeText(requireContext(), "✓ Filter zurückgesetzt", Toast.LENGTH_SHORT).show();
            });
        }

        builder.show();
    }
}
