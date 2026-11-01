package com.rescuelink.app.activities;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.rescuelink.app.R;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.MapHelper;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.List;
import java.util.Locale;

/** Full-screen map for choosing the exact spot of an incident. Returns lat, lon and a street address. */
public class MapPickerActivity extends AppCompatActivity {
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    public static final String EXTRA_ADDRESS = "address";
    /** Set when the picker is opened before sign-in (responder registration). */
    public static final String EXTRA_PUBLIC = "public";

    private MapView map;
    private boolean userMoved;
    private TextView tvCoords, tvHint;
    private MaterialButton btnConfirm;

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), new androidx.activity.result.ActivityResultCallback<java.util.Map<java.lang.String, java.lang.Boolean>>() {
                @Override
                public void onActivityResult(java.util.Map<java.lang.String, java.lang.Boolean> r) {
                    goToMe();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().getBooleanExtra(EXTRA_PUBLIC, false)
                && !com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_map_picker);
        map = findViewById(R.id.map);
        tvCoords = findViewById(R.id.tv_pick_coords);
        tvHint = findViewById(R.id.tv_pick_hint);
        btnConfirm = findViewById(R.id.btn_confirm_pick);

        double lat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
        double lon = getIntent().getDoubleExtra(EXTRA_LON, Double.NaN);
        boolean given = !Double.isNaN(lat) && !Double.isNaN(lon);
        if (!given) {
            LocationHelper.refresh(this);
            lat = GeoUtils.myLat();
            lon = GeoUtils.myLon();
        }
        MapHelper.setup(map, lat, lon, given || GeoUtils.hasFix() ? 17.0 : 14.0);
        userMoved = given; // a pin that was handed in is never pulled away by the first GPS fix
        map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent event) { updateCoords(); return false; }
            @Override public boolean onZoom(ZoomEvent event) { return false; }
        });
        updateCoords();
        map.setOnTouchListener(new android.view.View.OnTouchListener() {
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_MOVE) userMoved = true;
                return false;
            }
        });
        if (!given && !LocationHelper.hasPermission(this)) permLauncher.launch(LocationHelper.PERMISSIONS);

        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
        findViewById(R.id.btn_locate).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
            if (!LocationHelper.hasPermission(MapPickerActivity.this)) permLauncher.launch(LocationHelper.PERMISSIONS);
            else goToMe();
        }
        });
        btnConfirm.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                confirm();
            }
        });
    }

    /** Until the person drags the map, the first real GPS fix moves the pin to where they actually are. */
    private final com.rescuelink.app.utils.LiveLocation.Listener live = new com.rescuelink.app.utils.LiveLocation.Listener() {
        @Override
        public void onLocation(double lat, double lon) {
            if (map == null || userMoved) return;
            map.getController().animateTo(new GeoPoint(lat, lon));
            map.getController().setZoom(17.0);
        }
    };

    private void goToMe() {
        com.rescuelink.app.utils.LiveLocation.start(this, live);
        if (LocationHelper.refresh(this) && GeoUtils.hasFix()) {
            userMoved = false;
            map.getController().animateTo(new GeoPoint(GeoUtils.myLat(), GeoUtils.myLon()));
            map.getController().setZoom(17.0);
        } else {
            userMoved = false; // the pin jumps to you as soon as the first fix arrives
            Toast.makeText(this, com.rescuelink.app.utils.LiveLocation.isEnabled(this)
                            ? "Finding your location..."
                            : "Location is off. Turn it on in your phone settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateCoords() {
        GeoPoint c = (GeoPoint) map.getMapCenter();
        tvCoords.setText(GeoUtils.formatCoords(c.getLatitude(), c.getLongitude()));
        double km = GeoUtils.distanceFromMeKm(c.getLatitude(), c.getLongitude());
        tvHint.setText(GeoUtils.hasFix() ? GeoUtils.formatDistance(km) + " from you" : "Drag the map to place the pin");
    }

    private void confirm() {
        GeoPoint c = (GeoPoint) map.getMapCenter();
        final double lat = c.getLatitude();
        final double lon = c.getLongitude();
        btnConfirm.setEnabled(false);
        btnConfirm.setText("Finding address...");
        new Thread(new java.lang.Runnable() {
            @Override
            public void run() {
            String address = reverseGeocode(lat, lon);
            runOnUiThread(new java.lang.Runnable() {
                @Override
                public void run() {
                if (isFinishing() || isDestroyed()) return;
                Intent result = new Intent();
                result.putExtra(EXTRA_LAT, lat);
                result.putExtra(EXTRA_LON, lon);
                result.putExtra(EXTRA_ADDRESS, address);
                setResult(RESULT_OK, result);
                finish();
            }
            });
        }
        }).start();
    }

    /** Street address from OpenStreetMap's Nominatim service. No Google service involved. */
    private String reverseGeocode(double lat, double lon) {
        try {
            String url = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&lat=%f&lon=%f", lat, lon);
            String name = new org.json.JSONObject(com.rescuelink.app.utils.MapHelper.httpGet(url)).optString("display_name", "");
            return name;
        } catch (Exception ignored) { }
        return "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        com.rescuelink.app.utils.LiveLocation.start(this, live);
    }

    @Override
    protected void onPause() {
        super.onPause();
        com.rescuelink.app.utils.LiveLocation.stop(live);
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        map.onDetach();
    }
}
