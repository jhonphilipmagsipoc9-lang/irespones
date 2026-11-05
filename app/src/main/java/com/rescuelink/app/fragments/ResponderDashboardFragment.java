package com.rescuelink.app.fragments;

import com.rescuelink.app.utils.ThemeColors;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.rescuelink.app.R;
import com.rescuelink.app.activities.AlertsActivity;
import com.rescuelink.app.activities.ProfileActivity;
import com.rescuelink.app.activities.StationsActivity;
import com.rescuelink.app.activities.TabHost;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.AlertItem;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.DashUi;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.MapHelper;
import com.rescuelink.app.utils.Nav;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Responder home: duty status, current assignment, open incidents and tools. */
public class ResponderDashboardFragment extends Fragment implements com.rescuelink.app.activities.LiveRefresh {
    private LinearLayout content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        content = v.findViewById(R.id.dash_content);
        ((TextView) v.findViewById(R.id.tv_dash_greeting))
                .setText(UiUtils.greeting() + ", " + SessionManager.firstName() + "!");
        ((TextView) v.findViewById(R.id.tv_dash_sub)).setText(SessionManager.getCurrentUser() == null || SessionManager.getCurrentUser().getStationName().isEmpty()
                ? "Responder dashboard" : SessionManager.getCurrentUser().getStationName());
        TextView avatar = v.findViewById(R.id.tv_dash_avatar);
        avatar.setText(SessionManager.firstName().substring(0, 1).toUpperCase(Locale.ROOT));
        avatar.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                startActivity(new Intent(requireContext(), ProfileActivity.class));
            }
        });
        v.findViewById(R.id.btn_dash_bell).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                startActivity(new Intent(requireContext(), AlertsActivity.class));
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        LocationHelper.refresh(requireContext());
        render();
    }

    private void render() {
        View root = getView();
        if (root != null) {
            User who = SessionManager.getCurrentUser();
            String st = who == null || who.getStationName().isEmpty() ? "Responder dashboard" : who.getStationName();
            ((TextView) root.findViewById(R.id.tv_dash_sub)).setText(st);
        }
        if (content == null) return;
        Context ctx = requireContext();
        content.removeAllViews();
        User me = SessionManager.getCurrentUser();
        if (me == null) return;

        DataRepository.dispatchNearest();   // the closest responder is put in charge of anything waiting
        if (com.rescuelink.app.net.Config.cloudEnabled(ctx) && !com.rescuelink.app.data.CloudSync.isOnline()) {
            LinearLayout off = DashUi.card(ctx, content, R.color.amber_pale, R.color.amber);
            DashUi.text(ctx, off, "Offline: showing what is saved on this phone", 14, R.color.navy, true);
            DashUi.text(ctx, off, "New reports from other phones appear when the connection is back. "
                    + "Anything you do now is saved and sent later.", 12, R.color.text_main, false);
        }
        int urgent = 0;
        for (Incident w : DataRepository.getOpenIncidentsFor(SessionManager.getCurrentUser())) {
            if (w.isSos() || "Critical".equals(w.getSeverity())) urgent++;
        }
        if (urgent > 0) {
            LinearLayout b = DashUi.card(ctx, content, R.color.coral_pale, R.color.coral_border);
            b.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) {
                    if (getActivity() instanceof TabHost) ((TabHost) getActivity()).showReportsTab();
                }
            });
            DashUi.text(ctx, b, urgent + (urgent == 1 ? " critical incident needs" : " critical incidents need")
                    + " a responder", 16, R.color.critical, true);
            DashUi.text(ctx, b, "Tap to open the task list, urgent ones first.", 13, R.color.text_main, false);
        }

        // duty status
        DashUi.section(ctx, content, "Duty status", null, null);
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(ctx);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        String[] states = {User.DUTY_ON, User.DUTY_BUSY};   // always on duty; Busy only pauses new assignments
        for (String st : states) {
            MaterialButton b = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setId(View.generateViewId());
            b.setText(st);
            UiUtils.fitButton(b);
            b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            group.addView(b);
            if (st.equals(me.getDutyStatus())) group.check(b.getId());
        }
        group.addOnButtonCheckedListener(new com.google.android.material.button.MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(com.google.android.material.button.MaterialButtonToggleGroup g, int id, boolean checked) {
            if (!checked) return;
            MaterialButton b = g.findViewById(id);
            if (b != null) { me.setDutyStatus(b.getText().toString()); DataRepository.save(); }
        }
        });
        group.setLayoutParams(DashUi.lp(ctx, 0, 6));
        content.addView(group);

        // stats
        List<Incident> mine = DataRepository.getAssignedTo(me.getFullName());
        int active = 0, done = 0;
        for (Incident i : mine) {
            if (i.isActive()) active++;
            else if (i.getStatus() == IncidentStatus.RESOLVED) done++;
        }
        int open = DataRepository.getOpenIncidentsFor(SessionManager.getCurrentUser()).size();
        DashUi.statRow(getLayoutInflater(), content,
                new String[]{String.valueOf(active), String.valueOf(open), String.valueOf(done)},
                new String[]{"My active", "Waiting", "Resolved"},
                new int[]{0xFF0A1F44, 0xFFD97706, 0xFF15803D});

        long dayStart = java.util.Calendar.getInstance().getTimeInMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        dayStart = cal.getTimeInMillis();
        int today = 0;
        for (Incident i : mine) {
            com.rescuelink.app.models.StatusEvent ev = i.findEvent(IncidentStatus.RESOLVED);
            if (i.getStatus() == IncidentStatus.RESOLVED && ev != null && ev.getTimeMillis() >= dayStart) today++;
        }
        DashUi.text(ctx, content, "Today: " + today + " resolved by you \u00B7 "
                + DataRepository.countOnDutyResponders() + " responders ready", 13, R.color.text_secondary, false)
                .setPadding(UiUtils.dp(ctx, 4), UiUtils.dp(ctx, 6), 0, 0);

        addNearestCitizens(ctx, me);

        // current assignment
        DashUi.section(ctx, content, "Current assignment", null, null);
        Incident current = null;
        for (Incident i : mine) if (i.isActive()) { current = i; break; }
        if (current == null) {
            LinearLayout c = DashUi.card(ctx, content, R.color.white, R.color.border);
            DashUi.text(ctx, c, "No active assignment", 16, R.color.navy, true);
            DashUi.text(ctx, c, open + " incidents are waiting for a responder. Open the Tasks tab to accept one.",
                    13, R.color.text_secondary, false);
        } else {
            final Incident inc = current;
            LinearLayout c = DashUi.card(ctx, content, R.color.white, R.color.border);
            DashUi.text(ctx, c, inc.displayTitle(), 17, R.color.navy, true);
            DashUi.text(ctx, c, inc.getReference() + " · " + inc.getCategory() + " · " + inc.getSeverity()
                    + " · " + inc.getStatus().getLabel(), 13, R.color.secondary_blue, false);
            String where = inc.getLocation();
            if (inc.hasPosition()) {
                double km = GeoUtils.distanceFromMeKm(inc.getLat(), inc.getLon());
                where += "\n" + GeoUtils.formatDistance(km) + " away, about " + GeoUtils.formatEta(km);
            }
            DashUi.text(ctx, c, where, 13, R.color.text_secondary, false).setPadding(0, UiUtils.dp(ctx, 6), 0, UiUtils.dp(ctx, 10));
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton open1 = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonStyle);
            styleButton(open1, "Open", true);
            open1.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) {
                    Nav.openIncident(ctx, inc.getReference());
                }
            });
            row.addView(open1);
            if (inc.hasPosition()) {
                MaterialButton dir = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                styleButton(dir, "Directions", false);
                dir.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View x) {
                        MapHelper.navigateTo(ctx, inc.getLat(), inc.getLon(), inc.displayTitle());
                    }
                });
                row.addView(dir);
            }
            c.addView(row);

            LinearLayout row2 = new LinearLayout(ctx);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setPadding(0, UiUtils.dp(ctx, 6), 0, 0);
            final IncidentStatus next = nextStep(inc.getStatus());
            if (next != null) {
                MaterialButton step = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonStyle);
                styleButton(step, stepLabel(next), true);
                step.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View x) { advance(inc, next); }
                });
                row2.addView(step);
            }
            final User reporter = DataRepository.findUser(inc.getOwnerUsername());
            if (reporter != null) {
                MaterialButton callBtn = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                styleButton(callBtn, "Call reporter", false);
                callBtn.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View x) { MapHelper.dial(ctx, reporter.getContact()); }
                });
                row2.addView(callBtn);
            }
            c.addView(row2);
        }

        AlertItem latest = DataRepository.latestAlert();
        if (latest != null) {
            DashUi.section(ctx, content, "Latest alert", "All", new java.lang.Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(ctx, AlertsActivity.class));
                }
            });
            LinearLayout c = DashUi.card(ctx, content, R.color.amber_pale, R.color.amber);
            DashUi.text(ctx, c, latest.getTitle(), 15, R.color.navy, true);
            DashUi.text(ctx, c, latest.getMessage(), 13, R.color.text_main, false);
        }

        // tools
        DashUi.section(ctx, content, "Field tools", null, null);
        LinearLayout tools = new LinearLayout(ctx);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        addTool(tools, "Map", R.drawable.ic_map, 0xFFE2F2F6, new java.lang.Runnable() {
            @Override
            public void run() {
                if (getActivity() instanceof TabHost) ((TabHost) getActivity()).showMapTab();
            }
        });
        addTool(tools, "Stations", R.drawable.ic_pin, 0xFFE3ECF7, new java.lang.Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(ctx, StationsActivity.class));
            }
        });
        int unreadMsgs = DataRepository.unreadMessagesFor(me);
        addTool(tools, com.rescuelink.app.utils.BadgeUi.label("Messages", unreadMsgs), R.drawable.ic_chat, 0xFFE3ECF7, new java.lang.Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(ctx, com.rescuelink.app.activities.MessagesActivity.class));
            }
        });
        content.addView(tools);
    }


    /** The citizens who need help right now, closest to this responder first, with one-tap actions. */
    private void addNearestCitizens(final Context ctx, final User me) {
        List<Incident> waiting = new ArrayList<>(DataRepository.getOpenIncidentsFor(me));
        Collections.sort(waiting, new java.util.Comparator<Incident>() {
            @Override
            public int compare(Incident a, Incident b) {
                return Double.compare(distance(a), distance(b));
            }
        });
        DashUi.section(ctx, content, "Nearest citizens needing help", "View all", new java.lang.Runnable() {
            @Override
            public void run() {
                if (getActivity() instanceof TabHost) ((TabHost) getActivity()).showReportsTab();
            }
        });
        if (!GeoUtils.hasFix() && !waiting.isEmpty()) {
            DashUi.text(ctx, content, "Turn on location so distances can be shown.", 12, R.color.text_secondary, false);
        }
        if (waiting.isEmpty()) {
            DashUi.text(ctx, content, "Nobody is waiting. Every active incident has a responder.", 14,
                    R.color.text_secondary, false);
            return;
        }
        for (int k = 0; k < Math.min(5, waiting.size()); k++) {
            final Incident w = waiting.get(k);
            LinearLayout c = DashUi.card(ctx, content, R.color.white, R.color.border);
            c.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) { Nav.openIncident(ctx, w.getReference()); }
            });
            final User citizen = DataRepository.findUser(w.getOwnerUsername());
            String who = citizen != null ? citizen.getFullName() : w.displayTitle();
            DashUi.text(ctx, c, who, 16, R.color.navy, true);
            DashUi.text(ctx, c, w.displayTitle() + " \u00B7 " + w.getCategory() + " \u00B7 " + w.getSeverity(),
                    13, R.color.text_main, false);
            String line;
            if (w.hasPosition() && GeoUtils.hasFix()) {
                double km = distance(w);
                line = GeoUtils.formatDistance(km) + " away \u00B7 about " + GeoUtils.formatEta(km);
            } else if (w.hasPosition()) {
                line = "Location shared. Turn on your location to see the distance.";
            } else {
                line = w.getLocation().isEmpty() ? "No location given" : w.getLocation();
            }
            DashUi.text(ctx, c, line, 13, R.color.secondary_blue, true);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, UiUtils.dp(ctx, 8), 0, 0);
            MaterialButton acc = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonStyle);
            styleButton(acc, "Accept", true);
            acc.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) {
                    if (User.DUTY_OFF.equals(me.getDutyStatus())) {
                        android.widget.Toast.makeText(ctx, "Set yourself On duty first.",
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DataRepository.acceptIncident(w, me);
                    android.widget.Toast.makeText(ctx, "Accepted " + w.getReference() + ". Head to the location.",
                            android.widget.Toast.LENGTH_SHORT).show();
                    render();
                }
            });
            row.addView(acc);
            if (w.hasPosition()) {
                MaterialButton nav = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                styleButton(nav, "Navigate", false);
                nav.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View x) {
                        MapHelper.navigateTo(ctx, w.getLat(), w.getLon(), w.displayTitle());
                    }
                });
                row.addView(nav);
            }
            if (citizen != null && !citizen.getContact().isEmpty()) {
                MaterialButton call = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                styleButton(call, "Call", false);
                call.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View x) { MapHelper.dial(ctx, citizen.getContact()); }
                });
                row.addView(call);
            }
            c.addView(row);
        }
    }

    private static IncidentStatus nextStep(IncidentStatus s) {
        if (s == IncidentStatus.ASSIGNED) return IncidentStatus.RESPONDING;
        if (s == IncidentStatus.RESPONDING) return IncidentStatus.ON_SCENE;
        if (s == IncidentStatus.ON_SCENE) return IncidentStatus.RESOLVED;
        return null;
    }

    private static String stepLabel(IncidentStatus s) {
        if (s == IncidentStatus.RESPONDING) return "Start responding";
        if (s == IncidentStatus.ON_SCENE) return "I'm on scene";
        return "Mark resolved";
    }

    private void advance(final Incident inc, final IncidentStatus next) {
        if (next == IncidentStatus.RESOLVED) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Mark " + inc.getReference() + " resolved?")
                    .setMessage("The citizen and the operations desk will see it as resolved.")
                    .setNegativeButton("Not yet", null)
                    .setPositiveButton("Resolved", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            DataRepository.updateStatus(inc, next, null);
                            DataRepository.freeResponder(inc);
                            render();
                        }
                    })
                    .show();
            return;
        }
        DataRepository.updateStatus(inc, next, null);
        render();
    }

    private double distance(Incident i) {
        return i.hasPosition() ? GeoUtils.distanceFromMeKm(i.getLat(), i.getLon()) : Double.MAX_VALUE;
    }

    private void styleButton(MaterialButton b, String text, boolean filled) {
        b.setText(text);
        UiUtils.fitButton(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(UiUtils.dp(requireContext(), 6));
        b.setLayoutParams(lp);
        if (filled) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ThemeColors.color(requireContext(), R.color.primary)));
            b.setTextColor(ThemeColors.color(requireContext(), R.color.white));
        }
    }

    private void addTool(LinearLayout row, CharSequence label, int icon, int bg, Runnable action) {
        View t = getLayoutInflater().inflate(R.layout.tile_action, row, false);
        t.findViewById(R.id.tile_bg).setBackground(UiUtils.rounded(bg, UiUtils.dp(requireContext(), 18)));
        ((android.widget.ImageView) t.findViewById(R.id.tile_icon)).setImageResource(icon);
        ((TextView) t.findViewById(R.id.tile_label)).setText(label);
        t.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View x) {
                action.run();
            }
        });
        row.addView(t);
    }

    @Override
    public void onLiveUpdate() { if (isAdded()) render(); }
}
