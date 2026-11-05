package com.rescuelink.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.ActivityLogActivity;
import com.rescuelink.app.activities.AlertsActivity;
import com.rescuelink.app.activities.ManageStationsActivity;
import com.rescuelink.app.activities.ManageUsersActivity;
import com.rescuelink.app.activities.ProfileActivity;
import com.rescuelink.app.activities.TabHost;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.LogEntry;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.AdminTools;
import com.rescuelink.app.utils.DashUi;
import com.rescuelink.app.utils.Nav;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiUtils;
import com.rescuelink.app.utils.UserAdminActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin control centre: system numbers, decisions waiting, response team, data tools and activity. */
public class AdminControlFragment extends Fragment implements com.rescuelink.app.activities.LiveRefresh {
    private LinearLayout content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        content = v.findViewById(R.id.dash_content);
        ((TextView) v.findViewById(R.id.tv_dash_greeting))
                .setText(UiUtils.greeting() + ", " + SessionManager.firstName());
        TextView avatar = v.findViewById(R.id.tv_dash_avatar);
        avatar.setText(SessionManager.firstName().substring(0, 1).toUpperCase(Locale.ROOT));
        avatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) { startActivity(new Intent(requireContext(), ProfileActivity.class)); }
        });
        v.findViewById(R.id.btn_dash_bell).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) { startActivity(new Intent(requireContext(), AlertsActivity.class)); }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void open(Class<?> c) { startActivity(new Intent(requireContext(), c)); }

    private void render() {
        if (content == null) return;
        final Context ctx = requireContext();
        content.removeAllViews();

        final User me = SessionManager.getCurrentUser();
        final boolean scoped = DataRepository.isScoped(me);
        List<Incident> all = DataRepository.getAllIncidentsFor(me);
        int resolved = 0, active = 0;
        for (Incident i : all) {
            if (i.getStatus() == IncidentStatus.RESOLVED) resolved++;
            if (i.isActive()) active++;
        }
        String rate = all.isEmpty() ? "0%" : Math.round(100f * resolved / all.size()) + "%";

        // Responder and citizen messages sit right on the overview, not inside Manage.
        int unreadMsgs = DataRepository.unreadMessagesFor(SessionManager.getCurrentUser());
        int threads = DataRepository.chatThreadsFor(SessionManager.getCurrentUser()).size();
        LinearLayout msg = DashUi.card(ctx, content, unreadMsgs > 0 ? R.color.amber_pale : R.color.white,
                unreadMsgs > 0 ? R.color.amber : R.color.border);
        msg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) { open(com.rescuelink.app.activities.MessagesActivity.class); }
        });
        DashUi.text(ctx, msg, "Messages", 16, R.color.navy, true).setText(
                com.rescuelink.app.utils.BadgeUi.label("Messages", unreadMsgs));
        DashUi.text(ctx, msg, threads == 0 ? "No conversations yet."
                : "Tap to read what responders and citizens wrote on " + threads
                + (threads == 1 ? " report." : " reports."), 13, R.color.text_main, false);

        DashUi.section(ctx, content, scoped ? me.getService() + " service" : "System", null, null);
        if (scoped) {
            List<User> mine = new ArrayList<>();
            for (User t : DataRepository.getUsersFor(me)) if (User.ROLE_RESPONDER.equals(t.getRole())) mine.add(t);
            int onDuty = 0;
            for (User r : mine) if (r.isActive() && !User.DUTY_OFF.equals(r.getDutyStatus())) onDuty++;
            DashUi.statRow(getLayoutInflater(), content,
                    new String[]{String.valueOf(mine.size()), String.valueOf(onDuty),
                            String.valueOf(DataRepository.getStationsForService(me.getService()).size())},
                    new String[]{"Responders", "On duty", "Stations"},
                    new int[]{0xFF6D5BD0, 0xFF15803D, 0xFF0F8B6D});
        } else {
        DashUi.statRow(getLayoutInflater(), content,
                new String[]{String.valueOf(DataRepository.getUsers().size()),
                        String.valueOf(DataRepository.countUsersByRole(User.ROLE_CITIZEN)),
                        String.valueOf(DataRepository.countUsersByRole(User.ROLE_RESPONDER))},
                new String[]{"Accounts", "Citizens", "Responders"},
                new int[]{0xFF0A1F44, 0xFF2563EB, 0xFF6D5BD0});
        }
        DashUi.statRow(getLayoutInflater(), content,
                new String[]{String.valueOf(all.size()), String.valueOf(active), rate},
                new String[]{"Reports", "Active", "Resolved rate"},
                new int[]{0xFF0A1F44, 0xFFD97706, 0xFF15803D});
        if (!scoped) DashUi.statRow(getLayoutInflater(), content,
                new String[]{String.valueOf(DataRepository.getStations().size()),
                        String.valueOf(DataRepository.getAlerts().size()),
                        String.valueOf(DataRepository.countDisabledUsers())},
                new String[]{"Stations", "Broadcasts", "Disabled"},
                new int[]{0xFF0F8B6D, 0xFFD97706, 0xFF7B8794});

        // decisions waiting on the admin
        List<Incident> waiting = new ArrayList<>();
        for (Incident i : DataRepository.getOpenIncidents()) if (DataRepository.inScope(me, i)) waiting.add(i);
        DashUi.section(ctx, content, "Needs your decision", "All reports", new Runnable() {
            @Override
            public void run() {
                if (getActivity() instanceof TabHost) ((TabHost) getActivity()).showReportsTab();
            }
        });
        if (waiting.isEmpty()) {
            DashUi.text(ctx, content, "Every active report has a responder.", 14, R.color.text_secondary, false);
        }
        for (int k = 0; k < Math.min(3, waiting.size()); k++) {
            final Incident i = waiting.get(k);
            LinearLayout c = DashUi.card(ctx, content, R.color.white, i.isCritical() ? R.color.coral_border : R.color.border);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) { Nav.openIncident(ctx, i.getReference()); }
            });
            DashUi.text(ctx, c, (i.isSos() ? "SOS \u00B7 " : "") + i.displayTitle(), 15, R.color.text_main, true);
            DashUi.text(ctx, c, i.getReference() + " \u00B7 " + i.getCategory() + " \u00B7 " + i.getSeverity()
                    + " \u00B7 " + UiUtils.timeAgo(i.getCreatedAt()), 13, R.color.text_secondary, false);
            DashUi.text(ctx, c, "Tap to assign a responder and station", 12, R.color.secondary_blue, false);
        }

        // response team
        DashUi.section(ctx, content, "Response team", "Manage", new Runnable() {
            @Override
            public void run() { open(ManageUsersActivity.class); }
        });
        List<User> team = new ArrayList<>();
        for (User t : DataRepository.getUsersFor(me)) if (User.ROLE_RESPONDER.equals(t.getRole())) team.add(t);
        if (team.isEmpty()) {
            DashUi.text(ctx, content, "No responders yet. Responders register themselves and choose their station.", 14, R.color.text_secondary, false);
        }
        LinearLayout teamCard = team.isEmpty() ? null : DashUi.card(ctx, content, R.color.white, R.color.border);
        for (final User r : team) {
            int busyWith = 0;
            for (Incident i : DataRepository.getAssignedTo(r.getFullName())) if (i.isActive()) busyWith++;
            String state = (!r.isActive() ? "Disabled" : r.getDutyStatus())
                    + (r.getStationName().isEmpty() ? "" : " \u00B7 " + r.getStationName());
            DashUi.text(ctx, teamCard, r.getFullName(), 15, R.color.text_main, true)
                    .setPadding(0, UiUtils.dp(ctx, 8), 0, 0);
            TextView line = DashUi.text(ctx, teamCard, state + " \u00B7 " + busyWith + " active"
                    + " \u00B7 tap for actions", 12, R.color.text_secondary, false);
            line.setPadding(0, 0, 0, UiUtils.dp(ctx, 6));
            View row = (View) line.getParent();
            line.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    UserAdminActions.show(requireActivity(), r, new Runnable() {
                        @Override
                        public void run() { render(); }
                    });
                }
            });
        }

        // charts
        DashUi.section(ctx, content, "Reports by category", null, null);
        LinearLayout cat = DashUi.card(ctx, content, R.color.white, R.color.border);
        Map<String, Integer> byCat = new java.util.LinkedHashMap<>();
        for (Incident i : all) {
            Integer n = byCat.get(i.getCategory());
            byCat.put(i.getCategory(), n == null ? 1 : n + 1);
        }
        int max = 1;
        for (int n : byCat.values()) max = Math.max(max, n);
        if (byCat.isEmpty()) DashUi.text(ctx, cat, "No reports yet.", 14, R.color.text_secondary, false);
        for (Map.Entry<String, Integer> e : byCat.entrySet()) {
            DashUi.bar(ctx, cat, e.getKey(), e.getValue(), max, 0xFF2563EB);
        }

        // data tools
        DashUi.section(ctx, content, "Manage all data", null, null);
        LinearLayout t1 = new LinearLayout(ctx);
        t1.setOrientation(LinearLayout.HORIZONTAL);
        addTool(t1, "Users", R.drawable.ic_people, 0xFFECE9F5, new Runnable() {
            @Override
            public void run() { open(ManageUsersActivity.class); }
        });
        addTool(t1, "Reports", R.drawable.ic_list, 0xFFE3ECF7, new Runnable() {
            @Override
            public void run() {
                if (getActivity() instanceof TabHost) ((TabHost) getActivity()).showReportsTab();
            }
        });
        addTool(t1, "Stations", R.drawable.ic_pin, 0xFFE6F0E8, new Runnable() {
            @Override
            public void run() { open(com.rescuelink.app.activities.StationsActivity.class); }
        });
        content.addView(t1);
        if (scoped) return;
        LinearLayout t2 = new LinearLayout(ctx);
        t2.setOrientation(LinearLayout.HORIZONTAL);
        addTool(t2, "Broadcast", R.drawable.ic_broadcast, 0xFFFBF1DE, new Runnable() {
            @Override
            public void run() { open(AlertsActivity.class); }
        });
        addTool(t2, "Activity log", R.drawable.ic_clock, 0xFFEEF1F5, new Runnable() {
            @Override
            public void run() { open(ActivityLogActivity.class); }
        });
        addTool(t2, "Clean-up", R.drawable.ic_delete, 0xFFFBE9EB, new Runnable() {
            @Override
            public void run() {
                AdminTools.showCleanup(requireActivity(), new Runnable() {
                    @Override
                    public void run() { render(); }
                });
            }
        });
        content.addView(t2);

        // recent activity
        DashUi.section(ctx, content, "Recent activity", "See all", new Runnable() {
            @Override
            public void run() { open(ActivityLogActivity.class); }
        });
        List<LogEntry> log = DataRepository.getLog();
        if (log.isEmpty()) DashUi.text(ctx, content, "No changes recorded yet.", 14, R.color.text_secondary, false);
        if (!log.isEmpty()) {
            LinearLayout lc = DashUi.card(ctx, content, R.color.white, R.color.border);
            for (int k = 0; k < Math.min(5, log.size()); k++) {
                LogEntry e = log.get(k);
                DashUi.text(ctx, lc, e.getAction(), 13, R.color.text_main, false)
                        .setPadding(0, UiUtils.dp(ctx, 4), 0, 0);
                DashUi.text(ctx, lc, e.getActor() + " \u00B7 " + UiUtils.timeAgo(e.getTimeMillis()),
                        12, R.color.text_secondary, false);
            }
        }
    }

    private void addTool(LinearLayout row, String label, int icon, int bg, final Runnable action) {
        View t = getLayoutInflater().inflate(R.layout.tile_action, row, false);
        t.findViewById(R.id.tile_bg).setBackground(UiUtils.rounded(bg, UiUtils.dp(requireContext(), 18)));
        ((ImageView) t.findViewById(R.id.tile_icon)).setImageResource(icon);
        ((TextView) t.findViewById(R.id.tile_label)).setText(label);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) { action.run(); }
        });
        row.addView(t);
    }

    @Override
    public void onLiveUpdate() { if (isAdded()) render(); }
}
