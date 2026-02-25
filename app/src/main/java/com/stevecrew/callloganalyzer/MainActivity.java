package com.stevecrew.callloganalyzer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView tvIncoming, tvOutgoing, tvMissed, tvRejected;
    private TextView tvTopCallers, tvTopDuration, tvStatus;
    private Button btnExport;
    
    private CallLogHelper callLogHelper;

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
        btnExport = findViewById(R.id.btnExport);

        callLogHelper = new CallLogHelper(this);

        btnExport.setOnClickListener(v -> exportData());

        // Check permissions
        if (checkPermission()) {
            loadData();
        } else {
            requestPermission();
        }
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
        tvIncoming.setText(String.valueOf(callLogHelper.getIncomingCount()));
        tvOutgoing.setText(String.valueOf(callLogHelper.getOutgoingCount()));
        tvMissed.setText(String.valueOf(callLogHelper.getMissedCount()));
        tvRejected.setText(String.valueOf(callLogHelper.getRejectedCount()));

        // Top 10 Callers
        List<Map.Entry<String, Integer>> topCallers = callLogHelper.getTopCallers(10);
        StringBuilder callerSb = new StringBuilder();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : topCallers) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            callerSb.append(rank).append(". ")
                    .append(name)
                    .append(" (").append(entry.getValue()).append(" calls)\n");
            rank++;
        }
        tvTopCallers.setText(callerSb.toString().trim());

        // Top 10 Duration
        List<Map.Entry<String, Long>> topDuration = callLogHelper.getTopDuration(10);
        StringBuilder durationSb = new StringBuilder();
        rank = 1;
        for (Map.Entry<String, Long> entry : topDuration) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            String formattedDuration = formatDuration(entry.getValue());
            durationSb.append(rank).append(". ")
                    .append(name)
                    .append(" (").append(formattedDuration).append(")\n");
            rank++;
        }
        tvTopDuration.setText(durationSb.toString().trim());

        int totalCalls = callLogHelper.getAllCalls().size();
        tvStatus.setText("✅ Loaded " + totalCalls + " calls");
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, secs);
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
            Toast.makeText(this, "Exported to: " + path, Toast.LENGTH_LONG).show();
            tvStatus.setText("📁 Exported to " + path);
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
            tvStatus.setText("❌ Export failed");
        }
    }
}
