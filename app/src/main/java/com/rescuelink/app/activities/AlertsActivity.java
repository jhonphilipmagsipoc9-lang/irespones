package com.rescuelink.app.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.RowAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.AlertItem;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Public advisories. Admins can also broadcast a new alert. */
public class AlertsActivity extends AppCompatActivity {
    private RowAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_simple_list);
        ((TextView) findViewById(R.id.tv_list_title)).setText("Alerts");
        ((TextView) findViewById(R.id.tv_list_sub)).setText("Advisories and announcements from the operations desk.");
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
        empty = findViewById(R.id.tv_empty);
        empty.setText("No alerts right now");

        adapter = new RowAdapter(new java.util.function.Consumer<com.rescuelink.app.adapters.RowAdapter.Row>() {
            @Override
            public void accept(com.rescuelink.app.adapters.RowAdapter.Row row) {
            final AlertItem a = (AlertItem) row.tag;
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(AlertsActivity.this)
                    .setTitle(a.getTitle())
                    .setMessage(a.getMessage() + "\n\n" + a.getAuthor() + " · " + UiUtils.formatTime(a.getTimeMillis()))
                    .setPositiveButton(R.string.ok, null);
            if ((User.ROLE_ADMIN.equals(SessionManager.role()) && !DataRepository.isScoped(SessionManager.getCurrentUser()))) {
                b.setNegativeButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        DataRepository.deleteAlert(a);
                        bind();
                    }
                });
            }
            b.show();
        }
        });
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        if ((User.ROLE_ADMIN.equals(SessionManager.role()) && !DataRepository.isScoped(SessionManager.getCurrentUser()))) {
            MaterialButton add = findViewById(R.id.btn_action);
            add.setText("Broadcast");
            add.setVisibility(View.VISIBLE);
            add.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    showComposer();
                }
            });
        }
        bind();
        DataRepository.markAllAlertsRead();
    }

    private void bind() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (AlertItem a : DataRepository.getAlerts()) {
            int fg = a.getLevel() == AlertItem.CRITICAL ? UiUtils.RED_FG
                    : a.getLevel() == AlertItem.WARNING ? UiUtils.AMBER_FG : UiUtils.BLUE_FG;
            int bg = a.getLevel() == AlertItem.CRITICAL ? UiUtils.RED_BG
                    : a.getLevel() == AlertItem.WARNING ? UiUtils.AMBER_BG : UiUtils.BLUE_BG;
            rows.add(new RowAdapter.Row(a.getTitle(), a.getMessage())
                    .meta(a.getAuthor() + " · " + UiUtils.timeAgo(a.getTimeMillis()))
                    .badge(a.levelLabel(), fg, bg)
                    .icon(R.drawable.ic_warning, bg)
                    .tag(a));
        }
        adapter.setItems(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showComposer() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_alert, null);
        TextInputLayout tilTitle = view.findViewById(R.id.til_alert_title);
        TextInputLayout tilMsg = view.findViewById(R.id.til_alert_message);
        TextInputEditText etTitle = view.findViewById(R.id.et_alert_title);
        TextInputEditText etMsg = view.findViewById(R.id.et_alert_message);
        AutoCompleteTextView level = view.findViewById(R.id.actv_alert_level);
        level.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                new String[]{"Advisory", "Warning", "Critical"}));
        level.setText("Advisory", false);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Broadcast alert")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View b) {
                    String t = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();
                    String m = etMsg.getText() == null ? "" : etMsg.getText().toString().trim();
                    tilTitle.setError(t.isEmpty() ? getString(R.string.err_required) : null);
                    tilMsg.setError(m.isEmpty() ? getString(R.string.err_required) : null);
                    if (t.isEmpty() || m.isEmpty()) return;
                    String lv = level.getText().toString();
                    int lvl = "Critical".equals(lv) ? AlertItem.CRITICAL : "Warning".equals(lv)
                            ? AlertItem.WARNING : AlertItem.INFO;
                    DataRepository.addAlert(t, m, lvl, SessionManager.getCurrentUser() == null
                            ? "Operations Desk" : SessionManager.getCurrentUser().getFullName());
                    DataRepository.markAllAlertsRead();
                    bind();
                    dialog.dismiss();
                    Toast.makeText(AlertsActivity.this, "Alert sent to all citizens and responders.", Toast.LENGTH_SHORT).show();
                }
                });
            }
        });
        dialog.show();
    }
}
