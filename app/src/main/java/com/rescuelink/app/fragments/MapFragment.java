package com.rescuelink.app.fragments;

import com.rescuelink.app.utils.ThemeColors;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.rescuelink.app.R;
import com.rescuelink.app.activities.ReportActivity;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LiveLocation;
import com.rescuelink.app.utils.MapHelper;
import com.rescuelink.app.utils.Nav;
import com.rescuelink.app.utils.RouteAnim;
import com.rescuelink.app.utils.SearchUi;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Street map (OpenStreetMap) used by citizens, responders and admins with role-specific layers. */
public class MapFragment extends Fragment {
    private MapView map;
    private MyLocationNewOverlay myLoc;
    private final List<Marker> dynamic = new ArrayList<>();
    private Marker pickMarker;
    private RouteAnim routeAnim;

    private Chip chipIncidents, chipStations, chipEvac, chipResponders;
    private View card, cardSearch;
    private android.widget.ScrollView suggestScroll;
    private android.widget.LinearLayout suggestBox;
    private EditText searchBox;
    private boolean muteSuggest;
    /** Station names matching the letters typed. While set, those pins are lit up and the others fade. */
    private final java.util.Set<String> highlighted = new java.util.HashSet<>();
    private TextView tvTitle, tvSub, tvMeta, tvBadge;
    private MaterialButton btnPrimary, btnSecondary;

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), new androidx.activity.result.ActivityResultCallback<java.util.Map<java.lang.String, java.lang.Boolean>>() {
                @Override
                public void onActivityResult(java.util.Map<java.lang.String, java.lang.Boolean> result) {
                if (hasLocationPermission()) startLocation(true);
                else Toast.makeText(requireContext(), "Location permission is needed to show where you are.",
                        Toast.LENGTH_SHORT).show();
            }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        map = v.findViewById(R.id.map);
        MapHelper.setup(map, GeoUtils.myLat(), GeoUtils.myLon(), 14.0);

        card = v.findViewById(R.id.card_map);
        cardSearch = v.findViewById(R.id.card_search);
        int[] catIds = {R.id.chip_cat_medical, R.id.chip_cat_police, R.id.chip_cat_fire, R.id.chip_cat_drrmo};
        String[] cats = {"Medical", "Police", "Fire", "DRRMO"};
        for (int k = 0; k < catIds.length; k++) {
            final String cat = cats[k];
            v.findViewById(catIds[k]).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { showCategory(cat); }
            });
        }
        tvTitle = v.findViewById(R.id.tv_map_title);
        tvSub = v.findViewById(R.id.tv_map_sub);
        tvMeta = v.findViewById(R.id.tv_map_meta);
        tvBadge = v.findViewById(R.id.tv_map_badge);
        btnPrimary = v.findViewById(R.id.btn_map_primary);
        btnSecondary = v.findViewById(R.id.btn_map_secondary);
        chipIncidents = v.findViewById(R.id.chip_incidents);
        chipStations = v.findViewById(R.id.chip_stations);
        chipEvac = v.findViewById(R.id.chip_evac);
        chipResponders = v.findViewById(R.id.chip_responders);

        boolean citizen = User.ROLE_CITIZEN.equals(SessionManager.role());
        chipResponders.setVisibility(View.VISIBLE);      // everyone can see who is closest
        chipIncidents.setText(citizen ? "My reports" : "Incidents");

        android.widget.CompoundButton.OnCheckedChangeListener refresh =
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                        refreshMarkers();
                    }
                };
        chipIncidents.setOnCheckedChangeListener(refresh);
        chipStations.setOnCheckedChangeListener(refresh);
        chipEvac.setOnCheckedChangeListener(refresh);
        chipResponders.setOnCheckedChangeListener(refresh);

        map.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                hideCard();
                return false;
            }
            @Override public boolean longPressHelper(GeoPoint p) {
                selectPoint(p);
                return true;
            }
        }));

        routeAnim = new RouteAnim(map);
        map.getOverlays().add(1, routeAnim);   // under the pins, above the tiles

        v.findViewById(R.id.btn_zoom_in).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                map.getController().zoomIn();
            }
        });
        v.findViewById(R.id.btn_zoom_out).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                map.getController().zoomOut();
            }
        });
        v.findViewById(R.id.btn_locate).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                locateMe();
            }
        });
        v.findViewById(R.id.btn_map_close).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                hideCard();
            }
        });
        v.findViewById(R.id.btn_map_layers).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View anchorView) {
                MapFragment.this.showStyleMenu(anchorView);
            }
        });

        EditText search = v.findViewById(R.id.et_map_search);
        search.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView tv, int actionId, android.view.KeyEvent event) {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                runSearch(tv.getText().toString());
                InputMethodManager imm = (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(tv.getWindowToken(), 0);
                return true;
            }
            return false;
        }
        });

        searchBox = search;
        suggestScroll = v.findViewById(R.id.sv_suggest);
        suggestBox = v.findViewById(R.id.ll_suggest);
        search.addTextChangedListener(new com.rescuelink.app.utils.SimpleTextWatcher() {
            @Override public void onChanged(String text) {
                if (!muteSuggest) showSuggestions(text);
            }
        });

        refreshMarkers();
        // Open on where the person actually is and keep following them until they drag the map.
        if (hasLocationPermission()) {
            startLocation(true);
        } else {
            permLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    // ---------- live position ----------
    private long lastMarkerRefresh;
    private final android.os.Handler tick = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Responders move on their own phones, so redraw their pins every few seconds. */
    private final Runnable pinTick = new Runnable() {
        @Override public void run() {
            if (map != null && isResumed()) refreshMarkers();
            tick.postDelayed(this, 4000);
        }
    };

    /** Feeds the shared position (distances on cards and lists) and keeps responder pins moving. */
    private final LiveLocation.Listener live = new LiveLocation.Listener() {
        @Override
        public void onLocation(double lat, double lon) {
            if (map == null) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastMarkerRefresh > 3000) {
                lastMarkerRefresh = now;
                refreshMarkers();
            }
        }
    };

    private boolean warnedLocationOff;

    private void warnIfLocationOff() {
        if (warnedLocationOff || !hasLocationPermission() || LiveLocation.isEnabled(requireContext())) return;
        warnedLocationOff = true;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Turn on location")
                .setMessage("The map can only follow you while your phone's location (GPS) is switched on.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
            if (myLoc != null && hasLocationPermission()) myLoc.enableMyLocation();
            if (routeAnim != null) routeAnim.resume();
            if (hasLocationPermission()) {
                LiveLocation.start(requireContext(), live);
                warnIfLocationOff();
            }
            refreshMarkers();
        }
        tick.removeCallbacks(pinTick);
        tick.postDelayed(pinTick, 4000);
    }

    @Override
    public void onPause() {
        super.onPause();
        tick.removeCallbacks(pinTick);
        LiveLocation.stop(live);
        if (map != null) {
            if (myLoc != null) myLoc.disableMyLocation();
            if (routeAnim != null) routeAnim.pause();
            map.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (routeAnim != null) routeAnim.stop();
        routeAnim = null;
        if (map != null) map.onDetach();
        map = null;
        myLoc = null;
        pickMarker = null;
        dynamic.clear();
    }

    // ---------- location ----------
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void locateMe() {
        if (!hasLocationPermission()) {
            permLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        startLocation(true);
        warnIfLocationOff();
        GeoPoint p = myLoc == null ? null : myLoc.getMyLocation();
        if (p != null) {
            map.getController().animateTo(p);
            map.getController().setZoom(17.0);
        } else {
            Toast.makeText(requireContext(), "Finding your location...", Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocation(boolean center) {
        if (map == null) return;
        if (myLoc == null) {
            myLoc = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
            myLoc.setDrawAccuracyEnabled(true);
            map.getOverlays().add(myLoc);
        }
        myLoc.enableMyLocation();
        if (center) myLoc.enableFollowLocation(); // the map moves with the person until they drag it
        final MyLocationNewOverlay overlay = myLoc;
        overlay.runOnFirstFix(new java.lang.Runnable() {
            @Override
            public void run() {
            GeoPoint p = overlay.getMyLocation();
            if (p == null || getActivity() == null) return;
            getActivity().runOnUiThread(new java.lang.Runnable() {
                @Override
                public void run() {
                if (map == null) return;
                GeoUtils.setMyLocation(p.getLatitude(), p.getLongitude());
                User me = SessionManager.getCurrentUser();
                if (me != null && User.ROLE_RESPONDER.equals(me.getRole())) {
                    me.setPosition(p.getLatitude(), p.getLongitude());
                }
                if (center) {
                    map.getController().animateTo(p);
                    map.getController().setZoom(17.0);
                }
                refreshMarkers();
            }
            });
        }
        });
    }

    // ---------- markers ----------
    private List<Incident> incidentsForRole() {
        List<Incident> out = new ArrayList<>();
        if (User.ROLE_CITIZEN.equals(SessionManager.role())) {
            for (Incident i : DataRepository.getIncidentsFor(SessionManager.username())) {
                if (i.getStatus() != IncidentStatus.DRAFT && i.getStatus() != IncidentStatus.CANCELLED) out.add(i);
            }
        } else {
            for (Incident i : DataRepository.getAllIncidentsFor(SessionManager.getCurrentUser())) if (i.isActive()) out.add(i);
        }
        return out;
    }

    private void refreshMarkers() {
        if (map == null) return;
        for (Marker m : dynamic) map.getOverlays().remove(m);
        dynamic.clear();

        List<RouteAnim.Route> routes = new ArrayList<>();
        if (chipIncidents.isChecked()) {
            for (Incident i : incidentsForRole()) {
                if (!i.hasPosition()) continue;
                Station from = MapHelper.stationFor(i);      // animated dispatch line: station to citizen
                if (from != null && i.isActive()) {
                    routes.add(MapHelper.roadRoute(from.getLat(), from.getLon(), i.getLat(), i.getLon(), MapHelper.incidentColor(i)));
                    MapHelper.requestRoad(from.getLat(), from.getLon(), i.getLat(), i.getLon(), new Runnable() {
                        @Override public void run() { if (map != null) refreshMarkers(); }
                    });
                }
                Marker m = MapHelper.addBadgeMarker(map, i.getLat(), i.getLon(), i.displayTitle(),
                        MapHelper.incidentColor(i), MapHelper.iconFor(i.getCategory()));
                m.setOnMarkerClickListener(new org.osmdroid.views.overlay.Marker.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(org.osmdroid.views.overlay.Marker mk, org.osmdroid.views.MapView mv) { selectIncident(i); return true; }
                });
                dynamic.add(m);
            }
        }
        for (Station s : DataRepository.getStations()) {
            boolean evac = "Evacuation".equals(s.getCategory()) || "Relief".equals(s.getCategory());
            if (evac ? !chipEvac.isChecked() : !chipStations.isChecked()) continue;
            int pin = highlighted.isEmpty() ? MapHelper.stationColor(s)
                    : highlighted.contains(s.getName()) ? 0xFFF59E0B : 0xFFB7C0CC;
            Marker m = MapHelper.addBadgeMarker(map, s.getLat(), s.getLon(), s.getName(), pin,
                    evac ? R.drawable.ic_pin : MapHelper.iconFor(s.getCategory()));
            m.setOnMarkerClickListener(new org.osmdroid.views.overlay.Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(org.osmdroid.views.overlay.Marker mk, org.osmdroid.views.MapView mv) { selectStation(s); return true; }
            });
            dynamic.add(m);
        }
        if (chipResponders.isChecked()) {
            User viewer = SessionManager.getCurrentUser();
            // Citizens, dispatchers and the Command Center see every responder; a service admin or responder sees their own service.
            String svc = viewer == null ? "" : viewer.isServiceAdmin() ? viewer.getService()
                    : User.ROLE_RESPONDER.equals(viewer.getRole()) ? DataRepository.serviceOf(viewer) : "";
            for (User u : DataRepository.getRespondersIn(svc)) {
                if (!u.isActive() || !u.hasPosition() || User.DUTY_OFF.equals(u.getDutyStatus())) continue;
                Marker m = MapHelper.addAvatarMarker(map, u.getLat(), u.getLon(),
                        u.getFullName() + (u.isLive() || !u.isRemote() ? "" : " (last seen here)"),
                        u.isLive() || !u.isRemote() ? MapHelper.COLOR_RESPONDER : 0xFF8A94A6, u);
                m.setOnMarkerClickListener(new org.osmdroid.views.overlay.Marker.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(org.osmdroid.views.overlay.Marker mk, org.osmdroid.views.MapView mv) { selectResponder(u); return true; }
                });
                dynamic.add(m);
            }
        }
        if (routeAnim != null) routeAnim.setRoutes(routes);
        // keep the long-press pin on top
        if (pickMarker != null) {
            map.getOverlays().remove(pickMarker);
            map.getOverlays().add(pickMarker);
        }
        map.invalidate();
    }

    /** Quick filter from the bottom sheet: frames every station of one type and opens the nearest. */
    private void showCategory(String cat) {
        if (map == null) return;
        chipStations.setChecked(true);
        List<Station> list = DataRepository.stationsForCategory(cat, GeoUtils.myLat(), GeoUtils.myLon());
        if (list.isEmpty()) {
            Toast.makeText(requireContext(), "No " + cat + " station is available right now.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<GeoPoint> pts = new ArrayList<>();
        pts.add(new GeoPoint(GeoUtils.myLat(), GeoUtils.myLon()));
        for (Station s : list) pts.add(new GeoPoint(s.getLat(), s.getLon()));
        MapHelper.fitPoints(map, pts, true);
        selectStation(list.get(0));
    }

    // ---------- selection card ----------
    private void showCard(String title, String sub, String meta) {
        tvTitle.setText(title);
        tvSub.setText(sub);
        tvMeta.setText(meta);
        tvBadge.setVisibility(View.GONE);
        card.setVisibility(View.VISIBLE);
        cardSearch.setVisibility(View.GONE);
    }

    private void hideCard() {
        card.setVisibility(View.GONE);
        cardSearch.setVisibility(View.VISIBLE);
        if (pickMarker != null && map != null) {
            map.getOverlays().remove(pickMarker);
            pickMarker = null;
            map.invalidate();
        }
    }

    private String away(double lat, double lon) {
        double km = GeoUtils.distanceFromMeKm(lat, lon);
        return GeoUtils.formatDistance(km) + " away · about " + GeoUtils.formatEta(km);
    }

    private void selectIncident(Incident i) {
        String loc = i.getLocation().isEmpty() ? GeoUtils.formatCoords(i.getLat(), i.getLon()) : i.getLocation();
        showCard(i.displayTitle(), i.getCategory() + " · " + i.getReference(),
                loc + "\n" + away(i.getLat(), i.getLon()));
        UiUtils.styleChip(tvBadge, i.getStatus());
        tvBadge.setVisibility(View.VISIBLE);
        boolean citizen = User.ROLE_CITIZEN.equals(SessionManager.role());
        btnPrimary.setText(citizen ? "View report" : "Manage");
        btnPrimary.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                Nav.openIncident(requireContext(), i.getReference());
            }
        });
        btnSecondary.setText("Directions");
        btnSecondary.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                MapHelper.navigateTo(requireContext(), i.getLat(), i.getLon(), i.displayTitle());
            }
        });
        map.getController().animateTo(new GeoPoint(i.getLat(), i.getLon()));
    }

    private void selectStation(Station s) {
        showCard(s.getName(), s.getCategory() + " · " + s.getAddress(), away(s.getLat(), s.getLon()));
        if (s.isAvailable()) {
            UiUtils.styleBadge(tvBadge, "Available", ThemeColors.color(requireContext(), R.color.green),
                    Color.parseColor("#DDEFE5"));
        } else {
            UiUtils.styleBadge(tvBadge, "Unavailable", ThemeColors.color(requireContext(), R.color.text_secondary),
                    Color.parseColor("#EEF1F5"));
        }
        tvBadge.setVisibility(View.VISIBLE);
        btnPrimary.setText("Directions");
        btnPrimary.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                MapHelper.navigateTo(requireContext(), s.getLat(), s.getLon(), s.getName());
            }
        });
        btnSecondary.setText("Call");
        btnSecondary.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                MapHelper.dial(requireContext(), s.getPhone());
            }
        });
        map.getController().animateTo(new GeoPoint(s.getLat(), s.getLon()));
    }

    private void selectResponder(User u) {
        showCard(u.getFullName(), "Responder · " + u.getDutyStatus() + (u.isLive() ? " · Live now" : " · Offline")
                + (u.getStationName().isEmpty() ? "" : " · " + u.getStationName()), away(u.getLat(), u.getLon()));
        btnPrimary.setText("Call");
        btnPrimary.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                User me = SessionManager.getCurrentUser();
                if (com.rescuelink.app.call.CallLauncher.call(requireActivity(), u, Double.NaN, Double.NaN)) return;
                if (me != null && !User.ROLE_RESPONDER.equals(me.getRole())) {
                    Toast.makeText(requireContext(), u.isLive() ? "Could not start the call." : "This responder is offline. Personal numbers are private.", Toast.LENGTH_SHORT).show();
                } else {
                    MapHelper.dial(requireContext(), u.getContact());
                }
            }
        });
        btnSecondary.setText("Directions");
        btnSecondary.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                MapHelper.navigateTo(requireContext(), u.getLat(), u.getLon(), u.getFullName());
            }
        });
        map.getController().animateTo(new GeoPoint(u.getLat(), u.getLon()));
    }

    private void selectPoint(GeoPoint p) {
        if (pickMarker != null) map.getOverlays().remove(pickMarker);
        pickMarker = MapHelper.addMarker(map, p.getLatitude(), p.getLongitude(), "Selected location",
                MapHelper.COLOR_PICK);
        final double lat = p.getLatitude();
        final double lon = p.getLongitude();
        showCard("Selected location", GeoUtils.formatCoords(lat, lon), away(lat, lon));
        if (User.ROLE_CITIZEN.equals(SessionManager.role())) {
            btnPrimary.setText("Report here");
            btnPrimary.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) {
                Intent i = new Intent(requireContext(), ReportActivity.class);
                i.putExtra("lat", lat);
                i.putExtra("lon", lon);
                startActivity(i);
            }
            });
        } else {
            btnPrimary.setText("Directions");
            btnPrimary.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) {
                    MapHelper.navigateTo(requireContext(), lat, lon, "Selected location");
                }
            });
        }
        btnSecondary.setText("Share");
        btnSecondary.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                MapHelper.shareText(requireContext(), "Location",
                "My location: " + MapHelper.locationLink(lat, lon));
            }
        });
        map.invalidate();
    }

    // ---------- search and style ----------
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && searchBox != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
    }

    private void clearSuggestions() {
        if (suggestBox == null) return;
        suggestBox.removeAllViews();
        suggestScroll.setVisibility(View.GONE);
        if (!highlighted.isEmpty()) {
            highlighted.clear();
            refreshMarkers();
        }
    }

    private View suggestRow(CharSequence title, String sub, final Runnable onTap) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        int h = UiUtils.dp(requireContext(), 16), v = UiUtils.dp(requireContext(), 10);
        row.setPadding(h, v, h, v);
        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(ThemeColors.color(requireContext(), R.color.text_main));
        row.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView st = new TextView(requireContext());
            st.setText(sub);
            st.setTextSize(12);
            st.setTextColor(ThemeColors.color(requireContext(), R.color.text_secondary));
            row.addView(st);
        }
        if (onTap != null) {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                row.setBackgroundResource(tv.resourceId);
            }
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { onTap.run(); }
            });
        }
        return row;
    }

    /**
     * As letters are typed, lists every station with a word that starts with them ("b" gives Bayawan..., the
     * Bureau..., anything starting with B), highlights the letters, lights up those pins and frames them on the map.
     */
    private void showSuggestions(String raw) {
        if (suggestBox == null || !isAdded()) return;
        final String q = SearchUi.clean(raw);
        suggestBox.removeAllViews();
        highlighted.clear();
        if (q.isEmpty()) {
            suggestScroll.setVisibility(View.GONE);
            refreshMarkers();
            return;
        }
        final List<Station> hits = new ArrayList<>();
        for (Station s : DataRepository.getStations()) if (SearchUi.rank(s, q) >= 0) hits.add(s);
        java.util.Collections.sort(hits, new java.util.Comparator<Station>() {
            @Override public int compare(Station a, Station b) {
                int ra = SearchUi.rank(a, q), rb = SearchUi.rank(b, q);
                if (ra != rb) return Integer.compare(ra, rb);
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        final List<Incident> incs = new ArrayList<>();
        for (Incident i : incidentsForRole()) {
            if (i.hasPosition() && (SearchUi.wordStart(i.displayTitle(), q) >= 0
                    || SearchUi.wordStart(i.getReference(), q) >= 0 || SearchUi.wordStart(i.getCategory(), q) >= 0)) incs.add(i);
        }
        for (final Station s : hits) {
            highlighted.add(s.getName());
            String sub = s.getCategory() + " \u00B7 " + s.getAddress() + " \u00B7 "
                    + GeoUtils.formatDistance(GeoUtils.distanceFromMeKm(s.getLat(), s.getLon()));
            suggestBox.addView(suggestRow(SearchUi.highlight(s.getName(), q), sub, new Runnable() {
                @Override public void run() {
                    muteSuggest = true;
                    searchBox.setText(s.getName());
                    searchBox.setSelection(s.getName().length());
                    muteSuggest = false;
                    hideKeyboard();
                    clearSuggestions();
                    map.getController().setZoom(16.0);
                    selectStation(s);
                }
            }));
        }
        for (final Incident i : incs) {
            suggestBox.addView(suggestRow(SearchUi.highlight(i.displayTitle(), q),
                    "Incident \u00B7 " + i.getReference() + " \u00B7 " + i.getCategory(), new Runnable() {
                @Override public void run() {
                    hideKeyboard();
                    clearSuggestions();
                    map.getController().setZoom(16.0);
                    selectIncident(i);
                }
            }));
        }
        if (hits.isEmpty() && incs.isEmpty()) {
            suggestBox.addView(suggestRow("No station starts with \u201C" + raw.trim() + "\u201D", "", null));
        }
        suggestScroll.setVisibility(View.VISIBLE);
        // Keep the list from covering the whole map: about five rows tall, then it scrolls.
        suggestBox.measure(View.MeasureSpec.makeMeasureSpec(map.getWidth() > 0 ? map.getWidth() : 1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED);
        int max = UiUtils.dp(requireContext(), 290);
        ViewGroup.LayoutParams lp = suggestScroll.getLayoutParams();
        lp.height = Math.min(suggestBox.getMeasuredHeight(), max);
        suggestScroll.setLayoutParams(lp);

        if (!hits.isEmpty()) {
            chipStations.setChecked(true);
            List<GeoPoint> pts = new ArrayList<>();
            for (Station s : hits) pts.add(new GeoPoint(s.getLat(), s.getLon()));
            MapHelper.fitPoints(map, pts, true);
        }
        refreshMarkers();
    }

    private void runSearch(String raw) {
        String q = raw.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;
        clearSuggestions();
        Station best = null;
        for (Station s : DataRepository.getStations()) {      // letters at the start of a word come first
            int r = SearchUi.rank(s, q);
            if (r >= 0 && (best == null || r < SearchUi.rank(best, q))) best = s;
        }
        if (best != null) {
            map.getController().setZoom(16.0);
            selectStation(best);
            return;
        }
        for (Station s : DataRepository.getStations()) {
            if (s.getName().toLowerCase(Locale.ROOT).contains(q)
                    || s.getCategory().toLowerCase(Locale.ROOT).contains(q)
                    || s.getAddress().toLowerCase(Locale.ROOT).contains(q)) {
                map.getController().setZoom(16.0);
                selectStation(s);
                return;
            }
        }
        for (Incident i : incidentsForRole()) {
            if (i.hasPosition() && (i.displayTitle().toLowerCase(Locale.ROOT).contains(q)
                    || i.getCategory().toLowerCase(Locale.ROOT).contains(q)
                    || i.getReference().toLowerCase(Locale.ROOT).contains(q)
                    || i.getLocation().toLowerCase(Locale.ROOT).contains(q))) {
                map.getController().setZoom(16.0);
                selectIncident(i);
                return;
            }
        }
        Toast.makeText(requireContext(), "No station or incident matches \"" + raw.trim() + "\".",
                Toast.LENGTH_SHORT).show();
    }

    private void showStyleMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, "Street map (OpenStreetMap)");
        menu.getMenu().add(0, 2, 1, "Terrain (OpenTopoMap)");
        menu.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
            MapHelper.useSource(map, item.getItemId() == 2 ? TileSourceFactory.OpenTopo
                    : MapHelper.streetSource());
            return true;
        }
        });
        menu.show();
    }
}
