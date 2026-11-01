package com.rescuelink.app.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StationAdapter extends RecyclerView.Adapter<StationAdapter.VH> {
    private final List<Station> items = new ArrayList<>();
    private final Consumer<Station> onClick;

    public StationAdapter(Consumer<Station> onClick) { this.onClick = onClick; }

    public void setItems(List<Station> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_station, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Station s = items.get(position);
        h.name.setText(s.getName());
        h.meta.setText(s.getCategory() + " \u00B7 " + s.getAddress());
        double km = GeoUtils.distanceFromMeKm(s.getLat(), s.getLon());
        h.distance.setText(GeoUtils.formatDistance(km) + " away \u00B7 about " + GeoUtils.formatEta(km));
        if (s.isAvailable()) {
            UiUtils.styleBadge(h.badge, "Available", Color.parseColor("#4E8F6A"), Color.parseColor("#DDEFE5"));
        } else {
            UiUtils.styleBadge(h.badge, "Unavailable", Color.parseColor("#7B8794"), Color.parseColor("#EEF1F5"));
        }
        h.itemView.setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {onClick.accept(s);
}
});
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, meta, distance, badge;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tv_station_name);
            meta = v.findViewById(R.id.tv_station_meta);
            distance = v.findViewById(R.id.tv_station_distance);
            badge = v.findViewById(R.id.tv_station_badge);
        }
    }
}
