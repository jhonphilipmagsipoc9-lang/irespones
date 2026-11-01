package com.rescuelink.app.activities;

import com.rescuelink.app.utils.ThemeColors;import android.view.View;
import android.content.DialogInterface;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageView;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LocationHelper;
import android.widget.Toast;
import android.content.Intent;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.StatusEvent;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;
import com.rescuelink.app.utils.Validator;

/** Report form: map pin, photo, save draft, review and submit. */
public class ReportActivity extends AppCompatActivity {
    public static final String EXTRA_STATION = "station";
    private TextInputLayout tilCategory, tilTitle, tilDescription, tilLocation, tilAffected;
    private EditText etTitle, etDescription, etLocation, etLandmark, etAffected;
    private AutoCompleteTextView actvCategory, actvSeverity;
    private Incident editing;
    private double pinLat = Double.NaN, pinLon = Double.NaN;
    private String photoUri;
    private TextView tvPin;
    private String chosenStation;
    private TextView tvSendTo;

    private final ActivityResultLauncher<Intent> mapLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), new androidx.activity.result.ActivityResultCallback<androidx.activity.result.ActivityResult>() {
                @Override
                public void onActivityResult(androidx.activity.result.ActivityResult r) {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                Intent d = r.getData();
                pinLat = d.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN);
                pinLon = d.getDoubleExtra(MapPickerActivity.EXTRA_LON, Double.NaN);
                String addr = d.getStringExtra(MapPickerActivity.EXTRA_ADDRESS);
                if (addr != null && !addr.isEmpty()) etLocation.setText(addr);
                else if (Validator.isBlank(etLocation.getText().toString())) {
                    etLocation.setText("Pinned location (" + GeoUtils.formatCoords(pinLat, pinLon) + ")");
                }
                updatePinStatus();
            }
            });

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), new androidx.activity.result.ActivityResultCallback<java.util.Map<java.lang.String, java.lang.Boolean>>() {
                @Override
                public void onActivityResult(java.util.Map<java.lang.String, java.lang.Boolean> r) {
                    useMyLocation();
                }
            });

    private final ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), new androidx.activity.result.ActivityResultCallback<android.net.Uri>() {
                @Override
                public void onActivityResult(android.net.Uri uri) {
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) { }
                photoUri = uri.toString();
                showPhoto();
            }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_report);

        tilCategory = findViewById(R.id.til_category);
        tilTitle = findViewById(R.id.til_title);
        tilDescription = findViewById(R.id.til_description);
        tilLocation = findViewById(R.id.til_location);
        tilAffected = findViewById(R.id.til_affected);
        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        etLocation = findViewById(R.id.et_location);
        etLandmark = findViewById(R.id.et_landmark);
        etAffected = findViewById(R.id.et_affected);
        actvCategory = findViewById(R.id.actv_category);
        actvSeverity = findViewById(R.id.actv_severity);

        actvCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                DataRepository.getCategoryNames()));
        actvSeverity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                new String[]{"Low", "Moderate", "High", "Critical"}));
        actvSeverity.setText("Moderate", false);

        ((TextView) findViewById(R.id.tv_datetime))
                .setText("Date and time: " + UiUtils.formatTime(System.currentTimeMillis()));
        tvPin = findViewById(R.id.tv_pin_status);
        double startLat = getIntent().getDoubleExtra("lat", Double.NaN);
        double startLon = getIntent().getDoubleExtra("lon", Double.NaN);
        if (!Double.isNaN(startLat) && !Double.isNaN(startLon)) {
            pinLat = startLat;
            pinLon = startLon;
            etLocation.setText("Pinned location (" + GeoUtils.formatCoords(pinLat, pinLon) + ")");
        }

        String ref = getIntent().getStringExtra("ref");
        if (ref != null) {
            Incident found = DataRepository.findIncident(ref);
            if (found != null && found.getStatus() == IncidentStatus.DRAFT) {
                editing = found;
                prefill(found);
            }
        }
        String category = getIntent().getStringExtra("category");
        if (editing == null && category != null) actvCategory.setText(category, false);
        String station = getIntent().getStringExtra(EXTRA_STATION);
        if (station != null) chosenStation = station;
        if (editing != null && editing.getAssignedStation() != null) chosenStation = editing.getAssignedStation();
        tvSendTo = new TextView(this);
        tvSendTo.setTextColor(ThemeColors.color(this, R.color.navy));
        tvSendTo.setTextSize(14);
        tvSendTo.setBackgroundColor(ThemeColors.color(this, R.color.pale_blue));
        int pad = UiUtils.dp(this, 12);
        tvSendTo.setPadding(pad, pad, pad, pad);
        android.view.ViewGroup form = (android.view.ViewGroup) tilCategory.getParent();
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiUtils.dp(this, 12);
        form.addView(tvSendTo, form.indexOfChild(tilCategory) + 1, lp);
        actvCategory.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                chosenStation = null; // the old station may not fit the new category
                updateSendTo();
            }
        });
        updateSendTo();

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {confirmExit();
}
});
        findViewById(R.id.btn_cancel_report).setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {confirmExit();
}
});
        findViewById(R.id.photo_picker).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
            try {
                photoPicker.launch(new String[]{"image/*"});
            } catch (ActivityNotFoundException e) {
                Toast.makeText(ReportActivity.this, R.string.err_photo, Toast.LENGTH_SHORT).show();
            }
        }
        });
        findViewById(R.id.btn_pick_map).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
            Intent i = new Intent(ReportActivity.this, MapPickerActivity.class);
            if (!Double.isNaN(pinLat)) {
                i.putExtra(MapPickerActivity.EXTRA_LAT, pinLat);
                i.putExtra(MapPickerActivity.EXTRA_LON, pinLon);
            }
            mapLauncher.launch(i);
        }
        });
        findViewById(R.id.btn_use_my_loc).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                useMyLocation();
            }
        });
        findViewById(R.id.btn_save_draft).setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {saveDraft();
}
});
        findViewById(R.id.btn_review).setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {reviewAndSubmit();
}
});

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { confirmExit(); }
        });
    }

    private void useMyLocation() {
        if (!LocationHelper.hasPermission(this)) {
            permLauncher.launch(LocationHelper.PERMISSIONS);
            return;
        }
        if (!LocationHelper.refresh(this)) {
            Toast.makeText(this, "Could not find your location. Turn on location or pick a spot on the map.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        pinLat = GeoUtils.myLat();
        pinLon = GeoUtils.myLon();
        if (Validator.isBlank(etLocation.getText().toString())) {
            etLocation.setText("My current location (" + GeoUtils.formatCoords(pinLat, pinLon) + ")");
        }
        updatePinStatus();
    }

    private void updateSendTo() {
        tvSendTo.setText(chosenStation == null
                ? "Station: you will choose where to send this when you review the report."
                : "Sending to: " + chosenStation);
    }

    private void updatePinStatus() {
        tvPin.setText(Double.isNaN(pinLat) ? ""
                : "Pinned on map: " + GeoUtils.formatCoords(pinLat, pinLon));
    }

    private void showPhoto() {
        ImageView iv = findViewById(R.id.iv_report_photo);
        iv.setPadding(0, 0, 0, 0);
        try {
            iv.setImageURI(Uri.parse(photoUri));
        } catch (Exception ignored) { }
        ((TextView) findViewById(R.id.tv_photo_label)).setText("Change photo");
    }

    private void prefill(Incident i) {
        if (i.hasPosition()) { pinLat = i.getLat(); pinLon = i.getLon(); }
        if (i.getPhotoUri() != null) photoUri = i.getPhotoUri();
        actvCategory.setText(i.getCategory(), false);
        etTitle.setText(i.getTitle());
        etDescription.setText(i.getDescription());
        etLocation.setText(i.getLocation());
        etLandmark.setText(i.getLandmark());
        etAffected.setText(String.valueOf(i.getAffected()));
        actvSeverity.setText(i.getSeverity(), false);
        updatePinStatus();
        if (photoUri != null) showPhoto();
    }

    private boolean hasContent() {
        return !Validator.isBlank(etTitle.getText().toString())
                || !Validator.isBlank(etDescription.getText().toString())
                || !Validator.isBlank(etLocation.getText().toString())
                || !Validator.isBlank(etLandmark.getText().toString());
    }

    private void confirmExit() {
        if (!hasContent()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Leave this report?")
                .setMessage("Unsaved changes will be lost. You can also save it as a draft first.")
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Discard", new DialogInterface.OnClickListener() {
@Override public void onClick(DialogInterface d, int w) {finish();
}
})
                .show();
    }

    private boolean requireField(TextInputLayout til, EditText et) {
        til.setError(null);
        if (Validator.isBlank(et.getText().toString())) {
            til.setError(getString(R.string.err_required));
            return false;
        }
        return true;
    }

    private boolean validateForSubmit() {
        boolean ok = true;
        tilCategory.setError(null);
        if (Validator.isBlank(actvCategory.getText().toString())) {
            tilCategory.setError(getString(R.string.err_category));
            ok = false;
        }
        ok &= requireField(tilTitle, etTitle);
        tilDescription.setError(null);
        if (etDescription.getText().toString().trim().length() < 10) {
            tilDescription.setError(getString(R.string.err_description));
            ok = false;
        }
        ok &= requireField(tilLocation, etLocation);
        tilAffected.setError(null);
        if (!Validator.isValidCount(etAffected.getText().toString())) {
            tilAffected.setError(getString(R.string.err_count));
            ok = false;
        }
        return ok;
    }

    /** Copies the form into a new or existing incident. */
    private Incident fillIncident() {
        Incident t = editing != null ? editing
                : new Incident(DataRepository.nextDraftReference(), SessionManager.username());
        t.setCategory(actvCategory.getText().toString().trim());
        t.setTitle(etTitle.getText().toString().trim());
        t.setDescription(etDescription.getText().toString().trim());
        t.setLocation(etLocation.getText().toString().trim());
        t.setLandmark(etLandmark.getText().toString().trim());
        t.setSeverity(actvSeverity.getText().toString().trim());
        int affected = 1;
        try {
            affected = Integer.parseInt(etAffected.getText().toString().trim());
        } catch (NumberFormatException ignored) { }
        t.setAffected(affected);
        if (!Double.isNaN(pinLat)) t.setPosition(pinLat, pinLon);
        t.setPhotoUri(photoUri);
        if (chosenStation != null) t.setAssignedStation(chosenStation);
        return t;
    }

    private void saveDraft() {
        tilCategory.setError(null);
        if (Validator.isBlank(actvCategory.getText().toString())) {
            tilCategory.setError(getString(R.string.err_category));
            return;
        }
        boolean isNew = editing == null;
        Incident t = fillIncident();
        t.setStatus(IncidentStatus.DRAFT);
        if (isNew) DataRepository.addIncident(t);
        Toast.makeText(this, "Draft saved.", Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Step 2: after all details are filled in, the citizen picks which station receives the report. */
    private void reviewAndSubmit() {
        if (!validateForSubmit()) return;
        final String category = actvCategory.getText().toString().trim();
        if (chosenStation != null && DataRepository.findStation(chosenStation) != null) {
            showReview(); // already chosen on the map before filling in the form
            return;
        }
        final double lat = Double.isNaN(pinLat) ? GeoUtils.myLat() : pinLat;
        final double lon = Double.isNaN(pinLon) ? GeoUtils.myLon() : pinLon;
        final java.util.List<com.rescuelink.app.models.Station> options =
                DataRepository.stationsForCategory(category, lat, lon);
        if (options.isEmpty()) {
            chosenStation = null;
            showReview();
            return;
        }
        String[] labels = new String[options.size()];
        int preselect = 0;
        for (int k = 0; k < options.size(); k++) {
            com.rescuelink.app.models.Station st = options.get(k);
            labels[k] = st.getName() + "\n" + st.getCategory() + " \u00B7 "
                    + GeoUtils.formatDistance(GeoUtils.distanceKm(lat, lon, st.getLat(), st.getLon())) + " away";
            if (st.getName().equals(chosenStation)) preselect = k;
        }
        final int[] picked = {preselect};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Send " + category + " report to which station?")
                .setSingleChoiceItems(labels, preselect, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { picked[0] = which; }
                })
                .setNegativeButton("Back", null)
                .setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        chosenStation = options.get(picked[0]).getName();
                        updateSendTo();
                        showReview();
                    }
                })
                .show();
    }

    private void showReview() {
        String landmark = etLandmark.getText().toString().trim();
        String summary = "Category: " + actvCategory.getText()
                + (chosenStation == null ? "" : "\nSend to: " + chosenStation)
                + "\nTitle: " + etTitle.getText().toString().trim()
                + "\nLocation: " + etLocation.getText().toString().trim()
                + (landmark.isEmpty() ? "" : "\nLandmark: " + landmark)
                + "\nAffected people: " + etAffected.getText().toString().trim()
                + "\nSeverity: " + actvSeverity.getText()
                + (Double.isNaN(pinLat) ? "\n\nNo map pin set. Responders will rely on the address you typed."
                : "\nMap pin: " + GeoUtils.formatCoords(pinLat, pinLon));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Review your report")
                .setMessage(summary)
                .setNegativeButton("Edit", null)
                .setPositiveButton("Submit", new DialogInterface.OnClickListener() {
@Override public void onClick(DialogInterface d, int w) {submit();
}
})
                .show();
    }

    private void submit() {
        boolean isNew = editing == null;
        Incident t = fillIncident();
        final String ref = DataRepository.nextReference();
        t.setReference(ref);
        long now = System.currentTimeMillis();
        t.setCreatedAt(now);
        t.setStatus(IncidentStatus.SUBMITTED);
        t.getHistory().clear();
        t.addEvent(new StatusEvent(IncidentStatus.SUBMITTED, now, "Report submitted by citizen"
                + (chosenStation == null ? "." : " and sent to " + chosenStation + ".")));
        if (chosenStation != null) t.setAssignedStation(chosenStation);
        if (isNew) DataRepository.addIncident(t);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Report submitted")
                .setMessage("Reference number: " + ref
                        + "\n\nThe operations desk and the station you chose have received your report. You can follow its progress "
                        + "under Reports. If life is in danger, call your nearest station.")
                .setCancelable(false)
                .setPositiveButton("View report", new DialogInterface.OnClickListener() {
@Override public void onClick(DialogInterface d, int w) {
                    Intent i = new Intent(ReportActivity.this, IncidentDetailActivity.class);
                    i.putExtra("ref", ref);
                    startActivity(i);
                    finish();
                
}
})
                .show();
    }
}
