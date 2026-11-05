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
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.User;

import org.osmdroid.views.MapView;

/** Fills the mini map and photo shared by the citizen and staff incident screens. */
public final class IncidentViews {
    private IncidentViews() { }

    public static void bind(Activity a, Incident i) {
        bindMap(a, i, false);
        bindResponder(a, i);
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

    /**
     * The responder card: who is in charge, their station and where they are right now. Photo when they have one on
     * this phone, otherwise the first letter of their name.
     */
    public static void bindResponder(Activity a, Incident i) {
        TextView name = a.findViewById(R.id.tv_responder);
        TextView loc = a.findViewById(R.id.tv_responder_loc);
        TextView initial = a.findViewById(R.id.tv_responder_initial);
        ImageView photo = a.findViewById(R.id.iv_responder_photo);
        if (name == null || loc == null || initial == null || photo == null) return;
        String who = i.getResponderName();
        if (who == null) {
            name.setText("Responder: not assigned yet");
            loc.setText("Their name and live location appear here as soon as one accepts.");
            AvatarUtil.bind(initial, photo, "", null);
            return;
        }
        User r = DataRepository.findUserByName(who);
        name.setText(who);
        AvatarUtil.bind(initial, photo, who, r == null ? null : r.getPhotoUri());
        StringBuilder sb = new StringBuilder();
        if (r != null && r.hasPosition()) {
            sb.append("Location: ").append(GeoUtils.formatCoords(r.getLat(), r.getLon()));
            double km = DataRepository.distanceToReport(i, r);
            if (!Double.isNaN(km)) sb.append("\n").append(GeoUtils.formatDistance(km)).append(" from ")
                    .append(DataRepository.referenceLabel(i)).append(", about ").append(GeoUtils.formatEta(km));
            sb.append(isLive(r) ? "\nLive now" : "\nLast known position (not online)");
        } else {
            com.rescuelink.app.models.Station st = i.getAssignedStation() == null ? null
                    : DataRepository.findStation(i.getAssignedStation());
            sb.append("Location not shared yet");
            if (st != null) sb.append("\nStation: ").append(st.getName());
        }
        if (i.getAssignedStation() != null && r != null && r.hasPosition()) {
            sb.append("\nStation: ").append(i.getAssignedStation());
        }
        loc.setText(sb.toString());
    }

    private static boolean isLive(User r) {
        return r == SessionManager.getCurrentUser() || r.isLive();
    }

    /**
     * Live map for a report. Shows the report pin and, once a responder is in charge, their pin moving along a
     * route to the report. Redrawn on every sync, and every few seconds while the screen is open, without
     * resetting the zoom the person chose; it only reframes when the responder leaves the visible area.
     */
    public static void bindMap(final Activity a, final Incident i, boolean forceFit) {
        View card = a.findViewById(R.id.card_map_mini);
        final MapView map = a.findViewById(R.id.map_mini);
        if (card == null || map == null) return;
        final User r = DataRepository.trackedResponder(i);
        final boolean rPos = r != null && r.hasPosition();
        final double[] vp = DataRepository.victimPosition(i);       // the citizen's live spot (or the report pin)
        final boolean vHas = vp != null;
        if (!vHas && !rPos) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        if (map.getTag(R.id.tag_map_init) == null) {
            MapHelper.setup(map, vHas ? vp[0] : r.getLat(), vHas ? vp[1] : r.getLon(), 16.0);
            map.setTag(R.id.tag_map_init, Boolean.TRUE);
        }
        for (org.osmdroid.views.overlay.Overlay o : map.getOverlays()) if (o instanceof RouteAnim) ((RouteAnim) o).stop();
        map.getOverlays().clear();
        MapHelper.addAttribution(map);

        List<org.osmdroid.util.GeoPoint> frame = new java.util.ArrayList<>();
        com.rescuelink.app.models.Station from = MapHelper.stationFor(i);
        if (rPos && vHas) {
            RouteAnim anim = new RouteAnim(map);
            map.getOverlays().add(anim);
            MapHelper.showRoadRoute(anim, r.getLat(), r.getLon(), vp[0], vp[1], MapHelper.COLOR_RESPONDER);
        } else if (!rPos && from != null && i.isActive() && vHas) {
            RouteAnim anim = new RouteAnim(map);
            map.getOverlays().add(anim);
            MapHelper.showRoadRoute(anim, from.getLat(), from.getLon(), vp[0], vp[1], MapHelper.incidentColor(i));
            MapHelper.addMarker(map, from.getLat(), from.getLon(), from.getName(), MapHelper.stationColor(from));
            frame.add(new org.osmdroid.util.GeoPoint(from.getLat(), from.getLon()));
        }
        if (vHas) {
            MapHelper.addMarker(map, vp[0], vp[1], i.displayTitle(), MapHelper.incidentColor(i));
            frame.add(new org.osmdroid.util.GeoPoint(vp[0], vp[1]));
        }
        if (rPos) {
            MapHelper.addAvatarMarker(map, r.getLat(), r.getLon(), r.getFullName() + " (responder)",
                    MapHelper.COLOR_RESPONDER, r);
            frame.add(new org.osmdroid.util.GeoPoint(r.getLat(), r.getLon()));
        }

        // Reframe on the first draw, when something new appears, on Recenter, or when the responder drives out of view.
        String key = (rPos ? "r" : "n") + (vHas ? "i" : "x");
        boolean outOfView = false;
        if (rPos && map.getWidth() > 0) {
            try { outOfView = !map.getBoundingBox().contains(new org.osmdroid.util.GeoPoint(r.getLat(), r.getLon())); }
            catch (RuntimeException ignored) { }
        }
        if (forceFit || outOfView || !key.equals(map.getTag(R.id.tag_map_fit))) {
            map.setTag(R.id.tag_map_fit, key);
            MapHelper.fitPoints(map, frame, false);
        }
        map.invalidate();

        TextView title = a.findViewById(R.id.tv_track_title);
        TextView liveTag = a.findViewById(R.id.tv_track_live);
        TextView info = a.findViewById(R.id.tv_track_info);
        if (title != null) title.setText(rPos ? "Live responder tracking" : "Report location");
        if (liveTag != null) {
            if (rPos) {
                liveTag.setVisibility(View.VISIBLE);
                if (isLive(r)) UiUtils.styleBadge(liveTag, "\u25CF Live", UiUtils.GREEN_FG, UiUtils.GREEN_BG);
                else UiUtils.styleBadge(liveTag, "\u25CF Last known", UiUtils.GRAY_FG, UiUtils.GRAY_BG);
            } else {
                liveTag.setVisibility(View.GONE);
            }
        }
        if (info != null) {
            String text;
            if (rPos) {
                text = r.getFullName();
                User viewer = com.rescuelink.app.utils.SessionManager.getCurrentUser();
                boolean viewerIsResponder = viewer != null && User.ROLE_RESPONDER.equals(viewer.getRole())
                        && viewer.getFullName().equals(i.getResponderName());
                User victim = DataRepository.findUser(i.getOwnerUsername());
                String who = victim == null ? "the citizen" : victim.getFullName();
                if (vHas) {
                    double km = GeoUtils.distanceKm(vp[0], vp[1], r.getLat(), r.getLon());
                    boolean here = i.getStatus() == IncidentStatus.ON_SCENE || km < 0.03;
                    if (viewerIsResponder) {
                        text = here ? "You have arrived at " + who
                                : GeoUtils.formatDistance(km) + " left to reach " + who + " \u00B7 about " + GeoUtils.formatEta(km);
                    } else {
                        text = here ? r.getFullName() + " has arrived"
                                : r.getFullName() + " is " + GeoUtils.formatDistance(km) + " away \u00B7 arriving in about " + GeoUtils.formatEta(km);
                    }
                } else {
                    text = viewerIsResponder ? "Waiting for " + who + "'s live location\u2026"
                            : r.getFullName() + " is on the way";
                }
            } else if (r != null) {
                text = r.getFullName() + " has not shared a location yet.";
            } else {
                text = i.isActive() ? "No responder has accepted yet. Their pin shows here once they do." : "";
            }
            info.setText(text);
            info.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
        View recenter = a.findViewById(R.id.btn_track_center);
        if (recenter != null) {
            recenter.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { bindMap(a, i, true); }
            });
        }
        View dir = a.findViewById(R.id.btn_directions);
        View share = a.findViewById(R.id.btn_share_incident);
        int vis = vHas ? View.VISIBLE : View.GONE;
        if (dir != null) {
            dir.setVisibility(vis);
            if (vHas) dir.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { MapHelper.navigateTo(a, vp[0], vp[1], i.displayTitle()); }
            });
        }
        if (share != null) {
            share.setVisibility(vis);
            if (vHas) share.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    MapHelper.shareText(a, i.getReference(), i.displayTitle() + "\n" + i.getLocation()
                            + "\n" + MapHelper.locationLink(vp[0], vp[1]));
                }
            });
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
