package com.rescuelink.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.models.User;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.Validator;

/** The account owner edits their own details, Medical ID and password. Nobody else can. */
public class EditProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_edit_profile);
        User u = SessionManager.getCurrentUser();
        if (u == null) { finish(); return; }

        TextInputLayout tilName = findViewById(R.id.til_name);
        TextInputLayout tilAge = findViewById(R.id.til_age);
        TextInputLayout tilAddress = findViewById(R.id.til_address);
        TextInputLayout tilContact = findViewById(R.id.til_contact);
        EditText etName = findViewById(R.id.et_name);
        EditText etAge = findViewById(R.id.et_age);
        EditText etAddress = findViewById(R.id.et_address);
        EditText etContact = findViewById(R.id.et_contact);
        EditText etAllergies = findViewById(R.id.et_allergies);
        EditText etConditions = findViewById(R.id.et_conditions);
        AutoCompleteTextView blood = findViewById(R.id.actv_blood);

        blood.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                new String[]{"Unknown", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"}));
        etName.setText(u.getFullName());
        etAge.setText(String.valueOf(u.getAge()));
        etAddress.setText(u.getAddress());
        etContact.setText(u.getContact());
        final boolean citizenNumber = User.ROLE_CITIZEN.equals(u.getRole());
        if (citizenNumber) {          // a citizen signs in with this number, so it is not edited here
            etContact.setEnabled(false);
            tilContact.setHint("Mobile number (your sign-in)");
        }
        blood.setText(u.getBloodType().isEmpty() ? "Unknown" : u.getBloodType(), false);
        etAllergies.setText(u.getAllergies());
        etConditions.setText(u.getConditions());
        findViewById(R.id.medical_group).setVisibility(
                User.ROLE_CITIZEN.equals(u.getRole()) ? View.VISIBLE : View.GONE);

        // Password: only the owner can change it, and only by proving the current one.
        final TextInputLayout tilCur = findViewById(R.id.til_current_pw);
        final TextInputLayout tilNew = findViewById(R.id.til_new_pw);
        final TextInputLayout tilCon = findViewById(R.id.til_confirm_pw);
        final EditText etCur = findViewById(R.id.et_current_pw);
        final EditText etNew = findViewById(R.id.et_new_pw);
        final EditText etCon = findViewById(R.id.et_confirm_pw);
        findViewById(R.id.btn_change_password).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                tilCur.setError(null);
                tilNew.setError(null);
                tilCon.setError(null);
                String cur = etCur.getText().toString();
                String nw = etNew.getText().toString();
                if (cur.isEmpty()) { tilCur.setError("Enter your current password"); return; }
                if (nw.length() < 6) { tilNew.setError(getString(R.string.err_password_short)); return; }
                if (!nw.equals(etCon.getText().toString())) { tilCon.setError(getString(R.string.err_password_mismatch)); return; }
                if (!u.changePassword(cur, nw)) { tilCur.setError("Current password is wrong"); return; }
                DataRepository.log("Password changed: " + u.getFullName());
                DataRepository.save();
                etCur.setText("");
                etNew.setText("");
                etCon.setText("");
                Toast.makeText(EditProfileActivity.this, "Password changed.", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
        findViewById(R.id.btn_save_profile).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
            boolean ok = true;
            tilName.setError(null);
            tilAge.setError(null);
            tilAddress.setError(null);
            tilContact.setError(null);
            if (Validator.isBlank(etName.getText().toString())) { tilName.setError(getString(R.string.err_required)); ok = false; }
            if (!Validator.isValidAge(etAge.getText().toString())) { tilAge.setError(getString(R.string.err_age)); ok = false; }
            if (Validator.isBlank(etAddress.getText().toString())) { tilAddress.setError(getString(R.string.err_required)); ok = false; }
            if (!Validator.isValidContact(etContact.getText().toString())) { tilContact.setError(getString(R.string.err_contact)); ok = false; }
            if (!ok) return;
            String newName = etName.getText().toString().trim();
            com.rescuelink.app.data.DataRepository.renameResponder(u.getFullName(), newName);
            u.setFullName(newName);
            u.setAge(Integer.parseInt(etAge.getText().toString().trim()));
            u.setAddress(etAddress.getText().toString().trim());
            if (!citizenNumber) u.setContact(etContact.getText().toString().trim());
            String b = blood.getText().toString();
            u.setBloodType("Unknown".equals(b) ? "" : b);
            u.setAllergies(etAllergies.getText().toString().trim());
            u.setConditions(etConditions.getText().toString().trim());
            com.rescuelink.app.data.DataRepository.save();      
            Toast.makeText(EditProfileActivity.this, "Profile saved.", Toast.LENGTH_SHORT).show();
            finish();
        }
        });
    }
}
