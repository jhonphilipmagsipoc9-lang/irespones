package com.rescuelink.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.RowAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.LogEntry;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Admin-only record of who changed what: accounts, reports, stations and broadcasts. */
public class ActivityLogActivity extends AppCompatActivity {
    private RowAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireRole(this, User.ROLE_ADMIN)) return;
        if (com.rescuelink.app.data.DataRepository.isScoped(com.rescuelink.app.utils.SessionManager.getCurrentUser())) { finish(); return; }
        setContentView(R.layout.activity_simple_list);
        ((TextView) findViewById(R.id.tv_list_title)).setText("Activity log");
        ((TextView) findViewById(R.id.tv_list_sub)).setText("Every change made in the app, newest first.");
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        empty = findViewById(R.id.tv_empty);
        empty.setText("Nothing has happened yet");
        adapter = new RowAdapter(new java.util.function.Consumer<RowAdapter.Row>() {
            @Override
            public void accept(RowAdapter.Row row) { }
        });
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        MaterialButton clear = findViewById(R.id.btn_action);
        clear.setText("Clear");
        clear.setVisibility(View.VISIBLE);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialAlertDialogBuilder(ActivityLogActivity.this)
                        .setTitle("Clear the activity log?")
                        .setMessage("The history list is emptied. Accounts and reports are not touched.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                DataRepository.clearLog();
                                bind();
                            }
                        })
                        .show();
            }
        });
        bind();
    }

    private void bind() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (LogEntry e : DataRepository.getLog()) {
            rows.add(new RowAdapter.Row(e.getAction(), e.getActor())
                    .meta(UiUtils.formatTime(e.getTimeMillis()) + " (" + UiUtils.timeAgo(e.getTimeMillis()) + ")"));
        }
        adapter.setItems(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
