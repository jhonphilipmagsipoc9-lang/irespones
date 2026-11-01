package com.rescuelink.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.StationAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.MapHelper;
import com.rescuelink.app.utils.RouteAnim;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 1 of reporting: after tapping a category (for example Medical) the citizen sees the matching stations
 * on the OpenStreetMap map and in a list, picks one, and the report form opens addressed to that station.
 */
public class StationPickerActivity extends AppCompatActivity {
    public static final String EXTRA_CATEGORY = "category";

    private MapView map;
    private RouteAnim routeAnim;
    private String category;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        LocationHelper.refresh(this);
        final double myLat = GeoUtils.myLat();
        final double myLon = GeoUtils.myLon();
        List<Station> options = DataRepository.stationsForCategory(category, myLat, myLon);
        if (options.isEmpty()) { // nothing to choose from, go straight to the form
            openReport(null);
            return;
        }
        setContentView(R.layout.activity_station_picker);
        ((TextView) findViewById(R.id.tv_pick_title)).setText(category + ": choose a station");
        ((TextView) findViewById(R.id.tv_pick_sub)).setText("Tap a pin or a station below. Your report goes straight to it.");
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        map = findViewById(R.id.map);
        MapHelper.setup(map, myLat, myLon, 14.0);
        MapHelper.addMarker(map, myLat, myLon, "You", MapHelper.COLOR_PICK);
        final List<GeoPoint> pts = new ArrayList<>();
        pts.add(new GeoPoint(myLat, myLon));
        routeAnim = new RouteAnim(map);
        map.getOverlays().add(routeAnim);
        List<RouteAnim.Route> routes = new ArrayList<>();
        for (final Station s : options) {
            routes.add(new RouteAnim.Route(new GeoPoint(s.getLat(), s.getLon()), new GeoPoint(myLat, myLon),
                    MapHelper.stationColor(s)));
        }
        routeAnim.setRoutes(routes);
        for (final Station s : options) {
            Marker m = MapHelper.addBadgeMarker(map, s.getLat(), s.getLon(), s.getName(), MapHelper.stationColor(s), MapHelper.iconFor(s.getCategory()));
            m.setSnippet(s.getCategory());
            m.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    confirm(s);
                    return true;
                }
            });
            pts.add(new GeoPoint(s.getLat(), s.getLon()));
        }
        map.post(new Runnable() {
            @Override
            public void run() {
                if (pts.size() > 1) MapHelper.fitPoints(map, pts, false);
            }
        });

        StationAdapter adapter = new StationAdapter(new java.util.function.Consumer<Station>() {
            @Override
            public void accept(Station s) { confirm(s); }
        });
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        adapter.setItems(options);
    }

    private void confirm(final Station s) {
        double km = GeoUtils.distanceFromMeKm(s.getLat(), s.getLon());
        new MaterialAlertDialogBuilder(this)
                .setTitle(s.getName())
                .setMessage(s.getCategory() + "\n" + s.getAddress() + "\n\n" + GeoUtils.formatDistance(km)
                        + " away, about " + GeoUtils.formatEta(km) + " by road.\n\n"
                        + "Send your " + category + " report to this station?")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Directions", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        MapHelper.navigateTo(StationPickerActivity.this, s.getLat(), s.getLon(), s.getName());
                    }
                })
                .setPositiveButton("Send report here", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { openReport(s); }
                })
                .show();
    }

    private void openReport(Station s) {
        Intent i = new Intent(this, ReportActivity.class);
        i.putExtra("category", category);
        if (s != null) i.putExtra(ReportActivity.EXTRA_STATION, s.getName());
        startActivity(i);
        finish();
    }

    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); if (routeAnim != null) routeAnim.resume(); }
    @Override protected void onPause() { super.onPause(); if (routeAnim != null) routeAnim.pause(); if (map != null) map.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); if (map != null) map.onDetach(); }
}
