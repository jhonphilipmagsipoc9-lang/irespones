package com.rescuelink.app.activities;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.emergency.EmergencyActions;
import com.rescuelink.app.emergency.EmergencyActions.Svc;
import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Cloud;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.net.FirebaseAuth;
import com.rescuelink.app.net.Net;
import com.rescuelink.app.utils.OtpManager;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.Validator;

/**
 * Sign in with a mobile or staff number and password (checked by Firebase Authentication, so it needs internet).
 * Four emergency buttons work without an account.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String PREFS = "rescuelink_prefs";
    private static final String KEY_USER = "last_username";

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private EditText etUsername;
    private EditText etPassword;
    private MaterialButton btnLogin;
    private SharedPreferences prefs;
    /** Runs after the SMS permission answer, so a code is only sent once the phone is allowed to send it. */
    private Runnable afterSmsPermission;

    private final ActivityResultLauncher<String> smsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            new ActivityResultCallback<Boolean>() {
                @Override public void onActivityResult(Boolean granted) {
                    Runnable r = afterSmsPermission;
                    afterSmsPermission = null;
                    if (r != null) r.run();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        tilUsername = findViewById(R.id.til_username);
        tilPassword = findViewById(R.id.til_password);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_USER, null);
        if (saved != null && saved.matches("\\d+")) etUsername.setText(saved);
        if (getIntent().getBooleanExtra("expired", false)) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { attemptLogin(); }
        });
        findViewById(R.id.btn_create).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(LoginActivity.this, RegisterActivity.class)); }
        });
        findViewById(R.id.btn_forgot).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startForgotPassword(); }
        });

        quick(R.id.btn_q_police, Svc.POLICE);
        quick(R.id.btn_q_fire, Svc.FIRE);
        quick(R.id.btn_q_drrmo, Svc.DRRMO);
        quick(R.id.btn_q_medical, Svc.MEDICAL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.rescuelink.app.utils.LocationGate.autoEnable(this);     // location turns on by itself when the app opens
    }

    private void quick(int id, final Svc s) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { EmergencyActions.start(LoginActivity.this, s); }
        });
    }

    // ------------------------------------------------------------------ number + password
    private void attemptLogin() {
        tilUsername.setError(null);
        tilPassword.setError(null);
        String typed = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        boolean ok = true;
        if (Validator.isBlank(typed)) { tilUsername.setError(getString(R.string.err_required)); ok = false; }
        if (Validator.isBlank(password)) { tilPassword.setError(getString(R.string.err_required)); ok = false; }
        if (!ok) return;

        String number = Validator.normalizeNumber(typed);
        User user = DataRepository.findUserByLogin(number);
        if (user == null || user.isRemote()) { failed(number); tilPassword.setError("Wrong number or password."); return; }
        if (!user.hasPassword()) {
            tilPassword.setError("This account signs in with Google or a phone code. Use the buttons below.");
            return;
        }
        if (!user.passwordMatches(password)) { failed(number); tilPassword.setError("Wrong number or password."); return; }
        if (!user.isActive()) { tilPassword.setError("This account is disabled. Ask the Command Center."); return; }
        onlineCheck(user, number, password);
    }

    /** The password is only accepted once Firebase Authentication accepts it too, so signing in needs internet. */
    private void onlineCheck(final User user, final String number, final String password) {
        if (!FirebaseAuth.configured(this)) {
            tilPassword.setError("Online sign-in is not set up. Add the Firebase Web API key (see FIREBASE_SETUP.md).");
            return;
        }
        if (!Net.hasInternet(this)) {
            tilPassword.setError(FirebaseAuth.explain("NETWORK"));
            return;
        }
        setLoading(true);
        final Context app = getApplicationContext();
        Cloud.run(new Runnable() {
            @Override public void run() {
                final FirebaseAuth.Result r = FirebaseAuth.signInOrCreate(app, user.getUsername(), password);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isFinishing() && !isDestroyed()) setLoading(false);
                        if (isFinishing() || isDestroyed()) return;
                        if (r.ok) { clearFails(number); finishSignIn(user); return; }
                        if (r.wrongOrMissing() || r.error.startsWith("EMAIL_EXISTS")) failed(number);
                        tilPassword.setError(FirebaseAuth.explain(r.error));
                    }
                });
            }
        });
    }

    /** Wrong passwords are never locked out; the person can simply try again. */
    private void failed(String number) { }

    private void clearFails(String number) { }

    /** Shows or hides the loading screen and the button while the sign-in is checked online. */
    private void setLoading(boolean on) {
        View overlay = findViewById(R.id.login_loading);
        if (overlay != null) overlay.setVisibility(on ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!on);
    }

    private void finishSignIn(User user) {
        if (user.getUsername().matches("\\d+")) prefs.edit().putString(KEY_USER, user.getUsername()).apply();
        Config.saveSession(this, user.getUsername());
        SessionManager.setCurrentUser(user);
        openHomeFor(this, user);
    }

    // ------------------------------------------------------------------ forgot password
    private void startForgotPassword() {
        final EditText num = phoneField();
        String typed = Validator.normalizeNumber(etUsername.getText().toString());
        if (Validator.isValidContact(typed)) num.setText(typed);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset your password")
                .setMessage("Enter your mobile number. We will send a 6-digit code to the number saved on the account.")
                .setView(boxed(num))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send code", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        resetFor(Validator.normalizeNumber(num.getText().toString().trim()));
                    }
                })
                .show();
    }

    private void resetFor(final String typed) {
        final User u = Validator.isBlank(typed) ? null : DataRepository.findUserByLogin(typed);
        if (u == null || u.isRemote()) { noAccount(); return; }
        if (!u.hasPassword()) {
            new MaterialAlertDialogBuilder(this).setTitle("No password on this account")
                    .setMessage("This account signs in with Google or a phone code, so there is no password to reset.")
                    .setPositiveButton(R.string.ok, null).show();
            return;
        }
        final String mobile = u.getContact();
        if (!Validator.isValidContact(mobile)) {
            new MaterialAlertDialogBuilder(this).setTitle("Cannot reset here")
                    .setMessage("This account has no mobile number saved, so a code cannot be sent.")
                    .setPositiveButton(R.string.ok, null).show();
            return;
        }
        // The code always goes to the number saved on the account, never to a number typed here.
        sendCode(mobile, new Runnable() {
            @Override public void run() { askNewPassword(u); }
        });
    }

    private void noAccount() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cannot reset here")
                .setMessage("No account was found for that number. Please create an account first.")
                .setNegativeButton(R.string.ok, null)
                .setPositiveButton("Create account", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
                    }
                })
                .show();
    }

    private void askNewPassword(final User u) {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad / 2, pad, 0);
        final EditText p1 = new EditText(this), p2 = new EditText(this);
        p1.setHint("New password (at least 6 characters)");
        p2.setHint("Confirm new password");
        p1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        p2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        col.addView(p1);
        col.addView(p2);
        final AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle("Choose a new password")
                .setView(col)
                .setCancelable(false)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String a = p1.getText().toString(), b = p2.getText().toString();
                if (a.length() < 6) { p1.setError("At least 6 characters"); return; }
                if (!a.equals(b)) { p2.setError("Passwords do not match"); return; }
                if (!Net.hasInternet(LoginActivity.this)) {
                    Toast.makeText(LoginActivity.this, FirebaseAuth.explain("NETWORK"), Toast.LENGTH_LONG).show();
                    return;
                }
                v.setEnabled(false);
                final String newPw = a;
                final Context app = getApplicationContext();
                final View btn = v;
                Cloud.run(new Runnable() {
                    @Override public void run() {
                        // The SMS code proved the number, so the number gets a fresh Firebase account with the new password.
                        FirebaseAuth.bumpGeneration(app, u.getUsername());
                        final FirebaseAuth.Result r = FirebaseAuth.signUp(app, u.getUsername(), newPw);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                btn.setEnabled(true);
                                if (!r.ok) {
                                    Toast.makeText(LoginActivity.this, FirebaseAuth.explain(r.error), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                u.resetPassword(newPw);
                                DataRepository.log("Password reset: " + u.getFullName());
                                DataRepository.save();
                                clearFails(u.getUsername());
                                d.dismiss();
                                etUsername.setText(u.getUsername().matches("\\d+") ? u.getUsername() : "");
                                etPassword.setText("");
                                Toast.makeText(LoginActivity.this, "Password changed. Sign in with your new password.", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ one-time code by SMS
    /**
     * Sends a 6-digit code to {@code mobile} and runs {@code onVerified} when the right code is typed.
     * The code is never shown on screen here: if it were, anyone could type another person's number and get in.
     */
    private void sendCode(final String mobile, final Runnable onVerified) {
        if (!Net.canUseSim(this)) {
            Toast.makeText(this, "This phone needs a SIM that can send SMS to use a code. Use your password instead.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!Net.canSendSms(this)) {
            afterSmsPermission = new Runnable() {
                @Override public void run() {
                    if (Net.canSendSms(LoginActivity.this)) issueAndAsk(mobile, onVerified);
                    else Toast.makeText(LoginActivity.this, "Allow SMS so the code can be sent, or use your password.", Toast.LENGTH_LONG).show();
                }
            };
            smsPermission.launch(Manifest.permission.SEND_SMS);
            return;
        }
        issueAndAsk(mobile, onVerified);
    }

    private boolean issue(String mobile) {
        OtpManager.Sent how = OtpManager.issue(this, mobile, null, false);
        if (how == OtpManager.Sent.SMS) {
            Toast.makeText(this, "Code sent to " + mobile + ".", Toast.LENGTH_SHORT).show();
            return true;
        }
        Toast.makeText(this, "Could not send the code. Check your SIM and load, or use your password.", Toast.LENGTH_LONG).show();
        return false;
    }

    private void issueAndAsk(final String mobile, final Runnable onVerified) {
        if (!issue(mobile)) return;
        final EditText code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setHint("6-digit code");
        code.setMaxLines(1);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        final AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle("Enter the code")
                .setMessage("We sent a code to " + mobile + ".")
                .setView(boxed(code))
                .setCancelable(false)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface x, int w) { OtpManager.clear(); }
                })
                .setNeutralButton("Resend", null)
                .setPositiveButton("Verify", null)
                .create();
        d.show();
        final long[] lastSent = {System.currentTimeMillis()};
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (System.currentTimeMillis() - lastSent[0] < OtpManager.RESEND_MS) {
                    Toast.makeText(LoginActivity.this, "Wait a moment before asking for another code.", Toast.LENGTH_SHORT).show();
                    return;
                }
                lastSent[0] = System.currentTimeMillis();
                issue(mobile);
            }
        });
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                OtpManager.Result r = OtpManager.check(mobile, code.getText().toString());
                if (r == OtpManager.Result.OK) { d.dismiss(); onVerified.run(); }
                else if (r == OtpManager.Result.WRONG) code.setError("Wrong code");
                else code.setError(r == OtpManager.Result.EXPIRED ? "Code expired. Tap Resend." : "Too many tries. Tap Resend.");
            }
        });
    }

    // ------------------------------------------------------------------ small helpers
    private EditText phoneField() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        e.setHint("Mobile number, like 09171234567");
        e.setSingleLine(true);
        return e;
    }

    private FrameLayout boxed(EditText e) {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        return box;
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
