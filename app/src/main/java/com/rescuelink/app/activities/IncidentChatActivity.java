package com.rescuelink.app.activities;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.adapters.MessageAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentMessage;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.SessionManager;

import java.util.List;

/** The conversation attached to one report, shared by the citizen who filed it and the people working it. */
public class IncidentChatActivity extends AppCompatActivity implements LiveRefresh {
    public static final String EXTRA_REF = "ref";

    private String ref;
    private MessageAdapter adapter;
    private RecyclerView rv;
    private EditText et;
    private View inputRow;
    private TextView closed, empty;
    private int shown = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_chat);
        ref = getIntent().getStringExtra(EXTRA_REF);

        rv = findViewById(R.id.rv_chat);
        et = findViewById(R.id.et_chat);
        inputRow = findViewById(R.id.chat_input_row);
        closed = findViewById(R.id.tv_chat_closed);
        empty = findViewById(R.id.tv_chat_empty);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        adapter = new MessageAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { send(); }
        });
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView tv, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) { send(); return true; }
                return false;
            }
        });
    }

    @Override
    public void onLiveUpdate() { if (!isFinishing()) bind(); }

    @Override
    protected void onResume() {
        super.onResume();
        bind();
    }

    private void bind() {
        User me = SessionManager.getCurrentUser();
        if (DataRepository.isDm(ref)) { bindDirect(me); return; }
        Incident i = ref == null ? null : DataRepository.findIncident(ref);
        if (me == null || i == null || !DataRepository.canChat(me, i)) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.tv_chat_title)).setText(counterpart(me, i));
        ((TextView) findViewById(R.id.tv_chat_sub)).setText(i.getReference() + " · " + i.displayTitle());

        List<IncidentMessage> messages = DataRepository.getMessages(ref);
        adapter.setItems(messages, me.getUsername());
        if (messages.size() != shown) {
            shown = messages.size();
            if (!messages.isEmpty()) rv.scrollToPosition(messages.size() - 1);
        }
        boolean canSend = DataRepository.canSend(me, i);
        inputRow.setVisibility(canSend ? View.VISIBLE : View.GONE);
        closed.setVisibility(canSend ? View.GONE : View.VISIBLE);
        empty.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(User.ROLE_CITIZEN.equals(me.getRole())
                ? "No messages yet. Write to the responders handling your report. Do not use this chat in place of a call in a life-threatening emergency."
                : "No messages yet. Say hello to the reporter and tell them what to expect.");
        DataRepository.markRead(me, ref);
    }

    private User directOther(User me) {
        String key = DataRepository.dmOther(ref, me);
        return key == null ? null : DataRepository.findUserByKey(key);
    }

    /** A conversation between a citizen and a responder the citizen chose. */
    private void bindDirect(User me) {
        User other = directOther(me);
        if (me == null || DataRepository.dmOther(ref, me) == null) { finish(); return; }
        String name = other == null ? "Responder" : other.getFullName();
        String where = other == null ? "" : (!other.getStationName().isEmpty() ? other.getStationName() : other.getService());
        ((TextView) findViewById(R.id.tv_chat_title)).setText(name);
        ((TextView) findViewById(R.id.tv_chat_sub)).setText("Direct message" + (where.isEmpty() ? "" : " · " + where));
        List<IncidentMessage> messages = DataRepository.getMessages(ref);
        adapter.setItems(messages, me.getUsername());
        if (messages.size() != shown) {
            shown = messages.size();
            if (!messages.isEmpty()) rv.scrollToPosition(messages.size() - 1);
        }
        inputRow.setVisibility(View.VISIBLE);
        closed.setVisibility(View.GONE);
        empty.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(User.ROLE_CITIZEN.equals(me.getRole())
                ? "No messages yet. Write to " + name + ". Do not use this chat in place of a call in a life-threatening emergency."
                : "No messages yet. Write back to " + name + ".");
        DataRepository.markRead(me, ref);
    }

    /** Who the person is talking to, in words that make sense for their role. */
    private String counterpart(User me, Incident i) {
        if (User.ROLE_CITIZEN.equals(me.getRole())) {
            if (i.getResponderName() != null) return i.getResponderName();
            return i.getAssignedStation() != null ? i.getAssignedStation() : "Responders";
        }
        User reporter = DataRepository.findUser(i.getOwnerUsername());
        return reporter == null ? i.getOwnerUsername() : reporter.getFullName();
    }

    private void send() {
        // Messages are saved on this phone and shared with the others whenever the online service is reachable.
        User me = SessionManager.getCurrentUser();
        if (DataRepository.isDm(ref)) {
            String text0 = et.getText().toString().trim();
            User other = directOther(me);
            if (text0.isEmpty() || me == null || other == null) return;
            if (DataRepository.sendDirect(me, other, text0) != null) et.setText("");
            bind();
            rv.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
            return;
        }
        Incident i = ref == null ? null : DataRepository.findIncident(ref);
        String text = et.getText().toString().trim();
        if (text.isEmpty() || me == null || i == null) return;
        if (DataRepository.addMessage(i, me, text) != null) et.setText("");
        bind();
        rv.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
    }
}
