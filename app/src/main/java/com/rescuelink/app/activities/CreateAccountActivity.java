package com.rescuelink.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;
import android.widget.Toast;
import com.rescuelink.app.net.Net;
import com.rescuelink.app.utils.OtpDialog;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.Validator;

import java.util.ArrayList;
import java.util.List;

/**
 * Create an account.
 * Command Center: service admins only (Police, Fire, DRRMO or Medical). It does not create responders or citizens.
 * A service admin: responders of their own service only.
 * A responder gets a station of their service: an existing one, or a new one with its map location.
 */
public class CreateAccountActivity extends AppCompatActivity {
    /** Responders must verify their mobile number with a code. Admins use a special number and need none. */
    private String verifiedMobile = "";
    private String pendingMobile;
    private Runnable pendingDone;
    private final androidx.activity.result.ActivityResultLauncher<String> smsPermission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            new androidx.activity.result.ActivityResultCallback<Boolean>() {
                @Override public void onActivityResult(Boolean granted) {
                    if (pendingMobile != null && pendingDone != null) {
                        String m = pendingMobile; Runnable r = pendingDone;
                        pendingMobile = null; pendingDone = null;
                        OtpDialog.show(CreateAccountActivity.this, m, r);
                    }
                }
            });

    private static final String COMMAND_CENTER = "Command Center (all services)";   // no longer offered; kept for safety

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.requireRole(this, User.ROLE_ADMIN)) return;
        setContentView(R.layout.activity_create_account);
        final User me = SessionManager.getCurrentUser();
        final com.rescuelink.app.utils.PhotoField photoField = new com.rescuelink.app.utils.PhotoField(this, this,
                findViewById(R.id.photo_picker), (android.widget.ImageView) findViewById(R.id.iv_photo));
        final boolean scoped = me.isServiceAdmin();

        final TextInputLayout tilRole = findViewById(R.id.til_role);
        final TextInputLayout tilService = findViewById(R.id.til_service);
        final TextInputLayout tilStation = findViewById(R.id.til_station);
        final TextInputLayout tilName = findViewById(R.id.til_name);
        final TextInputLayout tilContact = findViewById(R.id.til_contact);
        final TextInputLayout tilUser = findViewById(R.id.til_username);
        final TextInputLayout tilPass = findViewById(R.id.til_password);
        final AutoCompleteTextView actvRole = findViewById(R.id.actv_role);
        final AutoCompleteTextView actvService = findViewById(R.id.actv_service);
        final AutoCompleteTextView actvStation = findViewById(R.id.actv_station);
        final View boxStation = findViewById(R.id.box_station);
        final TextView tvHint = findViewById(R.id.tv_station_hint);
        final EditText etName = findViewById(R.id.et_name);
        final EditText etContact = findViewById(R.id.et_contact);
        final EditText etUser = findViewById(R.id.et_username);
        final EditText etPass = findViewById(R.id.et_password);
        final MaterialButton btn = findViewById(R.id.btn_create);
        etUser.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (!Validator.normalizeNumber(e.toString()).equals(verifiedMobile)) {
                    verifiedMobile = "";
                    if (User.ROLE_RESPONDER.equals(actvRole.getText().toString().trim())) tilUser.setHelperText("11 digits starting with 09. A code is sent to verify it.");
                }
            }
        });

        actvRole.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                // Command Center creates service admins only; each service admin creates that service's responders.
                new String[]{scoped ? User.ROLE_RESPONDER : User.ROLE_ADMIN}));
        actvRole.setText(scoped ? User.ROLE_RESPONDER : User.ROLE_ADMIN, false);
        if (scoped) {
            actvService.setText(me.getService(), false);
            actvService.setEnabled(false);
            tilService.setHint("Service (your own)");
        }

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                String role = actvRole.getText().toString().trim();
                boolean resp = User.ROLE_RESPONDER.equals(role);
                boolean adm = User.ROLE_ADMIN.equals(role);
                // Admins sign in with a special number (no code). Responders sign in with their mobile number, verified by a code.
                tilUser.setHint(resp ? "Mobile number (sign-in)" : "Special number (sign-in)");
                tilUser.setHelperText(resp ? "11 digits starting with 09. A code is sent to verify it."
                        : "4 to 10 digits. Leave blank to generate one. No code needed.");
                etUser.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(resp ? 11 : 10)});
                tilContact.setVisibility(resp ? View.GONE : View.VISIBLE);
                tilService.setVisibility(resp || adm ? View.VISIBLE : View.GONE);
                boxStation.setVisibility(resp ? View.VISIBLE : View.GONE);
                if (!scoped) {
                    String[] opts;
                    if (adm) {
                        opts = User.ADMIN_SERVICES;
                    } else {
                        opts = User.SERVICES;
                    }
                    actvService.setAdapter(new ArrayAdapter<>(CreateAccountActivity.this,
                            android.R.layout.simple_dropdown_item_1line, opts));
                    String cur = actvService.getText().toString().trim();
                    boolean valid = false;
                    for (String o : opts) if (o.equals(cur)) valid = true;
                    if (!valid) actvService.setText("", false);   // Medical is not an admin service
                }
                if (resp) {
                    TextView hint = findViewById(R.id.tv_station_hint);
                    if (scoped) {
                        actvStation.setEnabled(false);
                        actvStation.setText(me.getStationName(), false);
                        hint.setText(me.getStationName().isEmpty()
                                ? "Set up your station under Manage > My station first. New responders join it."
                                : "New responders join your station automatically.");
                    } else {
                        List<String> names = new ArrayList<>();
                        for (Station st : DataRepository.getStationsForService(actvService.getText().toString().trim())) names.add(st.getName());
                        actvStation.setAdapter(new ArrayAdapter<>(CreateAccountActivity.this,
                                android.R.layout.simple_dropdown_item_1line, names));
                        hint.setText(names.isEmpty()
                                ? "No " + actvService.getText().toString().trim() + " station yet. Add one under Manage > Stations, or the service admin sets up their own."
                                : "Choose the station this responder belongs to.");
                    }
                }
            }
        };
        refresh.run();
        actvRole.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) { refresh.run(); }
        });
        actvService.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                actvStation.setText("");
                refresh.run();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tilRole.setError(null); tilService.setError(null); tilStation.setError(null); tilName.setError(null);
                tilContact.setError(null); tilUser.setError(null); tilPass.setError(null);
                final String role = actvRole.getText().toString().trim();
                final String stationName = actvStation.getText().toString().trim();
                String typedNumber = etUser.getText().toString().trim();
                final String password = etPass.getText().toString();
                String svcText = scoped ? me.getService() : actvService.getText().toString().trim();
                final String service = COMMAND_CENTER.equals(svcText) ? "" : svcText;
                boolean ok = true;
                boolean allowed = scoped ? User.ROLE_RESPONDER.equals(role) : User.ROLE_ADMIN.equals(role);
                if (!allowed) { tilRole.setError(getString(R.string.err_role)); ok = false; }
                if (User.ROLE_RESPONDER.equals(role)) {
                    boolean known = false;
                    for (String t : User.SERVICES) if (t.equals(service)) known = true;
                    if (!known) { tilService.setError("Choose a service"); ok = false; }
                } else if (User.ROLE_ADMIN.equals(role)) {
                    boolean known = false;
                    for (String t : User.ADMIN_SERVICES) if (t.equals(service)) known = true;
                    if (!known) {
                        tilService.setError("Choose Police, Fire, DRRMO or Medical");
                        ok = false;
                    }
                }
                Station existing = scoped ? DataRepository.findStation(me.getStationName())
                        : DataRepository.findStationIgnoreCase(stationName);
                if (User.ROLE_RESPONDER.equals(role) && ok) {
                    if (existing == null) {
                        if (scoped) {
                            new MaterialAlertDialogBuilder(CreateAccountActivity.this)
                                    .setTitle("Set up your station first")
                                    .setMessage("Your responders join your station. Add its name and map location, then come back.")
                                    .setNegativeButton("Later", null)
                                    .setPositiveButton("Set up", new android.content.DialogInterface.OnClickListener() {
                                        @Override public void onClick(android.content.DialogInterface d, int w) {
                                            startActivity(new Intent(CreateAccountActivity.this, StationEditActivity.class));
                                        }
                                    }).show();
                            return;
                        }
                        tilStation.setError("Choose a station");
                        ok = false;
                    } else if (!service.equals(DataRepository.serviceOfCategory(existing.getCategory()))) {
                        tilStation.setError("That is a " + existing.getCategory() + " station, not " + service);
                        ok = false;
                    }
                }
                if (Validator.isBlank(etName.getText().toString())) { tilName.setError(getString(R.string.err_required)); ok = false; }
                final boolean isResp = User.ROLE_RESPONDER.equals(role);
                if (!isResp && !etContact.getText().toString().trim().isEmpty() && !Validator.isValidContact(etContact.getText().toString())) {
                    tilContact.setError(getString(R.string.err_contact)); ok = false;
                }
                if (isResp) {
                    typedNumber = Validator.normalizeNumber(typedNumber);
                    if (!Validator.isValidContact(typedNumber)) { tilUser.setError(getString(R.string.err_contact)); ok = false; }
                } else if (!typedNumber.isEmpty() && !Validator.isValidStaffNumber(typedNumber)) { tilUser.setError("Use 4 to 10 digits"); ok = false; }
                if (password.length() < 6) { tilPass.setError(getString(R.string.err_password_short)); ok = false; }
                if (!ok) return;

                if (!typedNumber.isEmpty() && DataRepository.findUserByLogin(typedNumber) != null) { tilUser.setError("That number already has an account."); return; }
                if (isResp && !typedNumber.equals(verifiedMobile)) {
                    final String m = typedNumber;
                    Runnable done = new Runnable() {
                        @Override public void run() {
                            verifiedMobile = m;
                            tilUser.setHelperText("\u2713 Number verified");
                            Toast.makeText(CreateAccountActivity.this, "Number verified. Tap Create account to finish.", Toast.LENGTH_LONG).show();
                        }
                    };
                    if (!Net.canSendSms(CreateAccountActivity.this) && Net.canUseSim(CreateAccountActivity.this)) {
                        pendingMobile = m; pendingDone = done;
                        smsPermission.launch(android.Manifest.permission.SEND_SMS);
                    } else {
                        OtpDialog.show(CreateAccountActivity.this, m, done);
                    }
                    return;
                }
                final String username = typedNumber.isEmpty() ? DataRepository.generateStaffNumber() : typedNumber;
                String contact = isResp ? username : etContact.getText().toString().trim();
                Station station = null;
                if (User.ROLE_RESPONDER.equals(role)) {
                    station = existing;
                }
                final User created = new User(etName.getText().toString().trim(), 30,
                        station != null ? station.getName()
                                : User.ROLE_ADMIN.equals(role) ? "Command Center" : "Not set", username, password,
                        contact, role);
                if (User.ROLE_RESPONDER.equals(role) || User.ROLE_ADMIN.equals(role)) created.setService(service);
                if (station != null) {
                    created.setStationName(station.getName());
                    created.setPosition(station.getLat(), station.getLon());
                }
                // Accounts live on this phone. Give the sign-in number and password to the person.
                DataRepository.addUser(created);
                if (com.rescuelink.app.net.FirebaseAuth.configured(CreateAccountActivity.this)) {
                    final android.content.Context app = getApplicationContext();
                    final String fPw = password;
                    com.rescuelink.app.net.Cloud.run(new Runnable() {
                        @Override public void run() { com.rescuelink.app.net.FirebaseAuth.signUp(app, username, fPw); }
                    });
                }
                photoField.applyTo(created);        // optional profile picture, shown to everyone
                showCreated(role, service, username, password, station);
            }
        });
    }

    private void showCreated(String role, String service, String username, String password, Station fs) {
                String what = User.ROLE_ADMIN.equals(role)
                        ? (service.isEmpty() ? "Command Center admin" : service + " admin")
                        : User.ROLE_RESPONDER.equals(role) ? service + " responder" : role;
                new MaterialAlertDialogBuilder(CreateAccountActivity.this)
                        .setTitle("Account created")
                        .setMessage("Type: " + what + "\nSign-in number: " + username + "\nPassword: " + password
                                + (fs != null ? "\nStation: " + fs.getName() + " (" + fs.getCategory() + ")" : "")
                                + "\n\nGive these to the person. They can change the password after signing in.")
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) { finish(); }
                        }).show();
    }
}
