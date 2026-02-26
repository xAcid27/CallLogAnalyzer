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

public class OverviewFragment extends Fragment {

    private TextView tvIncoming, tvOutgoing, tvMissed, tvRejected;
    private TextView tvTopCallers, tvTopDuration, tvStatus, tvTotalCalls;
    private Button btnExport, btnBlacklist;
    private PieChart pieChart;
    private Spinner spinnerTimePeriod;

    private final int COLOR_GREEN = Color.parseColor("#2E7D32");
    private final int COLOR_BLUE = Color.parseColor("#1565C0");
    private final int COLOR_ORANGE = Color.parseColor("#EF6C00");
    private final int COLOR_RED = Color.parseColor("#C62828");

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

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

        setupPieChart();
        setupTimePeriodSpinner();

        btnExport.setOnClickListener(v -> exportData());
        btnBlacklist.setOnClickListener(v -> showBlacklistDialog());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
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
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
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
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null && activity.getCallLogHelper() != null) {
                    activity.getCallLogHelper().setTimePeriod(position);
                    updateUI();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    public void updateUI() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        
        CallLogHelper callLogHelper = activity.getCallLogHelper();
        if (callLogHelper == null) return;

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

        updatePieChart(incoming, outgoing, missed, rejected);

        // Top Callers
        lastTopCallers = callLogHelper.getTopCallers(10);
        StringBuilder callerSb = new StringBuilder();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : lastTopCallers) {
            String name = callLogHelper.getContactNameForNumber(entry.getKey());
            if (name.length() > 16) name = name.substring(0, 13) + "...";
            callerSb.append(String.format(Locale.getDefault(), "%s %s  ·  %d\n",
                    getRankPrefix(rank), name, entry.getValue()));
            rank++;
        }
        tvTopCallers.setText(callerSb.toString().trim());
        tvTopCallers.setOnClickListener(v -> showTopCallersDetail());

        // Top Duration
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

        tvStatus.setText("✓ Last updated just now");
    }

    private void updatePieChart(int incoming, int outgoing, int missed, int rejected) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        if (incoming > 0) { entries.add(new PieEntry(incoming, "Incoming")); colors.add(COLOR_GREEN); }
        if (outgoing > 0) { entries.add(new PieEntry(outgoing, "Outgoing")); colors.add(COLOR_BLUE); }
        if (missed > 0) { entries.add(new PieEntry(missed, "Missed")); colors.add(COLOR_ORANGE); }
        if (rejected > 0) { entries.add(new PieEntry(rejected, "Rejected")); colors.add(COLOR_RED); }

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

    private String formatNumber(int num) {
        if (num >= 1000) return String.format(Locale.getDefault(), "%.1fk", num / 1000.0);
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

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        else if (minutes > 0) return String.format(Locale.getDefault(), "%dm %ds", minutes, secs);
        return String.format(Locale.getDefault(), "%ds", secs);
    }

    private void showTopCallersDetail() {
        if (lastTopCallers == null || lastTopCallers.isEmpty()) return;
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        String[] items = new String[lastTopCallers.size()];
        for (int i = 0; i < lastTopCallers.size(); i++) {
            Map.Entry<String, Integer> entry = lastTopCallers.get(i);
            String name = activity.getCallLogHelper().getContactNameForNumber(entry.getKey());
            items[i] = getRankPrefix(i + 1) + " " + name + " (" + entry.getValue() + " Anrufe)";
        }

        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📞 Top Callers")
            .setItems(items, (dialog, which) -> {
                String number = lastTopCallers.get(which).getKey();
                activity.showCallDetailsForNumber(number, "calls");
            })
            .setNegativeButton("Schließen", null)
            .show();
    }

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
                activity.showCallDetailsForNumber(number, "duration");
            })
            .setNegativeButton("Schließen", null)
            .show();
    }

    private void exportData() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        
        if (activity.getCallLogHelper().getAllCalls().isEmpty()) {
            Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        String path = CSVExporter.exportToCSV(requireContext(), activity.getCallLogHelper().getAllCalls());
        if (path != null) {
            Toast.makeText(requireContext(), "✓ Exported to Downloads", Toast.LENGTH_LONG).show();
            tvStatus.setText("✓ Exported to " + path);
        } else {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBlacklistDialog() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        
        BlacklistManager blacklistManager = activity.getBlacklistManager();

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

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

        EditText input = new EditText(requireContext());
        input.setHint("Nummer eingeben...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#808080"));
        input.setBackgroundColor(Color.parseColor("#3D3D3D"));
        input.setPadding(24, 24, 24, 24);
        layout.addView(input);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("🚫 Nummern ausblenden")
            .setView(layout)
            .setPositiveButton("Hinzufügen", (dialog, which) -> {
                String number = input.getText().toString().trim();
                if (!number.isEmpty()) {
                    blacklistManager.addNumber(number);
                    activity.getCallLogHelper().setTimePeriod(activity.getCallLogHelper().getCurrentPeriod());
                    updateUI();
                    Toast.makeText(requireContext(), "✓ " + number + " ausgeblendet", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Abbrechen", null);

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
