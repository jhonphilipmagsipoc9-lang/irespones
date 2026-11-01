package com.rescuelink.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.emergency.EmergencyActions;
import com.rescuelink.app.emergency.EmergencyActions.Svc;
import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.Validator;

/** Sign in with a mobile number (citizens) or staff number (responders, admins) and a password. Four emergency buttons work without an account. */
public class LoginActivity extends AppCompatActivity {
    private static final String PREFS = "rescuelink_prefs";
    private static final String KEY_USER = "last_username";

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private EditText etUsername;
    private EditText etPassword;
    private MaterialButton btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        tilUsername = findViewById(R.id.til_username);
        tilPassword = findViewById(R.id.til_password);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);

        final SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_USER, null);
        if (saved != null) etUsername.setText(saved);
        if (getIntent().getBooleanExtra("expired", false)) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { attemptLogin(prefs); }
        });
        findViewById(R.id.btn_create).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(LoginActivity.this, RegisterActivity.class)); }
        });
        findViewById(R.id.btn_forgot).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new MaterialAlertDialogBuilder(LoginActivity.this)
                        .setTitle(R.string.forgot_title)
                        .setMessage(R.string.forgot_msg)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });

        quick(R.id.btn_q_police, Svc.POLICE);
        quick(R.id.btn_q_fire, Svc.FIRE);
        quick(R.id.btn_q_drrmo, Svc.DRRMO);
        quick(R.id.btn_q_medical, Svc.MEDICAL);
    }

    private void quick(int id, final Svc s) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { EmergencyActions.start(LoginActivity.this, s); }
        });
    }

    private void attemptLogin(final SharedPreferences prefs) {
        tilUsername.setError(null);
        tilPassword.setError(null);
        final String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        boolean ok = true;
        if (Validator.isBlank(username)) { tilUsername.setError(getString(R.string.err_required)); ok = false; }
        if (Validator.isBlank(password)) { tilPassword.setError(getString(R.string.err_required)); ok = false; }
        if (!ok) return;

        User user = DataRepository.findUserByLogin(username);
        if (user == null || !user.passwordMatches(password)) {
            tilPassword.setError("Wrong number or password.");
            return;
        }
        if (!user.isActive()) {
            tilPassword.setError("This account is disabled. Ask the Command Center.");
            return;
        }
        prefs.edit().putString(KEY_USER, user.getUsername()).apply();
        Config.saveSession(this, user.getUsername());
        SessionManager.setCurrentUser(user);
        openHomeFor(this, user);
    }

    public static void openHomeFor(Context from, User user) {
        Class<?> target;
        switch (user.getRole()) {
            case User.ROLE_ADMIN: target = AdminActivity.class; break;
            case User.ROLE_RESPONDER: target = ResponderActivity.class; break;
            default: target = CitizenActivity.class;
        }
        Intent i = new Intent(from, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(i);
        if (from instanceof android.app.Activity) {
            ((android.app.Activity) from).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }
}
