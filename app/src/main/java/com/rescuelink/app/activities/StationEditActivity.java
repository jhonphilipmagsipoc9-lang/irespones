package com.rescuelink.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;
import com.google.android.material.button.MaterialButton;
import com.rescuelink.app.data.StationPhotos;
import com.rescuelink.app.utils.AvatarUtil;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
    /** Set to true to add or edit an evacuation site (DRRMO admin and Command Center). */
    public static final String EXTRA_EVAC = "evac";
    public static final String[] TYPES = {"Police", "Fire", "DRRMO", "Medical"};

    /** The DRRMO admin and the Command Center may create evacuation sites. */
    public static boolean canManageEvac(User u) {
        return u != null && User.ROLE_ADMIN.equals(u.getRole()) && (u.isCommandCenter() || "DRRMO".equals(u.getService()));
    }

    static boolean isEvac(String category) { return "Evacuation".equals(category) || "Relief".equals(category); }

    private Bitmap newPhoto;
    private boolean photoRemoved;
    private ImageView ivPhoto;
    private MaterialButton btnPhoto;

    private final ActivityResultLauncher<String> gallery = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            new androidx.activity.result.ActivityResultCallback<Uri>() {
                @Override public void onActivityResult(Uri uri) {
                    if (uri == null) return;
                    Bitmap b = AvatarUtil.load(StationEditActivity.this, uri);
                    if (b == null) Toast.makeText(StationEditActivity.this, "That picture could not be used. Try another one.", Toast.LENGTH_SHORT).show();
                    else setPhoto(b);
                }
            });
    private final ActivityResultLauncher<Void> camera = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            new androidx.activity.result.ActivityResultCallback<Bitmap>() {
                @Override public void onActivityResult(Bitmap b) { if (b != null) setPhoto(b); }
            });

    private void setPhoto(Bitmap b) {
        newPhoto = b;
        photoRemoved = false;
        ivPhoto.setImageBitmap(b);
        ivPhoto.setVisibility(View.VISIBLE);
        btnPhoto.setText("Change the picture");
    }

    private void choosePhoto() {
        final boolean has = newPhoto != null || (editing != null && !editing.getPhotoUri().isEmpty() && !photoRemoved);
        String[] items = has
                ? new String[]{"Take a photo", "Choose from gallery", "Remove picture"}
                : new String[]{"Take a photo", "Choose from gallery"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Picture")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        try {
                            if (which == 0) camera.launch(null);
                            else if (which == 1) gallery.launch("image/*");
                            else {
                                newPhoto = null;
                                photoRemoved = true;
                                ivPhoto.setImageDrawable(null);
                                ivPhoto.setVisibility(View.GONE);
                                btnPhoto.setText("Add a picture");
                            }
                        } catch (RuntimeException e) {
                            Toast.makeText(StationEditActivity.this, "No camera or gallery app is available.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

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
        final Station pre = DataRepository.findStation(getIntent().getStringExtra(EXTRA_NAME));
        final boolean evac = getIntent().getBooleanExtra(EXTRA_EVAC, false) || (pre != null && isEvac(pre.getCategory()));
        if (evac && !canManageEvac(me)) { finish(); return; }
        final boolean scoped = me.isServiceAdmin() && !evac;   // a service admin only ever edits their own station
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
        ivPhoto = findViewById(R.id.iv_photo);
        btnPhoto = findViewById(R.id.btn_photo);
        btnPhoto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { choosePhoto(); }
        });
        if (evac) {
            actvType.setText("Evacuation", false);
            actvType.setEnabled(false);
            tilName.setHint("Evacuation site name");
            tilPhone.setHint("Contact number (optional)");
            cbAvail.setText("Open (accepting evacuees)");
            ((Button) findViewById(R.id.btn_save)).setText("Save evacuation site");
            ((Button) findViewById(R.id.btn_delete)).setText("Delete evacuation site");
        }

        editing = scoped ? DataRepository.findStation(me.getStationName()) : pre;
        if (scoped) {
            actvType.setText(me.getService(), false);
            actvType.setEnabled(false);
        }
        if (editing != null) {
            ((TextView) findViewById(R.id.tv_title)).setText(evac ? "Edit evacuation site" : scoped ? "My station" : "Edit station");
            if (!editing.getPhotoUri().isEmpty()) {
                try { ivPhoto.setImageURI(Uri.parse(editing.getPhotoUri())); } catch (Exception ignored) { }
                if (ivPhoto.getDrawable() != null) { ivPhoto.setVisibility(View.VISIBLE); btnPhoto.setText("Change the picture"); }
            }
            etName.setText(editing.getName());
            String t = editing.getCategory();
            if (!evac) actvType.setText("Medical".equals(t) || "Hospital".equals(t) ? "Medical" : "Rescue".equals(t) ? "DRRMO" : t, false);
            etPhone.setText(editing.getPhone());
            etAddress.setText(editing.getAddress());
            address = editing.getAddress();
            lat = editing.getLat();
            lon = editing.getLon();
            cbAvail.setChecked(editing.isAvailable());
            findViewById(R.id.btn_delete).setVisibility(scoped ? View.GONE : View.VISIBLE);   // evacuation sites: the DRRMO admin may delete
        } else {
            ((TextView) findViewById(R.id.tv_title)).setText(evac ? "Add evacuation site" : scoped ? "Set up my station" : "Add station");
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
                String type = evac ? "Evacuation" : actvType.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                boolean ok = true;
                if (name.isEmpty()) { tilName.setError(getString(R.string.err_required)); ok = false; }
                Station same = DataRepository.findStationIgnoreCase(name);
                if (same != null && same != editing) { tilName.setError("A station with this name already exists"); ok = false; }
                boolean known = false;
                for (String t : TYPES) if (t.equals(type)) known = true;
                if (evac) known = true;
                if (!known) { tilType.setError("Choose a type"); ok = false; }
                if (!phone.isEmpty() && !Validator.isValidPhoneLoose(phone)) { tilPhone.setError("Use digits, + or - only"); ok = false; }
                if (Double.isNaN(lat) || Double.isNaN(lon)) { tvLocation.setText(evac ? "Set the site's location on the map" : "Set the station's location on the map"); ok = false; }
                if (!ok) return;
                String addr = etAddress.getText().toString().trim();
                if (addr.isEmpty()) addr = GeoUtils.formatCoords(lat, lon);
                if (editing == null) {
                    Station s = new Station(name, type, addr, lat, lon, cbAvail.isChecked(), phone);
                    DataRepository.addStation(s);
                    if (newPhoto != null) StationPhotos.save(StationEditActivity.this, s, newPhoto);
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
                    if (newPhoto != null) StationPhotos.save(StationEditActivity.this, editing, newPhoto);
                    else if (photoRemoved) StationPhotos.clear(StationEditActivity.this, editing);
                }
                Toast.makeText(StationEditActivity.this, evac ? "Evacuation site saved." : "Station saved.", Toast.LENGTH_SHORT).show();
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
