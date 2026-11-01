package com.rescuelink.app.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuelink.app.R;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.IncidentViews;
import com.rescuelink.app.utils.MapHelper;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Incident handling for responders (accept, update status, notes) and admins (assign, prioritise). */
public class IncidentManageActivity extends AppCompatActivity implements LiveRefresh {
    private String ref;
    private boolean admin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_incident_manage);
        ref = getIntent().getStringExtra("ref");
        admin = User.ROLE_ADMIN.equals(SessionManager.role());
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
    }

    @Override
    public void onLiveUpdate() { if (!isFinishing()) bind(); }

    @Override
    protected void onResume() {
        super.onResume();
        bind();
        com.rescuelink.app.utils.AlarmCenter.onScreenResumed(this, ref);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        IncidentViews.detach(this);
    }

    private void setText(int id, String text) { ((TextView) findViewById(id)).setText(text); }

    private boolean isMine(Incident i) {
        User me = SessionManager.getCurrentUser();
        return me != null && me.getFullName().equals(i.getResponderName());
    }

    private void bind() {
        Incident i = ref == null ? null : DataRepository.findIncident(ref);
        if (i == null) { finish(); return; }

        setText(R.id.tv_ref, i.getReference() + (i.isSos() ? "  SOS" : ""));
        UiUtils.styleChip(findViewById(R.id.tv_status), i.getStatus());
        setText(R.id.tv_category, i.getCategory() + " · " + i.getSeverity() + " priority");
        setText(R.id.tv_title, i.displayTitle());
        setText(R.id.tv_description, i.getDescription().isEmpty() ? "No description." : i.getDescription());
        String loc = i.getLocation().isEmpty() ? "Not set" : i.getLocation();
        if (!i.getLandmark().isEmpty()) loc += " (near: " + i.getLandmark() + ")";
        if (i.hasPosition()) {
            double km = GeoUtils.distanceFromMeKm(i.getLat(), i.getLon());
            loc += "\n" + GeoUtils.formatDistance(km) + " from you, about " + GeoUtils.formatEta(km);
        }
        setText(R.id.tv_location, "Location: " + loc);
        setText(R.id.tv_time, "Reported: " + UiUtils.formatTime(i.getCreatedAt()) + " (" + UiUtils.timeAgo(i.getCreatedAt()) + ")");
        setText(R.id.tv_severity, "Affected people: " + i.getAffected());
        User reporter = DataRepository.findUser(i.getOwnerUsername());
        setText(R.id.tv_reporter, "Reporter: " + (reporter == null ? i.getOwnerUsername()
                : reporter.getFullName()));
        setText(R.id.tv_station, "Assigned station: "
                + (i.getAssignedStation() == null ? "Not assigned yet" : i.getAssignedStation()));
        setText(R.id.tv_responder, "Responder: "
                + (i.getResponderName() == null ? "Not assigned yet" : i.getResponderName()));

        setText(R.id.tv_nearest, nearestText(i));
        IncidentViews.bind(this, i);
        IncidentViews.buildTimeline(this, i);
        IncidentViews.buildHistory(this, i);
        bindActions(i, reporter);
    }

    private void bindActions(Incident i, User reporter) {
        MaterialButton primary = findViewById(R.id.btn_primary_action);
        MaterialButton status = findViewById(R.id.btn_status);
        MaterialButton assign = findViewById(R.id.btn_assign);
        MaterialButton priority = findViewById(R.id.btn_priority);
        MaterialButton call = findViewById(R.id.btn_call_reporter);
        MaterialButton note = findViewById(R.id.btn_note);

        MaterialButton message = findViewById(R.id.btn_message);
        User viewer = SessionManager.getCurrentUser();
        boolean chatOpen = DataRepository.canChat(viewer, i);
        message.setVisibility(chatOpen ? View.VISIBLE : View.GONE);
        if (chatOpen) {
            int unread = DataRepository.unreadMessages(viewer, i);
            message.setText(unread > 0 ? "Messages (" + unread + " new)" : "Message reporter");
            message.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    android.content.Intent chat = new android.content.Intent(IncidentManageActivity.this,
                            IncidentChatActivity.class);
                    chat.putExtra(IncidentChatActivity.EXTRA_REF, i.getReference());
                    startActivity(chat);
                }
            });
        }

        boolean active = i.isActive();
        boolean canWork = active && !admin && isMine(i);

        primary.setVisibility(View.GONE);
        status.setVisibility(canWork || (admin && active) ? View.VISIBLE : View.GONE);
        status.setText(admin ? "Review or close report" : "Update status");
        assign.setVisibility(admin && active ? View.VISIBLE : View.GONE);
        priority.setVisibility(admin && active ? View.VISIBLE : View.GONE);
        note.setVisibility(canWork || (admin && active) ? View.VISIBLE : View.GONE);
        // The admin never sees reporter phone numbers, but can call over the internet while the reporter is online.
        call.setVisibility(reporter != null && (!admin || (reporter.isLive() && com.rescuelink.app.call.CallLauncher.available(this)))
                ? View.VISIBLE : View.GONE);

        if (active && !admin && i.getResponderName() == null) {
            primary.setVisibility(View.VISIBLE);
            primary.setText("Accept this incident");
            primary.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    accept(i);
                }
            });
        } else if (admin && active) {
            if (i.getResponderName() == null) {
                primary.setVisibility(View.VISIBLE);
                primary.setText("Assign responder and station");
                primary.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) { chooseResponder(i); }
                });
            } else if (i.getStatus() == IncidentStatus.SUBMITTED) {
                primary.setVisibility(View.VISIBLE);
                primary.setText("Start review");
                primary.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) { changeStatus(i, IncidentStatus.UNDER_REVIEW, ""); }
                });
            }
        } else if (canWork) {
            IncidentStatus next = nextStatus(i.getStatus());
            if (next != null) {
                primary.setVisibility(View.VISIBLE);
                primary.setText(actionLabel(next));
                primary.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) {
                        askNote(next.getLabel(), new NoteCallback() {
                    @Override
                    public void done(java.lang.String n) {
                        changeStatus(i, next, n);
                    }
                });
                    }
                });
            }
        }

        MaterialButton release = findViewById(R.id.btn_release);
        MaterialButton delete = findViewById(R.id.btn_delete_report);
        release.setVisibility(canWork && (i.getStatus() == IncidentStatus.ASSIGNED
                || i.getStatus() == IncidentStatus.RESPONDING) ? View.VISIBLE : View.GONE);
        release.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                askNote("Hand back to operations", new NoteCallback() {
                    @Override
                    public void done(String n) {
                        User me = SessionManager.getCurrentUser();
                        String who = me == null ? "Responder" : me.getFullName();
                        i.setResponderName(null);
                        i.setAssignedStation(null);
                        DataRepository.updateStatus(i, IncidentStatus.UNDER_REVIEW,
                                who + " handed this back." + (n.isEmpty() ? "" : " " + n));
                        if (me != null && User.DUTY_BUSY.equals(me.getDutyStatus())) {
                            boolean other = false;
                            for (Incident x : DataRepository.getAssignedTo(me.getFullName())) if (x.isActive()) other = true;
                            if (!other) me.setDutyStatus(User.DUTY_ON);
                        }
                        finish();
                    }
                });
            }
        });
        delete.setVisibility(admin ? View.VISIBLE : View.GONE);
        delete.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                new MaterialAlertDialogBuilder(IncidentManageActivity.this)
                        .setTitle("Delete " + i.getReference() + "?")
                        .setMessage("The report is removed for the citizen and for responders. This cannot be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                DataRepository.deleteIncident(i);
                                finish();
                            }
                        })
                        .show();
            }
        });
        status.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                chooseStatus(i);
            }
        });
        assign.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                chooseResponder(i);
            }
        });
        priority.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                choosePriority(i);
            }
        });
        note.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                askNote("Add note", new NoteCallback() {
            @Override
            public void done(java.lang.String n) {
            if (n.isEmpty()) return;
            DataRepository.addNote(i, n);
            bind();
        }
        });
            }
        });
        call.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (reporter == null) return;
                double la = i.hasPosition() ? i.getLat() : Double.NaN, lo = i.hasPosition() ? i.getLon() : Double.NaN;
                if (com.rescuelink.app.call.CallLauncher.call(IncidentManageActivity.this, reporter, la, lo)) return;
                if (!admin) MapHelper.dial(IncidentManageActivity.this, reporter.getContact());
            }
        });
    }

    private static IncidentStatus nextStatus(IncidentStatus s) {
        switch (s) {
            case SUBMITTED: return IncidentStatus.UNDER_REVIEW;
            case UNDER_REVIEW: return IncidentStatus.ASSIGNED;
            case ASSIGNED: return IncidentStatus.RESPONDING;
            case RESPONDING: return IncidentStatus.ON_SCENE;
            case ON_SCENE: return IncidentStatus.RESOLVED;
            default: return null;
        }
    }

    private static String actionLabel(IncidentStatus s) {
        switch (s) {
            case UNDER_REVIEW: return "Start review";
            case ASSIGNED: return "Mark as assigned";
            case RESPONDING: return "Start responding";
            case ON_SCENE: return "Mark on scene";
            case RESOLVED: return "Mark resolved";
            default: return s.getLabel();
        }
    }

    private interface NoteCallback { void done(String note); }

    private void askNote(String title, NoteCallback cb) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_note, null);
        TextInputEditText et = view.findViewById(R.id.et_note);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        cb.done(et.getText() == null ? "" : et.getText().toString().trim());
                    }
                })
                .show();
    }

    private void accept(Incident i) {
        User me = SessionManager.getCurrentUser();
        if (me == null) return;
        DataRepository.acceptIncident(i, me);
        Toast.makeText(this, "Incident accepted. Head to the location when ready.", Toast.LENGTH_SHORT).show();
        bind();
    }

    private void changeStatus(Incident i, IncidentStatus target, String note) {
        DataRepository.updateStatus(i, target, note);
        if (target == IncidentStatus.RESOLVED || target == IncidentStatus.CANCELLED) freeResponder(i);
        bind();
    }

    private void freeResponder(Incident i) { DataRepository.freeResponder(i); }

    private void chooseStatus(Incident i) {
        final IncidentStatus[] options = admin
                ? new IncidentStatus[]{IncidentStatus.UNDER_REVIEW, IncidentStatus.RESOLVED, IncidentStatus.CANCELLED}
                : new IncidentStatus[]{IncidentStatus.RESPONDING, IncidentStatus.ON_SCENE, IncidentStatus.RESOLVED};
        String[] labels = new String[options.length];
        for (int k = 0; k < options.length; k++) labels[k] = options[k].getLabel();
        new MaterialAlertDialogBuilder(this)
                .setTitle(admin ? "Review or close report" : "Update status")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        askNote(labels[which], new NoteCallback() {
                            @Override
                            public void done(java.lang.String n) {
                                changeStatus(i, options[which], n);
                            }
                        });
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void choosePriority(Incident i) {
        final String[] levels = {"Low", "Moderate", "High", "Critical"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Priority")
                .setItems(levels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                    i.setSeverity(levels[which]);
                    DataRepository.addNote(i, "Priority set to " + levels[which] + ".");
                    bind();
                }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private String nearestText(Incident i) { return DataRepository.nearestSummary(i); }

    private void chooseResponder(Incident i) {
        List<User> responders = DataRepository.getRespondersIn(
                DataRepository.serviceOfCategory(DataRepository.stationTypeFor(i.getCategory())));
        List<User> usable = new ArrayList<>();
        for (User u : responders) if (u.isActive()) usable.add(u);
        Collections.sort(usable, new java.util.Comparator<com.rescuelink.app.models.User>() {
            @Override
            public int compare(com.rescuelink.app.models.User a, com.rescuelink.app.models.User b) {
            int ra = User.DUTY_ON.equals(a.getDutyStatus()) ? 0 : User.DUTY_BUSY.equals(a.getDutyStatus()) ? 1 : 2;
            int rb = User.DUTY_ON.equals(b.getDutyStatus()) ? 0 : User.DUTY_BUSY.equals(b.getDutyStatus()) ? 1 : 2;
            if (ra != rb) return Integer.compare(ra, rb);
            double da = i.hasPosition() && a.hasPosition() ? GeoUtils.distanceKm(i.getLat(), i.getLon(), a.getLat(), a.getLon()) : Double.MAX_VALUE;
            double db = i.hasPosition() && b.hasPosition() ? GeoUtils.distanceKm(i.getLat(), i.getLon(), b.getLat(), b.getLon()) : Double.MAX_VALUE;
            return Double.compare(da, db);
        }
        });
        String[] labels = new String[usable.size()];
        for (int k = 0; k < usable.size(); k++) {
            User u = usable.get(k);
            String dist = i.hasPosition() && u.hasPosition()
                    ? " · " + GeoUtils.formatDistance(GeoUtils.distanceKm(i.getLat(), i.getLon(), u.getLat(), u.getLon()))
                    : "";
            labels[k] = u.getFullName() + " (" + u.getDutyStatus() + dist + ")";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose responder")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        chooseStation(i, usable.get(which));
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void chooseStation(Incident i, User responder) {
        List<Station> stations = new ArrayList<>();
        String type = DataRepository.stationTypeFor(i.getCategory());
        for (Station s : DataRepository.getStations()) if (s.isAvailable()) stations.add(s);
        Collections.sort(stations, new java.util.Comparator<com.rescuelink.app.models.Station>() {
            @Override
            public int compare(com.rescuelink.app.models.Station a, com.rescuelink.app.models.Station b) {
            int ta = a.getCategory().equals(type) ? 0 : 1;
            int tb = b.getCategory().equals(type) ? 0 : 1;
            if (ta != tb) return Integer.compare(ta, tb);
            if (!i.hasPosition()) return 0;
            return Double.compare(GeoUtils.distanceKm(i.getLat(), i.getLon(), a.getLat(), a.getLon()),
                    GeoUtils.distanceKm(i.getLat(), i.getLon(), b.getLat(), b.getLon()));
        }
        });
        String[] labels = new String[stations.size()];
        for (int k = 0; k < stations.size(); k++) {
            Station s = stations.get(k);
            labels[k] = s.getName() + " (" + s.getCategory() + ")";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose station")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                    DataRepository.assign(i, stations.get(which).getName(), responder.getFullName());
                    if (User.DUTY_ON.equals(responder.getDutyStatus())) responder.setDutyStatus(User.DUTY_BUSY);
                    bind();
                }
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
