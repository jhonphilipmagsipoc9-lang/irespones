package com.rescuelink.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.RowAdapter;
import com.rescuelink.app.models.EmergencyContact;
import com.rescuelink.app.utils.ContactStore;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.MapHelper;

import java.util.ArrayList;
import java.util.List;

/** Personal emergency contacts with one-tap call and location sharing. */
public class ContactsActivity extends AppCompatActivity {
    private RowAdapter adapter;
    private TextView empty;
    private List<EmergencyContact> contacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_contacts);
        empty = findViewById(R.id.tv_contacts_empty);
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
        findViewById(R.id.btn_add_contact).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                showAddDialog();
            }
        });
        findViewById(R.id.btn_call_nearest).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                MapHelper.callNearestStation(ContactsActivity.this);
            }
        });
        findViewById(R.id.btn_call_143).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                MapHelper.dial(ContactsActivity.this, "143");
            }
        });
        findViewById(R.id.btn_share_loc).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                shareLocation();
            }
        });

        adapter = new RowAdapter(new java.util.function.Consumer<com.rescuelink.app.adapters.RowAdapter.Row>() {
            @Override
            public void accept(com.rescuelink.app.adapters.RowAdapter.Row row) {
                showActions((Integer) row.tag);
            }
        });
        RecyclerView rv = findViewById(R.id.rv_contacts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        bind();
    }

    private void bind() {
        contacts = ContactStore.load(this);
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (int i = 0; i < contacts.size(); i++) {
            EmergencyContact c = contacts.get(i);
            rows.add(new RowAdapter.Row(c.getName(), c.getNumber())
                    .meta(c.getRelation())
                    .icon(R.drawable.ic_phone, 0xFFE7EFF7)
                    .tag(i));
        }
        adapter.setItems(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showActions(int index) {
        if (index < 0 || index >= contacts.size()) return;
        EmergencyContact c = contacts.get(index);
        new MaterialAlertDialogBuilder(this)
                .setTitle(c.getName())
                .setMessage(c.getNumber() + (c.getRelation().isEmpty() ? "" : "\n" + c.getRelation()))
                .setPositiveButton("Call", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        MapHelper.dial(ContactsActivity.this, c.getNumber());
                    }
                })
                .setNeutralButton("Remove", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                    ContactStore.remove(ContactsActivity.this, index);
                    bind();
                }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showAddDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null);
        TextInputLayout tilName = view.findViewById(R.id.til_c_name);
        TextInputLayout tilNumber = view.findViewById(R.id.til_c_number);
        TextInputEditText etName = view.findViewById(R.id.et_c_name);
        TextInputEditText etNumber = view.findViewById(R.id.et_c_number);
        TextInputEditText etRelation = view.findViewById(R.id.et_c_relation);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add contact")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View b) {
            String name = etName.getText() == null ? "" : etName.getText().toString().trim();
            String number = etNumber.getText() == null ? "" : etNumber.getText().toString().trim();
            String rel = etRelation.getText() == null ? "" : etRelation.getText().toString().trim();
            tilName.setError(name.isEmpty() ? getString(R.string.err_required) : null);
            boolean validNumber = number.replaceAll("[^0-9]", "").length() >= 7;
            tilNumber.setError(validNumber ? null : "Enter a valid phone number");
            if (name.isEmpty() || !validNumber) return;
            ContactStore.add(ContactsActivity.this, new EmergencyContact(name, number, rel));
            bind();
            dialog.dismiss();
        }
        });
            }
        });
        dialog.show();
    }

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), new androidx.activity.result.ActivityResultCallback<java.util.Map<java.lang.String, java.lang.Boolean>>() {
                @Override
                public void onActivityResult(java.util.Map<java.lang.String, java.lang.Boolean> r) {
                    shareLocation();
                }
            });

    private void shareLocation() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "Add a contact first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LocationHelper.hasPermission(this)) {
            permLauncher.launch(LocationHelper.PERMISSIONS);
            return;
        }
        if (!LocationHelper.refresh(this)) {
            Toast.makeText(this, "Could not find your location. Turn on location and try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder numbers = new StringBuilder();
        for (EmergencyContact c : contacts) {
            if (numbers.length() > 0) numbers.append(';');
            numbers.append(c.getNumber().replaceAll("[^0-9+]", ""));
        }
        String body = "I need help. My location: "
                + MapHelper.locationLink(GeoUtils.myLat(), GeoUtils.myLon());
        Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers));
        sms.putExtra("sms_body", body);
        try {
            startActivity(sms);
        } catch (ActivityNotFoundException e) {
            MapHelper.shareText(this, "My location", body);
        }
    }
}
