package com.rescuelink.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.Validator;

/** Add or edit a station (a service admin only their own). Command Center: any station (Police, Fire, DRRMO, Hospital): name, number, location, availability. */
public class StationEditActivity extends AppCompatActivity {
    public static final String EXTRA_NAME = "name";
    public static final String[] TYPES = {"Police", "Fire", "DRRMO", "Medical"};

    private Station editing;
    private double lat = Double.NaN, lon = Double.NaN;
    private String address = "";
    private TextView tvLocation;

    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new androidx.activity.result.ActivityResultCallback<androidx.activity.result.ActivityResult>() {
                @Override public void onActivityResult(androidx.activity.result.ActivityResult r) {
                    if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                    double la = r.getData().getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN);
                    double lo = r.getData().getDoubleExtra(MapPickerActivity.EXTRA_LON, Double.NaN);
                    if (Double.isNaN(la) || Double.isNaN(lo)) return;
                    lat = la; lon = lo;
                    String a = r.getData().getStringExtra(MapPickerActivity.EXTRA_ADDRESS);
                    if (a != null && !a.trim().isEmpty()) {
                        address = a.trim();
                        ((EditText) findViewById(R.id.et_address)).setText(address);
                    }
                    showLocation();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.requireRole(this, User.ROLE_ADMIN)) return;
        final User me = SessionManager.getCurrentUser();
        final boolean scoped = me.isServiceAdmin();   // a service admin only ever edits their own station
        setContentView(R.layout.activity_station_edit);

        final TextInputLayout tilName = findViewById(R.id.til_name);
        final TextInputLayout tilType = findViewById(R.id.til_type);
        final TextInputLayout tilPhone = findViewById(R.id.til_phone);
        final EditText etName = findViewById(R.id.et_name);
        final EditText etPhone = findViewById(R.id.et_phone);
        final EditText etAddress = findViewById(R.id.et_address);
        final AutoCompleteTextView actvType = findViewById(R.id.actv_type);
        final CheckBox cbAvail = findViewById(R.id.cb_available);
        tvLocation = findViewById(R.id.tv_location);
        actvType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, TYPES));

        editing = DataRepository.findStation(scoped ? me.getStationName() : getIntent().getStringExtra(EXTRA_NAME));
        if (scoped) {
            actvType.setText(me.getService(), false);
            actvType.setEnabled(false);
        }
        if (editing != null) {
            ((TextView) findViewById(R.id.tv_title)).setText(scoped ? "My station" : "Edit station");
            etName.setText(editing.getName());
            String t = editing.getCategory();
            actvType.setText("Medical".equals(t) || "Hospital".equals(t) ? "Medical" : "Rescue".equals(t) ? "DRRMO" : t, false);
            etPhone.setText(editing.getPhone());
            etAddress.setText(editing.getAddress());
            address = editing.getAddress();
            lat = editing.getLat();
            lon = editing.getLon();
            cbAvail.setChecked(editing.isAvailable());
            findViewById(R.id.btn_delete).setVisibility(scoped ? View.GONE : View.VISIBLE);
        } else {
            ((TextView) findViewById(R.id.tv_title)).setText(scoped ? "Set up my station" : "Add station");
        }
        showLocation();

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btn_location).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(StationEditActivity.this, MapPickerActivity.class);
                if (!Double.isNaN(lat)) { i.putExtra(MapPickerActivity.EXTRA_LAT, lat); i.putExtra(MapPickerActivity.EXTRA_LON, lon); }
                picker.launch(i);
            }
        });
        findViewById(R.id.btn_delete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new MaterialAlertDialogBuilder(StationEditActivity.this)
                        .setTitle("Delete " + editing.getName() + "?")
                        .setMessage(ManageStationsActivity.deleteWarning(editing))
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                DataRepository.deleteStation(editing);
                                finish();
                            }
                        }).show();
            }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tilName.setError(null); tilType.setError(null); tilPhone.setError(null);
                String name = etName.getText().toString().trim();
                String type = actvType.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                boolean ok = true;
                if (name.isEmpty()) { tilName.setError(getString(R.string.err_required)); ok = false; }
                Station same = DataRepository.findStationIgnoreCase(name);
                if (same != null && same != editing) { tilName.setError("A station with this name already exists"); ok = false; }
                boolean known = false;
                for (String t : TYPES) if (t.equals(type)) known = true;
                if (!known) { tilType.setError("Choose a type"); ok = false; }
                if (!phone.isEmpty() && !Validator.isValidPhoneLoose(phone)) { tilPhone.setError("Use digits, + or - only"); ok = false; }
                if (Double.isNaN(lat) || Double.isNaN(lon)) { tvLocation.setText("Set the station's location on the map"); ok = false; }
                if (!ok) return;
                String addr = etAddress.getText().toString().trim();
                if (addr.isEmpty()) addr = GeoUtils.formatCoords(lat, lon);
                if (editing == null) {
                    Station s = new Station(name, type, addr, lat, lon, cbAvail.isChecked(), phone);
                    DataRepository.addStation(s);
                    if (scoped) {                       // this is the admin's own station; their responders join it
                        me.setStationName(s.getName());
                        me.setPosition(lat, lon);
                        DataRepository.save();
                    }
                } else {
                    DataRepository.updateStation(editing, name, type, addr, lat, lon);
                    editing.setDetails(name, type, addr, phone);
                    editing.setAvailable(cbAvail.isChecked());
                    DataRepository.save();
                }
                Toast.makeText(StationEditActivity.this, "Station saved.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showLocation() {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            tvLocation.setText("No location yet. Responders find this station by its map position.");
        } else {
            tvLocation.setText("Location set: " + GeoUtils.formatCoords(lat, lon));
        }
    }
}
