package com.rescuelink.app.utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.StatusEvent;
import java.util.List;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import com.rescuelink.app.R;
import com.rescuelink.app.models.Incident;

import org.osmdroid.views.MapView;

/** Fills the mini map and photo shared by the citizen and staff incident screens. */
public final class IncidentViews {
    private IncidentViews() { }

    public static void bind(Activity a, Incident i) {
        View card = a.findViewById(R.id.card_map_mini);
        MapView map = a.findViewById(R.id.map_mini);
        if (i.hasPosition()) {
            card.setVisibility(View.VISIBLE);
            MapHelper.setup(map, i.getLat(), i.getLon(), 16.0);
            for (org.osmdroid.views.overlay.Overlay o : map.getOverlays()) if (o instanceof RouteAnim) ((RouteAnim) o).stop();
            map.getOverlays().clear();
            MapHelper.addAttribution(map);
            com.rescuelink.app.models.Station from = MapHelper.stationFor(i);
            if (from != null && i.isActive()) {
                RouteAnim anim = new RouteAnim(map);
                map.getOverlays().add(anim);
                java.util.List<RouteAnim.Route> rs = new java.util.ArrayList<>();
                rs.add(new RouteAnim.Route(new org.osmdroid.util.GeoPoint(from.getLat(), from.getLon()),
                        new org.osmdroid.util.GeoPoint(i.getLat(), i.getLon()), MapHelper.incidentColor(i)));
                anim.setRoutes(rs);
                MapHelper.addMarker(map, from.getLat(), from.getLon(), from.getName(), MapHelper.stationColor(from));
            }
            MapHelper.addMarker(map, i.getLat(), i.getLon(), i.displayTitle(), MapHelper.incidentColor(i));
            if (from != null && i.isActive()) {
                final org.osmdroid.views.MapView fm = map;
                final java.util.List<org.osmdroid.util.GeoPoint> bb = new java.util.ArrayList<>();
                bb.add(new org.osmdroid.util.GeoPoint(from.getLat(), from.getLon()));
                bb.add(new org.osmdroid.util.GeoPoint(i.getLat(), i.getLon()));
                MapHelper.fitPoints(fm, bb, false);
            }
            map.invalidate();
            a.findViewById(R.id.btn_directions).setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    MapHelper.navigateTo(a, i.getLat(), i.getLon(), i.displayTitle());
                }
            });
            a.findViewById(R.id.btn_share_incident).setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    MapHelper.shareText(a, i.getReference(), i.displayTitle() + "\n" + i.getLocation()
                            + "\n" + MapHelper.locationLink(i.getLat(), i.getLon()));
                }
            });
        } else {
            card.setVisibility(View.GONE);
        }
        ImageView photo = a.findViewById(R.id.iv_incident_photo);
        if (i.getPhotoUri() != null) {
            try {
                photo.setImageURI(Uri.parse(i.getPhotoUri()));
                photo.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                photo.setVisibility(View.GONE);
            }
        } else {
            photo.setVisibility(View.GONE);
        }
    }

    public static void detach(Activity a) {
        MapView map = a.findViewById(R.id.map_mini);
        if (map != null) map.onDetach();
    }

    public static void buildTimeline(Activity a, Incident i) {
        LinearLayout container = a.findViewById(R.id.timeline_container);
        container.removeAllViews();
        for (IncidentStatus s : IncidentStatus.FLOW) {
            StatusEvent e = i.findEvent(s);
            addRow(a, container, s.getLabel(), e == null ? "Pending" : UiUtils.formatTime(e.getTimeMillis()),
                    e == null ? Color.parseColor("#D5DCE4") : s.getTextColor());
        }
        StatusEvent c = i.findEvent(IncidentStatus.CANCELLED);
        if (c != null) {
            addRow(a, container, "Cancelled", UiUtils.formatTime(c.getTimeMillis()),
                    IncidentStatus.CANCELLED.getTextColor());
        }
    }

    private static void addRow(Activity a, LinearLayout container, String label, String time, int color) {
        View row = a.getLayoutInflater().inflate(R.layout.item_timeline_step, container, false);
        ((TextView) row.findViewById(R.id.tv_dot)).setTextColor(color);
        ((TextView) row.findViewById(R.id.tv_step_label)).setText(label);
        ((TextView) row.findViewById(R.id.tv_step_time)).setText(time);
        container.addView(row);
    }

    public static void buildHistory(Activity a, Incident i) {
        LinearLayout container = a.findViewById(R.id.history_container);
        container.removeAllViews();
        List<StatusEvent> events = i.getHistory();
        for (int k = events.size() - 1; k >= 0; k--) {
            StatusEvent e = events.get(k);
            TextView tv = new TextView(a);
            tv.setText(e.getStatus().getLabel() + " \u00B7 " + UiUtils.formatTime(e.getTimeMillis()) + "\n" + e.getNote());
            tv.setTextColor(a.getColor(R.color.text_main));
            tv.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UiUtils.dp(a, 10);
            tv.setLayoutParams(lp);
            container.addView(tv);
        }
    }
}
