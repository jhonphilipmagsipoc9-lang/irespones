package com.rescuelink.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.models.IncidentMessage;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Chat bubbles for a report's conversation: mine on the right, everyone else's on the left with their name. */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
    private final List<IncidentMessage> items = new ArrayList<>();
    private String me = "";

    public void setItems(List<IncidentMessage> list, String myUsername) {
        items.clear();
        items.addAll(list);
        me = myUsername == null ? "" : myUsername;
        notifyDataSetChanged();
    }

    private boolean mine(IncidentMessage m) { return m.getSenderUsername().equalsIgnoreCase(me); }

    @Override
    public int getItemViewType(int position) { return mine(items.get(position)) ? 1 : 0; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.item_message_out : R.layout.item_message_in;
        return new VH(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        IncidentMessage m = items.get(position);
        h.msg.setText(m.getText());
        h.time.setText(UiUtils.formatTime(m.getTimeMillis()));
        if (h.sender != null) h.sender.setText(m.getSenderName() + " · " + m.getSenderRole());
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView msg, time, sender;

        VH(@NonNull View v) {
            super(v);
            msg = v.findViewById(R.id.tv_msg);
            time = v.findViewById(R.id.tv_time);
            sender = v.findViewById(R.id.tv_sender); // only present on incoming messages
        }
    }
}
