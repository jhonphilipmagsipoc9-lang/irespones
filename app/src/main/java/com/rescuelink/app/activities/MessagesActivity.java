package com.rescuelink.app.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.adapters.RowAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentMessage;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Inbox: every report conversation the signed-in person can take part in, newest first. */
public class MessagesActivity extends AppCompatActivity {
    private RowAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_simple_list);
        ((TextView) findViewById(R.id.tv_list_title)).setText("Messages");
        ((TextView) findViewById(R.id.tv_list_sub)).setText(
                User.ROLE_CITIZEN.equals(SessionManager.role())
                        ? "Chat with the responders handling your reports."
                        : "Chat with the people who reported incidents.");
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        if (User.ROLE_CITIZEN.equals(SessionManager.role())) {
            MaterialButton newMsg = findViewById(R.id.btn_action);
            newMsg.setText("New message");
            newMsg.setVisibility(View.VISIBLE);
            newMsg.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { pickResponder(); }
            });
        }
        empty = findViewById(R.id.tv_empty);
        empty.setText("No conversations yet. Tap New message to write to any responder you choose.");

        adapter = new RowAdapter(new java.util.function.Consumer<RowAdapter.Row>() {
            @Override
            public void accept(RowAdapter.Row row) {
                Intent i = new Intent(MessagesActivity.this, IncidentChatActivity.class);
                i.putExtra(IncidentChatActivity.EXTRA_REF, (String) row.tag);
                startActivity(i);
            }
        });
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        User me = SessionManager.getCurrentUser();
        if (me == null) return;
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Incident i : DataRepository.chatThreadsFor(me)) {
            IncidentMessage last = DataRepository.lastMessage(i.getReference());
            String preview = last == null ? "No messages yet. Tap to start."
                    : (last.getSenderUsername().equalsIgnoreCase(me.getUsername()) ? "You" : last.getSenderName())
                    + ": " + last.getText();
            RowAdapter.Row r = new RowAdapter.Row(i.displayTitle(), preview)
                    .meta(i.getReference() + " · " + UiUtils.timeAgo(DataRepository.activityTime(i)))
                    .icon(R.drawable.ic_chat, 0xFFE3ECF7)
                    .tag(i.getReference());
            int unread = DataRepository.unreadMessages(me, i);
            if (unread > 0) r.badge(unread + " new", UiUtils.GREEN_FG, UiUtils.GREEN_BG);
            rows.add(r);
        }
        for (String ref : DataRepository.directThreadsFor(me)) {
            IncidentMessage last = DataRepository.lastMessage(ref);
            User other = DataRepository.findUserByKey(DataRepository.dmOther(ref, me));
            String name = other == null ? "Conversation" : other.getFullName();
            String preview = last == null ? "No messages yet. Tap to start."
                    : (last.getSenderUsername().equalsIgnoreCase(me.getUsername()) ? "You" : last.getSenderName())
                    + ": " + last.getText();
            RowAdapter.Row r = new RowAdapter.Row(name, preview)
                    .meta("Direct message" + (last == null ? "" : " · " + UiUtils.timeAgo(last.getTimeMillis())))
                    .person(name, other == null ? null : other.getPhotoUri())
                    .tag(ref);
            int unread = DataRepository.unreadDirect(me, ref);
            if (unread > 0) r.badge(unread + " new", UiUtils.GREEN_FG, UiUtils.GREEN_BG);
            rows.add(0, r);
        }
        adapter.setItems(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** The citizen chooses which responder to write to. Online responders are listed first. */
    private void pickResponder() {
        final List<User> list = DataRepository.messageableResponders();
        if (list.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle("No responders yet")
                    .setMessage("No responders are available right now. Please try again in a moment.")
                    .setPositiveButton(R.string.ok, null).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int k = 0; k < names.length; k++) {
            User u = list.get(k);
            String where = !u.getStationName().isEmpty() ? u.getStationName() : u.getService();
            names[k] = u.getFullName() + (where.isEmpty() ? "" : "\n" + where) + (u.isLive() ? "  ·  Online" : "  ·  Offline");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Message which responder?")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        User me = SessionManager.getCurrentUser();
                        User to = list.get(which);
                        if (me == null) return;
                        Intent i = new Intent(MessagesActivity.this, IncidentChatActivity.class);
                        i.putExtra(IncidentChatActivity.EXTRA_REF, DataRepository.dmRef(me.getUsername(), to.getUsername()));
                        startActivity(i);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
