package com.rescuelink.app.activities;
import android.content.DialogInterface;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.StatusEvent;
import com.rescuelink.app.utils.IncidentViews;
import com.rescuelink.app.utils.UiUtils;

import java.util.List;

/** Report tracking: details, status timeline, history, cancel and feedback. */
public class IncidentDetailActivity extends AppCompatActivity implements LiveRefresh {
    private String ref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_incident_detail);
        ref = getIntent().getStringExtra("ref");
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {finish();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        IncidentViews.detach(this);
    }

    private void setText(int id, String text) { ((TextView) findViewById(id)).setText(text); }

    private void bind() {
        Incident i = ref == null ? null : DataRepository.findIncident(ref);
        if (i == null) {
            finish();
            return;
        }
        boolean draft = i.getStatus() == IncidentStatus.DRAFT;
        setText(R.id.tv_ref, draft ? "Draft report" : i.getReference());
        UiUtils.styleChip(findViewById(R.id.tv_status), i.getStatus());

        setText(R.id.tv_category, "Category: " + i.getCategory());
        setText(R.id.tv_title, i.displayTitle());
        setText(R.id.tv_description, i.getDescription().isEmpty() ? "No description yet." : i.getDescription());
        String loc = i.getLocation().isEmpty() ? "Not set" : i.getLocation();
        if (!i.getLandmark().isEmpty()) loc += " (near: " + i.getLandmark() + ")";
        setText(R.id.tv_location, "Location: " + loc);
        setText(R.id.tv_time, (draft ? "Last saved: " : "Submitted: ") + UiUtils.formatTime(i.getCreatedAt()));
        setText(R.id.tv_severity, "Severity: " + i.getSeverity() + "  \u00B7  Affected people: " + i.getAffected());

        setText(R.id.tv_station, "Assigned station: "
                + (i.getAssignedStation() == null ? "Not assigned yet" : i.getAssignedStation()));
        setText(R.id.tv_responder, "Responder: "
                + (i.getResponderName() == null ? "Not assigned yet" : i.getResponderName()));
        TextView nearest = findViewById(R.id.tv_nearest);
        if (!draft && i.isActive()) { nearest.setVisibility(View.VISIBLE); nearest.setText(com.rescuelink.app.data.DataRepository.nearestSummary(i)); }
        else nearest.setVisibility(View.GONE);

        IncidentViews.bind(this, i);
        buildTimeline(i);
        buildHistory(i);

        bindMessageButton(i);

        // actions
        MaterialButton btnEdit = findViewById(R.id.btn_edit_draft);
        MaterialButton btnCancel = findViewById(R.id.btn_cancel_report);
        btnEdit.setVisibility(draft ? View.VISIBLE : View.GONE);
        btnEdit.setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {
            Intent intent = new Intent(IncidentDetailActivity.this, ReportActivity.class);
            intent.putExtra("ref", i.getReference());
            startActivity(intent);
        
}
});
        if (draft) {
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setText("Delete draft");
            btnCancel.setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {confirmDeleteDraft(i);
}
});
        } else if (i.isCancellable()) {
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setText("Cancel report");
            btnCancel.setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {confirmCancel(i);
}
});
        } else {
            btnCancel.setVisibility(View.GONE);
        }

        // feedback after resolved
        View feedback = findViewById(R.id.card_feedback);
        feedback.setVisibility(i.getStatus() == IncidentStatus.RESOLVED && !i.isFeedbackGiven()
                ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_send_feedback).setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View v) {
            RatingBar rb = findViewById(R.id.rb_feedback);
            EditText comment = findViewById(R.id.et_feedback);
            if (rb.getRating() < 1f) {
                Toast.makeText(IncidentDetailActivity.this, "Please choose a star rating.", Toast.LENGTH_SHORT).show();
                return;
            }
            i.setFeedbackGiven(true);
            comment.setText("");
            Toast.makeText(IncidentDetailActivity.this, "Thank you for your feedback.",
                    Toast.LENGTH_LONG).show();
            feedback.setVisibility(View.GONE);
        
}
});
    }

    /** The conversation with whoever is handling the report. Shows how many replies are waiting. */
    private void bindMessageButton(final Incident i) {
        MaterialButton btn = findViewById(R.id.btn_message);
        com.rescuelink.app.models.User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        if (!com.rescuelink.app.data.DataRepository.canChat(me, i)) {
            btn.setVisibility(View.GONE);
            return;
        }
        int unread = DataRepository.unreadMessages(me, i);
        btn.setVisibility(View.VISIBLE);
        btn.setText(unread > 0 ? "Messages (" + unread + " new)" : "Message responders");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent chat = new Intent(IncidentDetailActivity.this, IncidentChatActivity.class);
                chat.putExtra(IncidentChatActivity.EXTRA_REF, i.getReference());
                startActivity(chat);
            }
        });
    }

    private void buildTimeline(Incident i) {
        LinearLayout container = findViewById(R.id.timeline_container);
        container.removeAllViews();
        for (IncidentStatus s : IncidentStatus.FLOW) {
            StatusEvent e = i.findEvent(s);
            addTimelineRow(container, s.getLabel(),
                    e == null ? "Pending" : UiUtils.formatTime(e.getTimeMillis()),
                    e == null ? Color.parseColor("#D5DCE4") : s.getTextColor());
        }
        StatusEvent cancelled = i.findEvent(IncidentStatus.CANCELLED);
        if (cancelled != null) {
            addTimelineRow(container, "Cancelled", UiUtils.formatTime(cancelled.getTimeMillis()),
                    IncidentStatus.CANCELLED.getTextColor());
        }
    }

    private void addTimelineRow(LinearLayout container, String label, String time, int dotColor) {
        View row = getLayoutInflater().inflate(R.layout.item_timeline_step, container, false);
        TextView dot = row.findViewById(R.id.tv_dot);
        dot.setTextColor(dotColor);
        ((TextView) row.findViewById(R.id.tv_step_label)).setText(label);
        ((TextView) row.findViewById(R.id.tv_step_time)).setText(time);
        container.addView(row);
    }

    private void buildHistory(Incident i) {
        LinearLayout container = findViewById(R.id.history_container);
        container.removeAllViews();
        List<StatusEvent> events = i.getHistory();
        if (events.isEmpty()) {
            container.addView(historyLine("No updates yet. This draft has not been submitted."));
            return;
        }
        for (int k = events.size() - 1; k >= 0; k--) {
            StatusEvent e = events.get(k);
            container.addView(historyLine(e.getStatus().getLabel() + " \u00B7 "
                    + UiUtils.formatTime(e.getTimeMillis()) + "\n" + e.getNote()));
        }
    }

    private TextView historyLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.text_main));
        tv.setTextSize(13);
        ViewGroup.MarginLayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiUtils.dp(this, 10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void confirmCancel(Incident i) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel this report?")
                .setMessage("The report will be marked as Cancelled and responders will stop working on it.")
                .setNegativeButton("Keep report", null)
                .setPositiveButton("Cancel report", new DialogInterface.OnClickListener() {
@Override public void onClick(DialogInterface d, int w) {
                    i.setStatus(IncidentStatus.CANCELLED);
                    i.addEvent(new StatusEvent(IncidentStatus.CANCELLED, System.currentTimeMillis(),
                            "Cancelled by citizen."));
                    bind();
                
}
})
                .show();
    }

    private void confirmDeleteDraft(Incident i) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this draft?")
                .setMessage("The draft will be permanently removed.")
                .setNegativeButton("Keep draft", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
@Override public void onClick(DialogInterface d, int w) {
                    DataRepository.removeIncident(i);
                    finish();
                
}
})
                .show();
    }
}
