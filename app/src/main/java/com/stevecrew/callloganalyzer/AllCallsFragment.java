package com.stevecrew.callloganalyzer;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment das alle Anrufe in einer scrollbaren Liste anzeigt.
 * 
 * Features:
 * - RecyclerView für effizientes Scrollen auch bei vielen Anrufen
 * - Intelligente Datumsformatierung ("Heute", "Gestern", oder Datum)
 * - Farbige Typ-Indikatoren (grün/blau/orange/rot)
 * - Tap auf Eintrag zeigt Detail-Dialog
 * 
 * Wird als zweiter Tab in MainActivity angezeigt.
 */
public class AllCallsFragment extends Fragment {

    private RecyclerView recyclerView;
    private CallAdapter adapter;
    private TextView tvEmpty;  // Wird angezeigt wenn keine Anrufe vorhanden

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_calls, container, false);
        
        // UI-Elemente verbinden
        recyclerView = view.findViewById(R.id.recyclerViewCalls);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        
        // RecyclerView konfigurieren
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CallAdapter();
        recyclerView.setAdapter(adapter);
        
        return view;
    }

    /**
     * Wird aufgerufen wenn Fragment sichtbar wird.
     * Aktualisiert die Liste mit neuesten Daten.
     */
    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    /**
     * Aktualisiert die Anrufliste.
     * Wird aufgerufen bei:
     * - Fragment wird sichtbar (onResume)
     * - Zeitfilter wird geändert
     * - Neuer Anruf kommt rein (via ContentObserver)
     */
    public void updateUI() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null || activity.getCallLogHelper() == null) return;
        
        List<CallLogEntry> calls = activity.getCallLogHelper().getAllCalls();
        
        // Leere Liste → Hinweis anzeigen, RecyclerView verstecken
        if (calls.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setCalls(calls);
        }
    }

    /**
     * RecyclerView Adapter für die Anrufliste.
     * 
     * RecyclerView recycelt View-Elemente für bessere Performance:
     * - Nur sichtbare Items werden im Speicher gehalten
     * - Beim Scrollen werden Views wiederverwendet
     * - Wichtig bei Listen mit vielen Einträgen (1000+ Anrufe)
     */
    private class CallAdapter extends RecyclerView.Adapter<CallAdapter.CallViewHolder> {
        
        private List<CallLogEntry> calls = new ArrayList<>();
        
        // DateFormatter werden einmal erstellt (Performance)
        private final SimpleDateFormat sdfDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        private final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());

        /**
         * Setzt neue Anrufdaten und aktualisiert die Liste.
         * notifyDataSetChanged() informiert RecyclerView über Änderung.
         */
        public void setCalls(List<CallLogEntry> calls) {
            this.calls = calls;
            notifyDataSetChanged();
        }

        /**
         * Erstellt einen neuen ViewHolder für ein Listen-Item.
         * Wird nur aufgerufen wenn RecyclerView neue Views braucht.
         */
        @NonNull
        @Override
        public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_call, parent, false);
            return new CallViewHolder(view);
        }

        /**
         * Befüllt einen ViewHolder mit Daten für eine bestimmte Position.
         * Wird bei jedem Scrollen aufgerufen → sollte effizient sein!
         */
        @Override
        public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
            CallLogEntry call = calls.get(position);
            MainActivity activity = (MainActivity) getActivity();
            
            // === Name oder Nummer anzeigen ===
            String name = call.getDisplayName();
            if (activity != null) {
                // Versuche Kontaktnamen zu holen (könnte aktueller sein)
                String contactName = activity.getCallLogHelper().getContactNameForNumber(call.getNumber());
                if (!contactName.equals(call.getNumber())) {
                    name = contactName;
                }
            }
            holder.tvName.setText(name);
            
            // Nummer nur anzeigen wenn Name vorhanden (sonst redundant)
            if (!name.equals(call.getNumber())) {
                holder.tvNumber.setText(call.getNumber());
                holder.tvNumber.setVisibility(View.VISIBLE);
            } else {
                holder.tvNumber.setVisibility(View.GONE);
            }
            
            // === Anruftyp visualisieren ===
            holder.tvType.setText(getCallTypeEmoji(call.getType()));
            holder.viewTypeIndicator.setBackgroundColor(getCallTypeColor(call.getType()));
            
            // === Datum smart formatieren ===
            Date date = new Date(call.getTimestamp());
            Calendar callCal = Calendar.getInstance();
            callCal.setTime(date);
            Calendar todayCal = Calendar.getInstance();
            
            // "Heute" / "Gestern" statt Datum wenn passend
            if (isSameDay(callCal, todayCal)) {
                holder.tvDate.setText("Heute");
            } else {
                todayCal.add(Calendar.DAY_OF_YEAR, -1);
                if (isSameDay(callCal, todayCal)) {
                    holder.tvDate.setText("Gestern");
                } else {
                    holder.tvDate.setText(sdfDate.format(date));
                }
            }
            holder.tvTime.setText(" " + sdfTime.format(date));
            
            // === Dauer anzeigen ===
            holder.tvDuration.setText(formatDuration(call.getDuration()));
            
            // === Click-Handler für Detail-Ansicht ===
            holder.itemView.setOnClickListener(v -> {
                if (activity != null) {
                    activity.showCallDetailsForNumber(call.getNumber(), "calls");
                }
            });
        }
        
        /**
         * Prüft ob zwei Calendar-Objekte den gleichen Tag repräsentieren.
         */
        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }
        
        /**
         * Gibt die Farbe für einen Anruftyp zurück.
         * Verwendet für den seitlichen Farbbalken im Listen-Item.
         */
        private int getCallTypeColor(int type) {
            switch (type) {
                case CallLogEntry.TYPE_INCOMING: return Color.parseColor("#2E7D32"); // Grün
                case CallLogEntry.TYPE_OUTGOING: return Color.parseColor("#1565C0"); // Blau
                case CallLogEntry.TYPE_MISSED: return Color.parseColor("#EF6C00");   // Orange
                case CallLogEntry.TYPE_REJECTED: return Color.parseColor("#C62828"); // Rot
                default: return Color.parseColor("#808080");                          // Grau
            }
        }

        @Override
        public int getItemCount() {
            return calls.size();
        }

        /**
         * ViewHolder hält Referenzen auf die Views eines Listen-Items.
         * Vermeidet wiederholte findViewById()-Aufrufe (Performance).
         */
        class CallViewHolder extends RecyclerView.ViewHolder {
            View viewTypeIndicator;  // Farbiger Balken links
            TextView tvType;         // Emoji (📥/📤/❌/🚫)
            TextView tvName;         // Kontaktname oder Nummer
            TextView tvNumber;       // Nummer (wenn Name vorhanden)
            TextView tvDate;         // Datum oder "Heute"/"Gestern"
            TextView tvTime;         // Uhrzeit
            TextView tvDuration;     // Anrufdauer

            CallViewHolder(@NonNull View itemView) {
                super(itemView);
                viewTypeIndicator = itemView.findViewById(R.id.viewTypeIndicator);
                tvType = itemView.findViewById(R.id.tvType);
                tvName = itemView.findViewById(R.id.tvName);
                tvNumber = itemView.findViewById(R.id.tvNumber);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvDuration = itemView.findViewById(R.id.tvDuration);
            }
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
                default: return "📞";                          // Unbekannt
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
}
