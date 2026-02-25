package com.stevecrew.callloganalyzer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView tvIncoming, tvOutgoing, tvMissed, tvRejected;
    private TextView tvTopCallers, tvTopDuration, tvStatus, tvTotalCalls;
    private Button btnExport;
    private PieChart pieChart;
    
    private CallLogHelper callLogHelper;

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
        pieChart = findViewById(R.id.pieChart);

        setupPieChart();
        
        callLogHelper = new CallLogHelper(this);

        btnExport.setOnClickListener(v -> exportData());

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

        // Top 10 Callers with visual bars
        List<Map.Entry<String, Integer>> topCallers = callLogHelper.getTopCallers(10);
        StringBuilder callerSb = new StringBuilder();
        int maxCalls = topCallers.isEmpty() ? 1 : topCallers.get(0).getValue();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : topCallers) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            if (name.length() > 20) name = name.substring(0, 17) + "...";
            int calls = entry.getValue();
            String bar = generateBar(calls, maxCalls, 10);
            callerSb.append(String.format(Locale.getDefault(), "%2d. %-20s %s %d\n", 
                    rank, name, bar, calls));
            rank++;
        }
        tvTopCallers.setText(callerSb.toString().trim());

        // Top 10 Duration with visual bars
        List<Map.Entry<String, Long>> topDuration = callLogHelper.getTopDuration(10);
        StringBuilder durationSb = new StringBuilder();
        long maxDuration = topDuration.isEmpty() ? 1 : topDuration.get(0).getValue();
        rank = 1;
        for (Map.Entry<String, Long> entry : topDuration) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            if (name.length() > 20) name = name.substring(0, 17) + "...";
            long duration = entry.getValue();
            String bar = generateBar((int)duration, (int)maxDuration, 8);
            String formattedDuration = formatDuration(duration);
            durationSb.append(String.format(Locale.getDefault(), "%2d. %-20s %s %s\n", 
                    rank, name, bar, formattedDuration));
            rank++;
        }
        tvTopDuration.setText(durationSb.toString().trim());

        tvStatus.setText("✓ Last updated just now");
    }

    private String formatNumber(int num) {
        if (num >= 1000) {
            return String.format(Locale.getDefault(), "%.1fk", num / 1000.0);
        }
        return String.valueOf(num);
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
}
