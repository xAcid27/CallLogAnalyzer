package com.stevecrew.callloganalyzer;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView tvIncoming, tvOutgoing, tvMissed, tvRejected;
    private TextView tvTopCallers, tvTopDuration, tvStatus, tvTotalCalls;
    private Button btnExport, btnBlacklist;
    private PieChart pieChart;
    private Spinner spinnerTimePeriod;
    
    private CallLogHelper callLogHelper;
    private BlacklistManager blacklistManager;
    
    // Store top data for click handlers
    private List<Map.Entry<String, Integer>> lastTopCallers;
    private List<Map.Entry<String, Long>> lastTopDuration;
    
    private final String[] timePeriodOptions = {
        "Alle Anrufe",
        "Letzte 7 Tage",
        "Letzte 30 Tage",
        "Letzte 3 Monate",
        "Letzte 6 Monate",
        "Letztes Jahr"
    };

    // Colors matching our theme
    private final int COLOR_GREEN = Color.parseColor("#2E7D32");
    private final int COLOR_BLUE = Color.parseColor("#1565C0");
    private final int COLOR_ORANGE = Color.parseColor("#EF6C00");
    private final int COLOR_RED = Color.parseColor("#C62828");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        tvIncoming = findViewById(R.id.tvIncoming);
        tvOutgoing = findViewById(R.id.tvOutgoing);
        tvMissed = findViewById(R.id.tvMissed);
        tvRejected = findViewById(R.id.tvRejected);
        tvTopCallers = findViewById(R.id.tvTopCallers);
        tvTopDuration = findViewById(R.id.tvTopDuration);
        tvStatus = findViewById(R.id.tvStatus);
        tvTotalCalls = findViewById(R.id.tvTotalCalls);
        btnExport = findViewById(R.id.btnExport);
        btnBlacklist = findViewById(R.id.btnBlacklist);
        pieChart = findViewById(R.id.pieChart);
        spinnerTimePeriod = findViewById(R.id.spinnerTimePeriod);

        setupPieChart();
        setupTimePeriodSpinner();
        
        blacklistManager = new BlacklistManager(this);
        callLogHelper = new CallLogHelper(this);
        callLogHelper.setBlacklistManager(blacklistManager);

        btnExport.setOnClickListener(v -> exportData());
        btnBlacklist.setOnClickListener(v -> showBlacklistDialog());

        // Check permissions
        if (checkPermission()) {
            loadData();
        } else {
            requestPermission();
        }
    }

    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.parseColor("#2D2D2D"));
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setTransparentCircleColor(Color.parseColor("#2D2D2D"));
        pieChart.setTransparentCircleAlpha(100);
        pieChart.setDrawCenterText(true);
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setCenterTextSize(16f);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.getLegend().setEnabled(true);
        pieChart.getLegend().setTextColor(Color.parseColor("#B3B3B3"));
        pieChart.getLegend().setTextSize(12f);
        pieChart.setEntryLabelColor(Color.WHITE);
        pieChart.setEntryLabelTextSize(11f);
        pieChart.animateY(800);
    }

    private void setupTimePeriodSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 
                android.R.layout.simple_spinner_item, timePeriodOptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(15);
                return view;
            }

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
        
        spinnerTimePeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (callLogHelper != null) {
                    callLogHelper.setTimePeriod(position);
                    updateUI();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void updatePieChart(int incoming, int outgoing, int missed, int rejected) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        
        if (incoming > 0) {
            entries.add(new PieEntry(incoming, "Incoming"));
            colors.add(COLOR_GREEN);
        }
        if (outgoing > 0) {
            entries.add(new PieEntry(outgoing, "Outgoing"));
            colors.add(COLOR_BLUE);
        }
        if (missed > 0) {
            entries.add(new PieEntry(missed, "Missed"));
            colors.add(COLOR_ORANGE);
        }
        if (rejected > 0) {
            entries.add(new PieEntry(rejected, "Rejected"));
            colors.add(COLOR_RED);
        }

        if (entries.isEmpty()) {
            pieChart.setData(null);
            pieChart.setCenterText("No data");
            pieChart.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));
        
        pieChart.setData(data);
        pieChart.setCenterText("Total\n" + (incoming + outgoing + missed + rejected));
        pieChart.invalidate();
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) 
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, 
            new String[]{
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS
            }, 
            PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadData();
            } else {
                Toast.makeText(this, "Permission denied. Cannot read call log.", Toast.LENGTH_LONG).show();
                tvStatus.setText("⚠️ Permission denied - grant access in Settings");
            }
        }
    }

    private void loadData() {
        callLogHelper.loadCallLog();
        updateUI();
    }

    private void updateUI() {
        // Update counts
        int incoming = callLogHelper.getIncomingCount();
        int outgoing = callLogHelper.getOutgoingCount();
        int missed = callLogHelper.getMissedCount();
        int rejected = callLogHelper.getRejectedCount();
        int total = callLogHelper.getAllCalls().size();
        
        tvIncoming.setText(formatNumber(incoming));
        tvOutgoing.setText(formatNumber(outgoing));
        tvMissed.setText(formatNumber(missed));
        tvRejected.setText(formatNumber(rejected));
        tvTotalCalls.setText(total + " calls");

        // Update pie chart
        updatePieChart(incoming, outgoing, missed, rejected);

        // Top 10 Callers - pretty format with medals
        lastTopCallers = callLogHelper.getTopCallers(10);
        StringBuilder callerSb = new StringBuilder();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : lastTopCallers) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            if (name.length() > 16) name = name.substring(0, 13) + "...";
            int calls = entry.getValue();
            String prefix = getRankPrefix(rank);
            callerSb.append(String.format(Locale.getDefault(), "%s %s  ·  %d\n", 
                    prefix, name, calls));
            rank++;
        }
        tvTopCallers.setText(callerSb.toString().trim());
        tvTopCallers.setOnClickListener(v -> showTopCallersDetail());

        // Top 10 Duration - pretty format with medals
        lastTopDuration = callLogHelper.getTopDuration(10);
        StringBuilder durationSb = new StringBuilder();
        rank = 1;
        for (Map.Entry<String, Long> entry : lastTopDuration) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            if (name.length() > 16) name = name.substring(0, 13) + "...";
            long duration = entry.getValue();
            String formattedDuration = formatDuration(duration);
            String prefix = getRankPrefix(rank);
            durationSb.append(String.format(Locale.getDefault(), "%s %s  ·  %s\n", 
                    prefix, name, formattedDuration));
            rank++;
        }
        tvTopDuration.setText(durationSb.toString().trim());
        tvTopDuration.setOnClickListener(v -> showTopDurationDetail());

        tvStatus.setText("✓ Last updated just now");
    }

    private String formatNumber(int num) {
        if (num >= 1000) {
            return String.format(Locale.getDefault(), "%.1fk", num / 1000.0);
        }
        return String.valueOf(num);
    }

    private String getRankPrefix(int rank) {
        switch (rank) {
            case 1: return "🥇";
            case 2: return "🥈";
            case 3: return "🥉";
            default: return String.format(Locale.getDefault(), "%2d.", rank);
        }
    }

    private String generateBar(int value, int max, int barLength) {
        int filled = (int) Math.ceil((double) value / max * barLength);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, secs);
        }
        return String.format(Locale.getDefault(), "%ds", secs);
    }

    private void exportData() {
        if (callLogHelper.getAllCalls().isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        String path = CSVExporter.exportToCSV(this, callLogHelper.getAllCalls());
        if (path != null) {
            Toast.makeText(this, "✓ Exported to Downloads", Toast.LENGTH_LONG).show();
            tvStatus.setText("✓ Exported to " + path);
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
            tvStatus.setText("✗ Export failed");
        }
    }

    private void showTopCallersDetail() {
        if (lastTopCallers == null || lastTopCallers.isEmpty()) return;
        
        String[] items = new String[lastTopCallers.size()];
        int i = 0;
        for (Map.Entry<String, Integer> entry : lastTopCallers) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            items[i] = getRankPrefix(i + 1) + " " + name + " (" + entry.getValue() + " Anrufe)";
            i++;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle("📞 Top Callers - Details");
        builder.setItems(items, (dialog, which) -> {
            String number = lastTopCallers.get(which).getKey();
            showCallDetailsForNumber(number, "calls");
        });
        builder.setNegativeButton("Schließen", null);
        builder.show();
    }
    
    private void showTopDurationDetail() {
        if (lastTopDuration == null || lastTopDuration.isEmpty()) return;
        
        String[] items = new String[lastTopDuration.size()];
        int i = 0;
        for (Map.Entry<String, Long> entry : lastTopDuration) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            items[i] = getRankPrefix(i + 1) + " " + name + " (" + formatDuration(entry.getValue()) + ")";
            i++;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle("⏱️ Longest Calls - Details");
        builder.setItems(items, (dialog, which) -> {
            String number = lastTopDuration.get(which).getKey();
            showCallDetailsForNumber(number, "duration");
        });
        builder.setNegativeButton("Schließen", null);
        builder.show();
    }
    
    private void showCallDetailsForNumber(String number, String sortBy) {
        String contactName = callLogHelper.getContactNameForNumber(number);
        List<CallLogEntry> calls = new ArrayList<>();
        
        for (CallLogEntry entry : callLogHelper.getAllCalls()) {
            if (entry.getNumber().equals(number)) {
                calls.add(entry);
            }
        }
        
        // Sort by duration if requested
        if (sortBy.equals("duration")) {
            calls.sort((a, b) -> Long.compare(b.getDuration(), a.getDuration()));
        }
        
        // Calculate totals
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
        
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
        
        // Build the layout
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 24, 48, 24);
        
        // Header with number and summary
        TextView headerText = new TextView(this);
        headerText.setText("📱  " + number);
        headerText.setTextColor(Color.parseColor("#B3B3B3"));
        headerText.setTextSize(13);
        mainLayout.addView(headerText);
        
        // Summary stats
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
        
        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#404040"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2));
        mainLayout.addView(divider);
        
        // Call entries
        int count = 0;
        for (CallLogEntry call : calls) {
            count++;
            if (count > 50) {
                TextView moreText = new TextView(this);
                moreText.setText("\n... und " + (calls.size() - 50) + " weitere Anrufe");
                moreText.setTextColor(Color.parseColor("#808080"));
                moreText.setTextSize(13);
                mainLayout.addView(moreText);
                break;
            }
            
            LinearLayout entryLayout = new LinearLayout(this);
            entryLayout.setOrientation(LinearLayout.HORIZONTAL);
            entryLayout.setPadding(0, 20, 0, 20);
            
            // Type emoji
            TextView typeText = new TextView(this);
            typeText.setText(getCallTypeEmoji(call.getType()));
            typeText.setTextSize(18);
            typeText.setPadding(0, 0, 24, 0);
            entryLayout.addView(typeText);
            
            // Date and time
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
            
            // Duration
            TextView durationText = new TextView(this);
            durationText.setText(formatDuration(call.getDuration()));
            durationText.setTextColor(Color.parseColor("#4FC3F7"));
            durationText.setTextSize(14);
            durationText.setGravity(android.view.Gravity.END);
            entryLayout.addView(durationText);
            
            mainLayout.addView(entryLayout);
            
            // Entry divider
            View entryDivider = new View(this);
            entryDivider.setBackgroundColor(Color.parseColor("#333333"));
            entryDivider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
            mainLayout.addView(entryDivider);
        }
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(mainLayout);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle(contactName);
        builder.setView(scrollView);
        builder.setPositiveButton("Ausblenden", (dialog, which) -> {
            blacklistManager.addNumber(number);
            callLogHelper.setTimePeriod(callLogHelper.getCurrentPeriod());
            updateUI();
            Toast.makeText(this, "✓ " + number + " ausgeblendet", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Schließen", null);
        builder.show();
    }
    
    private String getCallTypeEmoji(int type) {
        switch (type) {
            case CallLogEntry.TYPE_INCOMING: return "📥";
            case CallLogEntry.TYPE_OUTGOING: return "📤";
            case CallLogEntry.TYPE_MISSED: return "❌";
            case CallLogEntry.TYPE_REJECTED: return "🚫";
            default: return "📞";
        }
    }

    private void showBlacklistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle("🚫 Nummern ausblenden");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

        // Show current blacklist
        java.util.Set<String> blacklisted = blacklistManager.getBlacklistedNumbers();
        
        TextView infoText = new TextView(this);
        if (blacklisted.isEmpty()) {
            infoText.setText("Keine Nummern ausgeblendet.");
        } else {
            StringBuilder sb = new StringBuilder("Ausgeblendet:\n");
            for (String num : blacklisted) {
                sb.append("• ").append(num).append("\n");
            }
            infoText.setText(sb.toString().trim());
        }
        infoText.setTextColor(Color.parseColor("#B3B3B3"));
        infoText.setPadding(0, 0, 0, 24);
        layout.addView(infoText);

        // Input field for new number
        EditText input = new EditText(this);
        input.setHint("Nummer eingeben...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#808080"));
        input.setBackgroundColor(Color.parseColor("#3D3D3D"));
        input.setPadding(24, 24, 24, 24);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("Hinzufügen", (dialog, which) -> {
            String number = input.getText().toString().trim();
            if (!number.isEmpty()) {
                blacklistManager.addNumber(number);
                callLogHelper.setTimePeriod(callLogHelper.getCurrentPeriod()); // Re-apply filter
                updateUI();
                Toast.makeText(this, "✓ " + number + " ausgeblendet", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Abbrechen", null);

        if (!blacklisted.isEmpty()) {
            builder.setNeutralButton("Alle löschen", (dialog, which) -> {
                blacklistManager.clear();
                callLogHelper.setTimePeriod(callLogHelper.getCurrentPeriod()); // Re-apply filter
                updateUI();
                Toast.makeText(this, "✓ Filter zurückgesetzt", Toast.LENGTH_SHORT).show();
            });
        }

        builder.show();
    }
}
