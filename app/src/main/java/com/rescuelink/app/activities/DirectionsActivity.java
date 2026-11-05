package com.rescuelink.app.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuelink.app.R;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.MapHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Directions shown on the app's own OpenStreetMap view. No external map app and no Google service is used.
 * The road route comes from the free OpenStreetMap-based OSRM service; without internet a straight line is drawn.
 */
public class DirectionsActivity extends AppCompatActivity {
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    public static final String EXTRA_LABEL = "label";

    private MapView map;
    private com.rescuelink.app.utils.RouteAnim routeAnim;
    private TextView tvMain, tvSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_directions);
        map = findViewById(R.id.map);
        tvMain = findViewById(R.id.tv_route_main);
        tvSub = findViewById(R.id.tv_route_sub);

        final double lat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
        final double lon = getIntent().getDoubleExtra(EXTRA_LON, Double.NaN);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        if (Double.isNaN(lat) || Double.isNaN(lon)) { finish(); return; }
        if (label == null) label = "Destination";
        ((TextView) findViewById(R.id.tv_dest)).setText("To " + label);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        LocationHelper.refresh(this);
        final double fromLat = GeoUtils.myLat();
        final double fromLon = GeoUtils.myLon();
        MapHelper.setup(map, lat, lon, 15.0);
        MapHelper.addMarker(map, lat, lon, label, MapHelper.COLOR_CRITICAL);
        MapHelper.addMarker(map, fromLat, fromLon, "You", MapHelper.COLOR_PICK);

        double km = GeoUtils.distanceKm(fromLat, fromLon, lat, lon);
        if (km < 0.03) {   // you are standing at the destination: nothing to route
            tvMain.setText("You are at " + label);
            tvSub.setText(GeoUtils.hasFix() ? "This place is within 30 m of your location."
                    : "Your location is not known yet, so the distance cannot be shown.");
            zoomTo(fromLat, fromLon, lat, lon);
            return;
        }
        tvMain.setText(GeoUtils.formatDistance(km) + " · about " + GeoUtils.formatEta(km));
        tvSub.setText((GeoUtils.hasFix() ? "From your location. " : "Your location is not known yet, showing the area start point. ")
                + "Finding the road route...");
        zoomTo(fromLat, fromLon, lat, lon);

        final String dest = label;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<GeoPoint> pts = new ArrayList<>();
                double dist = -1, dur = -1;
                try {
                    String url = String.format(Locale.US,
                            "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                            fromLon, fromLat, lon, lat);
                    JSONObject root = new JSONObject(MapHelper.httpGet(url));
                    JSONObject route = root.getJSONArray("routes").getJSONObject(0);
                    dist = route.getDouble("distance");
                    dur = route.getDouble("duration");
                    JSONArray c = route.getJSONObject("geometry").getJSONArray("coordinates");
                    for (int i = 0; i < c.length(); i++) {
                        JSONArray p = c.getJSONArray(i);
                        pts.add(new GeoPoint(p.getDouble(1), p.getDouble(0)));
                    }
                } catch (Exception e) {
                    pts.clear();
                }
                final double fd = dist, fdur = dur;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        showRoute(pts, fd, fdur, fromLat, fromLon, lat, lon, dest);
                    }
                });
            }
        }).start();
    }

    private void showRoute(List<GeoPoint> pts, double distM, double durS,
                           double fromLat, double fromLon, double lat, double lon, String dest) {
        boolean road = pts.size() >= 2;
        if (!road) {
            pts.add(new GeoPoint(fromLat, fromLon));
            pts.add(new GeoPoint(lat, lon));
        }
        Polyline line = new Polyline(map);
        line.setPoints(pts);
        line.getOutlinePaint().setColor(road ? Color.parseColor("#2563EB") : Color.parseColor("#9AA5B1"));
        line.getOutlinePaint().setStrokeWidth(road ? 14f : 8f);
        map.getOverlays().add(0, line);
        routeAnim = new com.rescuelink.app.utils.RouteAnim(map);
        map.getOverlays().add(1, routeAnim);
        java.util.List<com.rescuelink.app.utils.RouteAnim.Route> rs = new ArrayList<>();
        rs.add(new com.rescuelink.app.utils.RouteAnim.Route(new ArrayList<>(pts), Color.parseColor("#2563EB")));
        routeAnim.setRoutes(rs);
        if (road) {
            double km = distM / 1000.0;
            int min = Math.max(1, (int) Math.round(durS / 60.0));
            tvMain.setText(GeoUtils.formatDistance(km) + " · about " + (min < 60 ? min + " min" : (min / 60) + " h " + (min % 60) + " min"));
            tvSub.setText("Driving route to " + dest + " on OpenStreetMap roads.");
        } else {
            tvSub.setText("Road route unavailable (no internet). The grey line is a straight line to " + dest + ".");
        }
        map.invalidate();
        zoomTo(fromLat, fromLon, lat, lon);
    }

    private void zoomTo(final double aLat, final double aLon, final double bLat, final double bLon) {
        List<GeoPoint> two = new ArrayList<>();
        two.add(new GeoPoint(aLat, aLon));
        two.add(new GeoPoint(bLat, bLon));
        MapHelper.fitPoints(map, two, false);
    }

    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); if (routeAnim != null) routeAnim.resume(); }
    @Override protected void onPause() { super.onPause(); if (routeAnim != null) routeAnim.pause(); if (map != null) map.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); if (map != null) map.onDetach(); }
}
