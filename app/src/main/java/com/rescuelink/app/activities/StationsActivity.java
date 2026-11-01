package com.rescuelink.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.StationAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LiveLocation;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.MapHelper;
import com.rescuelink.app.utils.SimpleTextWatcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Response stations and evacuation sites, nearest first, with search and type filters. */
public class StationsActivity extends AppCompatActivity {
    public static final String EXTRA_QUERY = "query";
    private StationAdapter adapter;
    private List<Station> all = new ArrayList<>();
    private String filter = "All";
    private String query = "";
    private TextView empty;
    private boolean askedPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_simple_list);
        ((TextView) findViewById(R.id.tv_list_title)).setText("Response stations");
        ((TextView) findViewById(R.id.tv_list_sub)).setText("Nearest first, based on your location.");
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
        empty = findViewById(R.id.tv_empty);
        empty.setText("No stations match your search");

        all = DataRepository.getStationsByDistance();
        adapter = new StationAdapter(new java.util.function.Consumer<com.rescuelink.app.models.Station>() {
            @Override
            public void accept(com.rescuelink.app.models.Station s) {
                showDetails(StationsActivity.this, s, new Runnable() {
                    @Override public void run() { reload(); }
                });
            }
        });
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        EditText et = findViewById(R.id.et_search);
        et.setHint("Search police, medical, DRRMO, fire...");
        et.setVisibility(View.VISIBLE);
        String start = getIntent().getStringExtra(EXTRA_QUERY);
        if (start != null && !start.isEmpty()) {
            et.setText(start);
            query = start.trim().toLowerCase(Locale.ROOT);
        }
        et.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onChanged(String text) {
                query = text.trim().toLowerCase(Locale.ROOT);
                apply();
            }
        });

        Set<String> types = new LinkedHashSet<>();
        types.add("All");
        for (Station s : all) types.add(s.getCategory());
        ChipGroup group = findViewById(R.id.chips);
        for (String t : types) {
            Chip c = (Chip) getLayoutInflater().inflate(R.layout.chip_choice, group, false);
            c.setText(t);
            c.setId(View.generateViewId());
            c.setChecked(t.equals("All"));
            c.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) { filter = t; apply(); }
            });
            group.addView(c);
        }
        findViewById(R.id.chips_scroll).setVisibility(View.VISIBLE);
        apply();
    }

    /** Re-reads the stations (after one is deleted, or when the phone moves) and keeps the filters. */
    private void reload() {
        if (adapter == null) return;
        all = DataRepository.getStationsByDistance();
        apply();
    }

    private final LiveLocation.Listener live = new LiveLocation.Listener() {
        @Override public void onLocation(double lat, double lon) { reload(); }
    };

    @Override
    protected void onStart() {
        super.onStart();
        // Distances follow the phone while this list is open.
        if (adapter == null) return;
        if (LocationHelper.hasPermission(this)) {
            LiveLocation.start(this, live);
        } else if (!askedPermission) {
            askedPermission = true;
            permLauncher.launch(LocationHelper.PERMISSIONS);
        }
        reload();
    }

    @Override
    protected void onStop() {
        super.onStop();
        LiveLocation.stop(live);
    }

    private final androidx.activity.result.ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
            new androidx.activity.result.ActivityResultCallback<java.util.Map<String, Boolean>>() {
                @Override
                public void onActivityResult(java.util.Map<String, Boolean> r) {
                    if (LocationHelper.hasPermission(StationsActivity.this)) LiveLocation.start(StationsActivity.this, live);
                }
            });

    private void apply() {
        List<Station> out = new ArrayList<>();
        for (Station s : all) {
            if (!filter.equals("All") && !s.getCategory().equals(filter)) continue;
            if (!query.isEmpty() && !s.getName().toLowerCase(Locale.ROOT).contains(query)
                    && !s.getCategory().toLowerCase(Locale.ROOT).contains(query)
                    && !s.getAddress().toLowerCase(Locale.ROOT).contains(query)) continue;
            out.add(s);
        }
        adapter.setItems(out);
        empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    public static void showDetails(Context ctx, Station s) { showDetails(ctx, s, null); }

    /** Station card. The admin only gets Delete. {@code onChanged} runs after a delete. */
    public static void showDetails(final Context ctx, final Station s, final Runnable onChanged) {
        double km = GeoUtils.distanceFromMeKm(s.getLat(), s.getLon());
        String msg = s.getCategory() + "\n" + s.getAddress()
                + "\n\n" + GeoUtils.formatDistance(km) + " away, about " + GeoUtils.formatEta(km) + " by road"
                + "\nContact: " + (s.getPhone().isEmpty() ? "Not saved yet" : s.getPhone())
                + "\nStatus: " + (s.isAvailable() ? "Available" : "Unavailable");
        boolean admin = com.rescuelink.app.models.User.ROLE_ADMIN.equals(
                com.rescuelink.app.utils.SessionManager.role())
                && !com.rescuelink.app.data.DataRepository.isScoped(com.rescuelink.app.utils.SessionManager.getCurrentUser());
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx)
                .setTitle(s.getName())
                .setMessage(msg)
                .setNegativeButton("Close", null);
        if (admin) {
            // The admin can only remove a station; its details belong to the responders.
            b.setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    new MaterialAlertDialogBuilder(ctx)
                            .setTitle("Delete " + s.getName() + "?")
                            .setMessage(ManageStationsActivity.deleteWarning(s))
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d2, int w2) {
                                    DataRepository.deleteStation(s);
                                    if (onChanged != null) onChanged.run();
                                }
                            })
                            .show();
                }
            });
        } else {
            b.setPositiveButton("Directions", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    MapHelper.navigateTo(ctx, s.getLat(), s.getLon(), s.getName());
                }
            });
            b.setNeutralButton("Call", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    MapHelper.dial(ctx, s.getPhone());
                }
            });
        }
        b.show();
    }
}
