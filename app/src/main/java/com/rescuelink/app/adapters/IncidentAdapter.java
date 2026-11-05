package com.rescuelink.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class IncidentAdapter extends RecyclerView.Adapter<IncidentAdapter.VH> {
    private final List<Incident> items = new ArrayList<>();
    private final Consumer<Incident> onClick;

    private Consumer<Incident> onLong;

    public IncidentAdapter(Consumer<Incident> onClick) { this.onClick = onClick; }

    public void setOnLongClick(Consumer<Incident> c) { onLong = c; }

    public void setItems(List<Incident> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_incident, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Incident i = items.get(position);
        boolean draft = i.getStatus() == IncidentStatus.DRAFT;
        h.title.setText(i.displayTitle());
        h.meta.setText((draft ? "Draft" : i.getReference()) + " \u00B7 " + i.getCategory()
                + (i.isSos() ? " \u00B7 SOS" : "") + (draft ? "" : " \u00B7 " + i.getSeverity()));
        String loc = i.getLocation().isEmpty() ? "Location not set" : i.getLocation();
        if (i.hasPosition() && !User.ROLE_CITIZEN.equals(SessionManager.role())) {
            loc += " \u00B7 " + GeoUtils.formatDistance(GeoUtils.distanceFromMeKm(i.getLat(), i.getLon()));
        }
        h.location.setText(loc);
        h.time.setText(UiUtils.formatTime(i.getCreatedAt()));
        UiUtils.styleChip(h.status, i.getStatus());
        int updates = DataRepository.unreadUpdates(SessionManager.getCurrentUser(), i);
        h.fresh.setVisibility(updates > 0 ? View.VISIBLE : View.GONE);
        if (updates > 0) h.fresh.setText("\u25CF New update" + (updates > 1 ? " (" + updates + ")" : "")
                + latestHeadline(i));
        h.itemView.setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {onClick.accept(i);
}
});
        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (onLong == null) return false;
                onLong.accept(i);
                return true;
            }
        });
    }

    /** " \u00B7 Assigned" after the count: what the newest update says, in a word or two. */
    private static String latestHeadline(Incident i) {
        java.util.List<com.rescuelink.app.models.StatusEvent> h = i.getHistory();
        if (h.isEmpty()) return "";
        return " \u00B7 " + h.get(h.size() - 1).getStatus().getLabel();
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, meta, location, time, status, fresh;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_incident_title);
            meta = v.findViewById(R.id.tv_incident_meta);
            location = v.findViewById(R.id.tv_incident_location);
            time = v.findViewById(R.id.tv_incident_time);
            status = v.findViewById(R.id.tv_incident_status);
            fresh = v.findViewById(R.id.tv_incident_new);
        }
    }
}
