package com.rescuelink.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.RowAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.MapHelper;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.SimpleTextWatcher;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Admin list of accounts with role filter (All, Citizen, Admin) and one chip per service for its responders, contact and enable or disable. */
public class ManageUsersActivity extends AppCompatActivity {

    private RowAdapter adapter;
    private TextView empty;
    private String filter = "All";
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireRole(this, com.rescuelink.app.models.User.ROLE_ADMIN)) return;
        setContentView(R.layout.activity_simple_list);
        ((TextView) findViewById(R.id.tv_list_title)).setText("Users");
        com.google.android.material.button.MaterialButton create = findViewById(R.id.btn_action);
        create.setVisibility(View.VISIBLE);
        create.setText("Create");
        create.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new android.content.Intent(ManageUsersActivity.this, CreateAccountActivity.class)); }
        });
        ((TextView) findViewById(R.id.tv_list_sub)).setText("Create, disable or delete accounts.");
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
        empty = findViewById(R.id.tv_empty);
        empty.setText("No users match");

        adapter = new RowAdapter(new java.util.function.Consumer<com.rescuelink.app.adapters.RowAdapter.Row>() {
            @Override
            public void accept(com.rescuelink.app.adapters.RowAdapter.Row row) {
                showActions((User) row.tag);
            }
        });
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        EditText et = findViewById(R.id.et_search);
        et.setHint("Search by name or sign-in number");
        et.setVisibility(View.VISIBLE);
        et.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onChanged(String text) {
                query = text.trim().toLowerCase(Locale.ROOT);
                bind();
            }
        });
        ChipGroup group = findViewById(R.id.chips);
        final boolean scoped = DataRepository.isScoped(SessionManager.getCurrentUser());
        for (String f : scoped ? new String[]{"All"} : new String[]{"All", User.ROLE_CITIZEN, User.ROLE_ADMIN, "DRRMO", "Fire", "Police", "Medical"}) {
            Chip c = (Chip) getLayoutInflater().inflate(R.layout.chip_choice, group, false);
            c.setText(f);
            c.setId(View.generateViewId());
            c.setChecked(f.equals("All"));
            c.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) { filter = f; bind(); }
            });
            group.addView(c);
        }
        findViewById(R.id.chips_scroll).setVisibility(View.VISIBLE);

        bind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) bind();
    }

    private void bind() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (User u : DataRepository.getUsersFor(SessionManager.getCurrentUser())) {
            if (!matchesFilter(u)) continue;
            if (!query.isEmpty() && !u.getFullName().toLowerCase(Locale.ROOT).contains(query)
                    && !u.getUsername().toLowerCase(Locale.ROOT).contains(query)) continue;
            String svc = DataRepository.serviceOf(u);
            String roleLabel = User.ROLE_ADMIN.equals(u.getRole())
                    ? (u.isServiceAdmin() ? svc + " admin" : "Command Center")
                    : svc.isEmpty() ? u.getRole() : svc + " " + u.getRole().toLowerCase(Locale.ROOT);
            RowAdapter.Row r = new RowAdapter.Row(u.getFullName(), u.getUsername() + " · " + roleLabel)
                    .meta(User.ROLE_RESPONDER.equals(u.getRole())
                            ? u.getDutyStatus() + (u.getStationName().isEmpty() ? "" : " · " + u.getStationName()) : "")
                    .tag(u);
            if (u.isActive()) r.badge("Active", UiUtils.GREEN_FG, UiUtils.GREEN_BG);
            else r.badge("Disabled", UiUtils.GRAY_FG, UiUtils.GRAY_BG);
            rows.add(r);
        }
        ((TextView) findViewById(R.id.tv_list_sub)).setText(DataRepository.getUsersFor(SessionManager.getCurrentUser()).size() + " accounts \u00B7 "
                + DataRepository.countDisabledUsers() + " disabled. Tap one to enable, disable or remove it. Personal details are private.");
        adapter.setItems(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Role chips (Citizen, Admin) filter by role; service chips list that service's responders. */
    private boolean matchesFilter(User u) {
        if (filter.equals("All")) return true;
        for (String s : User.SERVICES) {
            if (s.equals(filter)) {
                return User.ROLE_RESPONDER.equals(u.getRole()) && s.equals(DataRepository.serviceOf(u));
            }
        }
        return u.getRole().equals(filter);
    }

    private void showActions(final User u) {
        com.rescuelink.app.utils.UserAdminActions.show(this, u, new Runnable() {
            @Override
            public void run() { bind(); }
        });
    }
}
