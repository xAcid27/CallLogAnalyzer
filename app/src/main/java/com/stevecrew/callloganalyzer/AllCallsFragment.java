package com.stevecrew.callloganalyzer;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AllCallsFragment extends Fragment {

    private RecyclerView recyclerView;
    private CallAdapter adapter;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_calls, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerViewCalls);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CallAdapter();
        recyclerView.setAdapter(adapter);
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    public void updateUI() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null || activity.getCallLogHelper() == null) return;
        
        List<CallLogEntry> calls = activity.getCallLogHelper().getAllCalls();
        
        if (calls.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setCalls(calls);
        }
    }

    private class CallAdapter extends RecyclerView.Adapter<CallAdapter.CallViewHolder> {
        
        private List<CallLogEntry> calls = new ArrayList<>();
        private final SimpleDateFormat sdfDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        private final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());

        public void setCalls(List<CallLogEntry> calls) {
            this.calls = calls;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_call, parent, false);
            return new CallViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
            CallLogEntry call = calls.get(position);
            MainActivity activity = (MainActivity) getActivity();
            
            // Name or number
            String name = call.getDisplayName();
            if (activity != null) {
                String contactName = activity.getCallLogHelper().getContactNameForNumber(call.getNumber());
                if (!contactName.equals(call.getNumber())) {
                    name = contactName;
                }
            }
            holder.tvName.setText(name);
            
            // Number (if different from name)
            if (!name.equals(call.getNumber())) {
                holder.tvNumber.setText(call.getNumber());
                holder.tvNumber.setVisibility(View.VISIBLE);
            } else {
                holder.tvNumber.setVisibility(View.GONE);
            }
            
            // Type emoji
            holder.tvType.setText(getCallTypeEmoji(call.getType()));
            
            // Date and time
            Date date = new Date(call.getTimestamp());
            holder.tvDate.setText(sdfDate.format(date));
            holder.tvTime.setText(sdfTime.format(date));
            
            // Duration
            holder.tvDuration.setText(formatDuration(call.getDuration()));
            
            // Click to show details
            holder.itemView.setOnClickListener(v -> {
                if (activity != null) {
                    activity.showCallDetailsForNumber(call.getNumber(), "calls");
                }
            });
        }

        @Override
        public int getItemCount() {
            return calls.size();
        }

        class CallViewHolder extends RecyclerView.ViewHolder {
            TextView tvType, tvName, tvNumber, tvDate, tvTime, tvDuration;

            CallViewHolder(@NonNull View itemView) {
                super(itemView);
                tvType = itemView.findViewById(R.id.tvType);
                tvName = itemView.findViewById(R.id.tvName);
                tvNumber = itemView.findViewById(R.id.tvNumber);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvDuration = itemView.findViewById(R.id.tvDuration);
            }
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
}
