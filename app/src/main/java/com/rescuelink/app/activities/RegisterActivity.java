package com.rescuelink.app.activities;

import com.rescuelink.app.utils.ThemeColors;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.rescuelink.app.utils.FaceVerifier;

import java.io.File;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Net;
import com.rescuelink.app.utils.GoogleAuth;
import com.rescuelink.app.utils.OtpManager;
import com.rescuelink.app.utils.Validator;

/**
 * Citizens create their own account here. Responder and admin accounts are created by the Command Center
 * (Manage > Create account). Accounts are saved on this phone.
 */
public class RegisterActivity extends AppCompatActivity {
    /** Set when someone chose Continue with Google and has no account yet. */
    public static final String EXTRA_GOOGLE_SUB = "google_sub";
    public static final String EXTRA_GOOGLE_EMAIL = "google_email";
    public static final String EXTRA_GOOGLE_NAME = "google_name";
    /** Set when someone chose Continue with phone number and the number has no account yet. */
    public static final String EXTRA_MOBILE = "prefill_mobile";

    /** Google sign-up: no password is asked, the account signs in with Google. */
    private String googleSub = "", googleEmail = "";
    private TextInputLayout tilName, tilAge, tilAddress, tilPassword, tilConfirm, tilContact;
    private EditText etName, etAge, etAddress, etPassword, etConfirm, etContact;
    private MaterialButton btnRegister;
    private TextView tvIdStatus, tvFaceStatus;
    private ImageView ivIdThumb, ivFaceThumb;
    private com.rescuelink.app.utils.PhotoField photoField;
    private File idFile, faceFile;
    private boolean idOk, faceOk;
    private String pendingMobile, pendingPassword;
    /** The number whose code was entered correctly. The account is created only when Register is tapped afterwards. */
    private String verifiedMobile = "";
    private final ActivityResultLauncher<String> smsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            new androidx.activity.result.ActivityResultCallback<Boolean>() {
                @Override public void onActivityResult(Boolean granted) {
                    if (pendingMobile != null) { String m = pendingMobile, pw = pendingPassword; pendingMobile = null; pendingPassword = null; showCodeDialog(m, pw); }
                }
            });

    private final ActivityResultLauncher<Intent> faceCamera = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new androidx.activity.result.ActivityResultCallback<androidx.activity.result.ActivityResult>() {
                @Override public void onActivityResult(androidx.activity.result.ActivityResult r) {
                    if (r.getResultCode() == RESULT_OK) checkPhoto(true);
                }
            });
    private final ActivityResultLauncher<Intent> idCamera = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new androidx.activity.result.ActivityResultCallback<androidx.activity.result.ActivityResult>() {
                @Override public void onActivityResult(androidx.activity.result.ActivityResult r) {
                    if (r.getResultCode() == RESULT_OK) checkPhoto(false);
                }
            });
    private final ActivityResultLauncher<String> idGallery = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            new androidx.activity.result.ActivityResultCallback<Uri>() {
                @Override public void onActivityResult(Uri uri) {
                    if (uri == null) return;
                    idOk = false;
                    if (FaceVerifier.copy(RegisterActivity.this, uri, idFile)) checkPhoto(false);
                    else Toast.makeText(RegisterActivity.this, "Could not open that picture.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        tilName = findViewById(R.id.til_name);
        tilAge = findViewById(R.id.til_age);
        tilAddress = findViewById(R.id.til_address);
        tilPassword = findViewById(R.id.til_password);
        tilConfirm = findViewById(R.id.til_confirm);
        tilContact = findViewById(R.id.til_contact);
        etName = findViewById(R.id.et_name);
        etAge = findViewById(R.id.et_age);
        etAddress = findViewById(R.id.et_address);
        etPassword = findViewById(R.id.et_password);
        etConfirm = findViewById(R.id.et_confirm);
        etContact = findViewById(R.id.et_contact);
        btnRegister = findViewById(R.id.btn_register);
        if (savedInstanceState != null) verifiedMobile = savedInstanceState.getString("verifiedMobile", "");
        googleSub = getIntent().getStringExtra(EXTRA_GOOGLE_SUB) == null ? "" : getIntent().getStringExtra(EXTRA_GOOGLE_SUB);
        googleEmail = getIntent().getStringExtra(EXTRA_GOOGLE_EMAIL) == null ? "" : getIntent().getStringExtra(EXTRA_GOOGLE_EMAIL);
        if (isGoogle()) {
            tilPassword.setVisibility(View.GONE);
            tilConfirm.setVisibility(View.GONE);
            if (savedInstanceState == null) {
                String gName = getIntent().getStringExtra(EXTRA_GOOGLE_NAME);
                if (gName != null) etName.setText(gName);
            }
        }
        String prefillMobile = getIntent().getStringExtra(EXTRA_MOBILE);
        if (savedInstanceState == null && prefillMobile != null) etContact.setText(prefillMobile);
        etContact.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (!Validator.normalizeNumber(e.toString()).equals(verifiedMobile)) {
                    verifiedMobile = "";
                    tilContact.setHelperText(null);
                }
            }
        });

        // Optional profile picture. Everyone (responders, admins, other citizens) sees it once the account exists.
        View photo = findViewById(R.id.photo_picker);
        photoField = new com.rescuelink.app.utils.PhotoField(this, this, photo, (ImageView) findViewById(R.id.iv_photo));

        tvIdStatus = findViewById(R.id.tv_id_status);
        tvFaceStatus = findViewById(R.id.tv_face_status);
        ivIdThumb = findViewById(R.id.iv_id_thumb);
        ivFaceThumb = findViewById(R.id.iv_face_thumb);
        idFile = FaceVerifier.tempFile(this, "reg_id.jpg");
        faceFile = FaceVerifier.tempFile(this, "reg_face.jpg");
        if (savedInstanceState == null) {
            FaceVerifier.clearTemp(this);
        } else {
            idOk = savedInstanceState.getBoolean("idOk") && idFile.exists();
            faceOk = savedInstanceState.getBoolean("faceOk") && faceFile.exists();
            if (idOk) showResult(false, true, "ID photo accepted.");
            if (faceOk) showResult(true, true, "Face scan passed.");
        }
        findViewById(R.id.btn_face).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCamera(faceCamera, faceFile, true); }
        });
        findViewById(R.id.btn_id).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new MaterialAlertDialogBuilder(RegisterActivity.this)
                        .setTitle("Add a photo of your ID")
                        .setItems(new String[]{"Take a photo", "Choose from gallery"},
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface d, int which) {
                                        if (which == 0) openCamera(idCamera, idFile, false);
                                        else idGallery.launch("image/*");
                                    }
                                }).show();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { attemptRegister(); }
        });
    }

    private boolean isGoogle() { return !googleSub.isEmpty(); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("idOk", idOk);
        out.putBoolean("faceOk", faceOk);
        out.putString("verifiedMobile", verifiedMobile);
    }

    private void openCamera(ActivityResultLauncher<Intent> launcher, File target, boolean front) {
        try {
            if (front) faceOk = false; else idOk = false;     // the old photo is about to be replaced
            (front ? tvFaceStatus : tvIdStatus).setText("");
            if (target.exists()) //noinspection ResultOfMethodCallIgnored
                target.delete();
            Uri uri = FaceVerifier.uriFor(this, target);
            Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
            i.setClipData(ClipData.newRawUri("photo", uri));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (front) {
                i.putExtra("android.intent.extras.CAMERA_FACING", 1);
                i.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                i.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
            }
            launcher.launch(i);
        } catch (Exception e) {
            Toast.makeText(this, "No camera app is available on this phone.", Toast.LENGTH_LONG).show();
        }
    }

    /** Checks the photo for a face on a background thread, then shows the result. */
    private void checkPhoto(final boolean selfie) {
        final File f = selfie ? faceFile : idFile;
        (selfie ? tvFaceStatus : tvIdStatus).setText("Checking photo...");
        new Thread(new Runnable() {
            @Override public void run() {
                final FaceVerifier.Result res = FaceVerifier.check(f, selfie);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (selfie) faceOk = res.ok; else idOk = res.ok;
                        showResult(selfie, res.ok, res.message);
                    }
                });
            }
        }).start();
    }

    private void showResult(boolean selfie, boolean ok, String message) {
        TextView status = selfie ? tvFaceStatus : tvIdStatus;
        ImageView thumb = selfie ? ivFaceThumb : ivIdThumb;
        status.setText((ok ? "\u2713 " : "") + message);
        status.setTextColor(com.rescuelink.app.utils.ThemeColors.color(this, ok ? R.color.green : R.color.critical));
        Bitmap b = FaceVerifier.load(selfie ? faceFile : idFile, 200);
        if (b != null) {
            thumb.setPadding(0, 0, 0, 0);
            thumb.setImageBitmap(b);
        }
    }

    private boolean required(TextInputLayout til, EditText et) {
        til.setError(null);
        if (Validator.isBlank(et.getText().toString())) {
            til.setError(getString(R.string.err_required));
            return false;
        }
        return true;
    }

    private void attemptRegister() {
        boolean ok = required(tilName, etName);
        if (required(tilAge, etAge)) {
            if (!Validator.isValidAge(etAge.getText().toString())) { tilAge.setError(getString(R.string.err_age)); ok = false; }
        } else ok = false;
        ok &= required(tilAddress, etAddress);

        final String username = Validator.normalizeNumber(etContact.getText().toString());
        String password = etPassword.getText().toString();
        if (!isGoogle()) {
            if (required(tilPassword, etPassword)) {
                if (password.length() < 6) { tilPassword.setError(getString(R.string.err_password_short)); ok = false; }
            } else ok = false;

            tilConfirm.setError(null);
            if (!password.equals(etConfirm.getText().toString())) { tilConfirm.setError(getString(R.string.err_password_mismatch)); ok = false; }
        }

        if (required(tilContact, etContact)) {
            if (!Validator.isValidContact(username)) { tilContact.setError(getString(R.string.err_contact)); ok = false; }
        } else ok = false;
        if (!idOk) { tvIdStatus.setText("Add a clear photo of your ID to continue."); tvIdStatus.setTextColor(com.rescuelink.app.utils.ThemeColors.color(this, R.color.critical)); ok = false; }
        if (!faceOk) { tvFaceStatus.setText("Scan your face to continue."); tvFaceStatus.setTextColor(com.rescuelink.app.utils.ThemeColors.color(this, R.color.critical)); ok = false; }
        if (!ok) return;

        if (DataRepository.findUserByLogin(username) != null) {
            tilContact.setError("That number already has an account. Sign in instead.");
            return;
        }
        if (!idFile.exists() || !faceFile.exists()) {
            idOk = idOk && idFile.exists();
            faceOk = faceOk && faceFile.exists();
            if (!idOk) tvIdStatus.setText("Add a clear photo of your ID to continue.");
            if (!faceOk) tvFaceStatus.setText("Scan your face to continue.");
            return;
        }
        if (username.equals(verifiedMobile)) {
            createAccount(username, password);
        } else {
            verifyNumberThenCreate(username, password);
        }
    }

    private void sendCode(final String mobile) {
        String[] shown = new String[1];
        OtpManager.Sent how = OtpManager.issue(this, mobile, shown);
        if (how == OtpManager.Sent.FAILED) {
            Toast.makeText(this, "Could not send the code. Check your SIM and try again.", Toast.LENGTH_LONG).show();
        } else if (how == OtpManager.Sent.ON_SCREEN) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Test mode")
                    .setMessage("This phone cannot send SMS, so the code is shown here: " + shown[0])
                    .setPositiveButton(R.string.ok, null).show();
        } else {
            Toast.makeText(this, "Code sent to " + mobile + ".", Toast.LENGTH_SHORT).show();
        }
    }

    private void verifyNumberThenCreate(final String mobile, final String password) {
        if (!Net.canSendSms(this) && Net.canUseSim(this)) {
            pendingMobile = mobile; pendingPassword = password;
            smsPermission.launch(android.Manifest.permission.SEND_SMS);
            return;
        }
        showCodeDialog(mobile, password);
    }

    private void showCodeDialog(final String mobile, final String password) {
        final EditText code = new EditText(this);
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        code.setHint("6-digit code");
        code.setMaxLines(1);
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(code);
        sendCode(mobile);
        final androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle("Verify " + mobile)
                .setMessage("Enter the code we sent to this number.")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Cancel", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface x, int w) { OtpManager.clear(); }
                })
                .setNeutralButton("Resend", null)
                .setPositiveButton("Verify", null)
                .create();
        d.show();
        final long[] lastSent = {System.currentTimeMillis()};
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (System.currentTimeMillis() - lastSent[0] < OtpManager.RESEND_MS) {
                    Toast.makeText(RegisterActivity.this, "Wait a moment before asking for another code.", Toast.LENGTH_SHORT).show();
                    return;
                }
                lastSent[0] = System.currentTimeMillis();
                sendCode(mobile);
            }
        });
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                OtpManager.Result r = OtpManager.check(mobile, code.getText().toString());
                if (r == OtpManager.Result.OK) {
                    d.dismiss();
                    verifiedMobile = mobile;
                    tilContact.setHelperText("\u2713 Number verified");
                    Toast.makeText(RegisterActivity.this, "Number verified. Tap Register to create your account.", Toast.LENGTH_LONG).show();
                }
                else if (r == OtpManager.Result.WRONG) code.setError("Wrong code");
                else { code.setError(r == OtpManager.Result.EXPIRED ? "Code expired. Tap Resend." : "Too many tries. Tap Resend."); }
            }
        });
    }

    private void createAccount(final String username, final String password) {
        final String keptId = FaceVerifier.keep(this, idFile, username, "id");
        final String keptFace = FaceVerifier.keep(this, faceFile, username, "face");
        if (keptId.isEmpty() || keptFace.isEmpty()) {          // never create an account marked verified without its photos
            Toast.makeText(this, "Could not save your photos. Check the free space on your phone and try again.", Toast.LENGTH_LONG).show();
            return;
        }
        // Accounts live on this phone, so there is nothing to wait for: the account exists as soon as it is saved.
        final String name = etName.getText().toString().trim();
        final int age = Integer.parseInt(etAge.getText().toString().trim());
        final String address = etAddress.getText().toString().trim();
        final User u;
        if (isGoogle()) {
            if (DataRepository.findUser(GoogleAuth.usernameFor(googleSub)) != null) {
                Toast.makeText(this, "This Google account already has an account. Go back and continue with Google.", Toast.LENGTH_LONG).show();
                return;
            }
            // No password: this person signs in with Google. Their mobile number is the contact for reports and calls.
            u = User.restore(name, age, address, GoogleAuth.usernameFor(googleSub), "", "", username, User.ROLE_CITIZEN);
            u.setEmail(googleEmail);
        } else {
            if (DataRepository.usernameExists(username)) { tilContact.setError("That number already has an account. Sign in instead."); return; }
            u = new User(name, age, address, username, password, username, User.ROLE_CITIZEN);
            provisionOnline(username, password);
        }
        DataRepository.addUser(u);
        u.setVerification(keptId, keptFace, true, System.currentTimeMillis());
        DataRepository.save();
        photoField.applyTo(u);                     // optional profile picture
        FaceVerifier.clearTemp(this);
        if (isGoogle()) {                          // already proven by Google: go straight in
            com.rescuelink.app.net.Config.saveSession(this, u.getUsername());
            com.rescuelink.app.utils.SessionManager.setCurrentUser(u);
            LoginActivity.openHomeFor(this, u);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reg_done_title)
                .setMessage(R.string.reg_done_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { finish(); }
                }).show();
    }

    /** Creates the Firebase sign-in for this number. Best effort: the first online sign-in creates it if this fails. */
    private void provisionOnline(final String username, final String password) {
        if (!com.rescuelink.app.net.FirebaseAuth.configured(this)) return;
        final android.content.Context app = getApplicationContext();
        com.rescuelink.app.net.Cloud.run(new Runnable() {
            @Override public void run() { com.rescuelink.app.net.FirebaseAuth.signUp(app, username, password); }
        });
    }
}
