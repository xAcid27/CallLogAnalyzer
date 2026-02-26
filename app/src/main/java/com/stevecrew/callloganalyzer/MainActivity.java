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

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private CallLogHelper callLogHelper;
    private BlacklistManager blacklistManager;
    
    private OverviewFragment overviewFragment;
    private AllCallsFragment allCallsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        blacklistManager = new BlacklistManager(this);
        callLogHelper = new CallLogHelper(this);
        callLogHelper.setBlacklistManager(blacklistManager);

        overviewFragment = new OverviewFragment();
        allCallsFragment = new AllCallsFragment();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_overview) {
                fragment = overviewFragment;
            } else if (itemId == R.id.nav_all_calls) {
                fragment = allCallsFragment;
            }
            
            if (fragment != null) {
                getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            }
            return true;
        });

        // Check permissions
        if (checkPermission()) {
            loadData();
            // Show overview by default
            bottomNav.setSelectedItemId(R.id.nav_overview);
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
                BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
                bottomNav.setSelectedItemId(R.id.nav_overview);
            } else {
                Toast.makeText(this, "Permission denied. Cannot read call log.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadData() {
        callLogHelper.loadCallLog();
    }

    public CallLogHelper getCallLogHelper() {
        return callLogHelper;
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    public void showCallDetailsForNumber(String number, String sortBy) {
        String contactName = callLogHelper.getContactNameForNumber(number);
        List<CallLogEntry> calls = new ArrayList<>();

        for (CallLogEntry entry : callLogHelper.getAllCalls()) {
            if (entry.getNumber().equals(number)) {
                calls.add(entry);
            }
        }

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

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 24, 48, 24);

        TextView headerText = new TextView(this);
        headerText.setText("📱  " + number);
        headerText.setTextColor(Color.parseColor("#B3B3B3"));
        headerText.setTextSize(13);
        mainLayout.addView(headerText);

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

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(Color.parseColor("#404040"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2));
        mainLayout.addView(divider);

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

            TextView typeText = new TextView(this);
            typeText.setText(getCallTypeEmoji(call.getType()));
            typeText.setTextSize(18);
            typeText.setPadding(0, 0, 24, 0);
            entryLayout.addView(typeText);

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

            TextView durationText = new TextView(this);
            durationText.setText(formatDuration(call.getDuration()));
            durationText.setTextColor(Color.parseColor("#4FC3F7"));
            durationText.setTextSize(14);
            durationText.setGravity(android.view.Gravity.END);
            entryLayout.addView(durationText);

            mainLayout.addView(entryLayout);

            android.view.View entryDivider = new android.view.View(this);
            entryDivider.setBackgroundColor(Color.parseColor("#333333"));
            entryDivider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
            mainLayout.addView(entryDivider);
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(mainLayout);

        new AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle(contactName)
            .setView(scrollView)
            .setPositiveButton("Ausblenden", (dialog, which) -> {
                blacklistManager.addNumber(number);
                callLogHelper.setTimePeriod(callLogHelper.getCurrentPeriod());
                if (overviewFragment != null) overviewFragment.updateUI();
                if (allCallsFragment != null) allCallsFragment.updateUI();
                Toast.makeText(this, "✓ " + number + " ausgeblendet", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Schließen", null)
            .show();
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

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        else if (minutes > 0) return String.format(Locale.getDefault(), "%dm %ds", minutes, secs);
        return String.format(Locale.getDefault(), "%ds", secs);
    }
}
