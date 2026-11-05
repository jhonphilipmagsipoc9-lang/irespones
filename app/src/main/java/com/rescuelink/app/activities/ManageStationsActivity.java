package com.rescuelink.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.RowAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Command Center: the four kinds of station (Police, Fire, DRRMO, Hospital). Tap one to edit its number, location or availability. */
public class ManageStationsActivity extends AppCompatActivity {
    public static final String EXTRA_EVAC = "evac";
    private RowAdapter adapter;
    private boolean evacOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireRole(this, com.rescuelink.app.models.User.ROLE_ADMIN)) return;
        com.rescuelink.app.models.User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        evacOnly = getIntent().getBooleanExtra(EXTRA_EVAC, false);
        if (evacOnly ? !StationEditActivity.canManageEvac(me) : com.rescuelink.app.data.DataRepository.isScoped(me)) { finish(); return; }
        setContentView(R.layout.activity_simple_list);
        ((TextView) findViewById(R.id.tv_list_title)).setText(evacOnly ? "Evacuation sites" : "Stations");
        ((TextView) findViewById(R.id.tv_list_sub)).setText(evacOnly
                ? "Add evacuation sites with a location and a picture. Everyone can see them on the map. Tap one to edit it."
                : "Set each station's phone number and exact location. Tap one to edit it.");
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        TextView empty = findViewById(R.id.tv_empty);
        empty.setText(evacOnly ? "No evacuation sites yet. Tap Add to create the first one." : "No stations yet. Tap Add to create the first one.");
        MaterialButton add = findViewById(R.id.btn_action);
        add.setVisibility(View.VISIBLE);
        add.setText("Add");
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(ManageStationsActivity.this, StationEditActivity.class);
                if (evacOnly) i.putExtra(StationEditActivity.EXTRA_EVAC, true);
                startActivity(i);
            }
        });

        adapter = new RowAdapter(new java.util.function.Consumer<RowAdapter.Row>() {
            @Override
            public void accept(RowAdapter.Row row) {
                Intent i = new Intent(ManageStationsActivity.this, StationEditActivity.class);
                i.putExtra(StationEditActivity.EXTRA_NAME, ((Station) row.tag).getName());
                startActivity(i);
            }
        });
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        bind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bind();
    }

    private void bind() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Station s : DataRepository.getStationsByDistance()) {
            if (evacOnly && !StationEditActivity.isEvac(s.getCategory())) continue;
            String phone = s.getPhone() == null || s.getPhone().isEmpty() ? "No phone saved" : s.getPhone();
            RowAdapter.Row r = new RowAdapter.Row(s.getName(), s.getCategory() + " · " + s.getAddress())
                    .meta(phone + " · " + GeoUtils.formatDistance(GeoUtils.distanceFromMeKm(s.getLat(), s.getLon())))
                    .tag(s);
            if (s.isAvailable()) r.badge(evacOnly ? "Open" : "Available", UiUtils.GREEN_FG, UiUtils.GREEN_BG);
            else r.badge(evacOnly ? "Closed" : "Unavailable", UiUtils.GRAY_FG, UiUtils.GRAY_BG);
            rows.add(r);
        }
        adapter.setItems(rows);
        findViewById(R.id.tv_empty).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** What deleting will do, including how many responders lose their station. */
    static String deleteWarning(Station s) {
        int n = DataRepository.getRespondersAt(s.getName()).size();
        return "It disappears from the map and from every list."
                + (n == 0 ? "" : "\n\n" + n + (n == 1 ? " responder is" : " responders are")
                + " registered here. They stay in the app but will have no station until you assign one.");
    }
}
