package com.rescuelink.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.rescuelink.app.R;
import com.rescuelink.app.adapters.IncidentAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.IncidentStatus;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.GeoUtils;
import com.rescuelink.app.utils.Nav;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.SimpleTextWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Filterable incident list for responders (tasks) and admins (all incidents). */
public class IncidentListFragment extends Fragment implements com.rescuelink.app.activities.LiveRefresh {
    private IncidentAdapter adapter;
    private TextView empty;
    private String filter;
    private TextView sub;
    private String query = "";
    private boolean admin;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        admin = User.ROLE_ADMIN.equals(SessionManager.role());
        ((TextView) v.findViewById(R.id.tv_list_title)).setText(admin ? "Incidents" : "Tasks");
        sub = v.findViewById(R.id.tv_list_sub);
        sub.setText(admin
                ? "Every report. Tap to manage, hold to delete." : "Every active incident. The closest responder is put in charge of each one.");
        empty = v.findViewById(R.id.tv_empty);
        empty.setText("Nothing to show here");

        adapter = new IncidentAdapter(new java.util.function.Consumer<com.rescuelink.app.models.Incident>() {
            @Override
            public void accept(com.rescuelink.app.models.Incident i) {
                Nav.openIncident(requireContext(), i.getReference());
            }
        });
        if (admin) {
            adapter.setOnLongClick(new java.util.function.Consumer<com.rescuelink.app.models.Incident>() {
                @Override
                public void accept(final com.rescuelink.app.models.Incident i) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete " + i.getReference() + "?")
                            .setMessage(i.displayTitle() + " will be removed for everyone. This cannot be undone.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    DataRepository.deleteIncident(i);
                                    apply();
                                }
                            })
                            .show();
                }
            });
        }
        RecyclerView rv = v.findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        EditText et = v.findViewById(R.id.et_search);
        et.setHint("Search by title, reference or place");
        et.setVisibility(View.VISIBLE);
        et.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onChanged(String text) {
                query = text.trim().toLowerCase(Locale.ROOT);
                apply();
            }
        });

        String[] filters = admin
                ? (DataRepository.isScoped(SessionManager.getCurrentUser())
                        ? new String[]{"All", "Pending", "Active", "Critical", "Resolved"}
                        : new String[]{"All", "Medical", "Police", "Fire", "DRRMO", "Pending", "Active", "Critical", "Resolved"})
                : new String[]{"All active", "Mine", "Waiting", "Critical", "Completed"};
        filter = filters[0];
        ChipGroup group = v.findViewById(R.id.chips);
        for (String f : filters) {
            Chip c = (Chip) getLayoutInflater().inflate(R.layout.chip_choice, group, false);
            c.setText(f);
            c.setId(View.generateViewId());
            c.setChecked(f.equals(filter));
            c.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View x) { filter = f; apply(); }
            });
            group.addView(c);
        }
        v.findViewById(R.id.chips_scroll).setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        apply();
    }

    private boolean matches(Incident i) {
        User me = SessionManager.getCurrentUser();
        switch (filter) {
            case "Pending": return i.getStatus() == IncidentStatus.SUBMITTED || i.getStatus() == IncidentStatus.UNDER_REVIEW;
            case "Active": return i.isActive();
            case "Critical": return i.isActive() && i.isCritical();
            case "Resolved": return i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CANCELLED;
            case "Mine": return me != null && me.getFullName().equals(i.getResponderName());
            case "Waiting": return i.isActive() && i.getResponderName() == null;
            case "All active": return i.isActive();
            case "Completed": return me != null && me.getFullName().equals(i.getResponderName()) && !i.isActive();
            case "Medical": case "Police": case "Fire": case "DRRMO": return filter.equals(i.getCategory());
            default: return true;
        }
    }

    private void apply() {
        if (adapter == null) return;
        if (!admin) DataRepository.dispatchNearest();
        List<Incident> out = new ArrayList<>();
        for (Incident i : DataRepository.getAllIncidentsFor(SessionManager.getCurrentUser())) {
            if (!matches(i)) continue;
            if (!admin && !DataRepository.visibleTo(i, SessionManager.getCurrentUser())) continue;
            if (!query.isEmpty() && !i.displayTitle().toLowerCase(Locale.ROOT).contains(query)
                    && !i.getReference().toLowerCase(Locale.ROOT).contains(query)
                    && !i.getLocation().toLowerCase(Locale.ROOT).contains(query)
                    && !i.getCategory().toLowerCase(Locale.ROOT).contains(query)) continue;
            out.add(i);
        }
        if (!admin) {
            // urgent first, then nearest
            Collections.sort(out, new java.util.Comparator<com.rescuelink.app.models.Incident>() {
                @Override
                public int compare(com.rescuelink.app.models.Incident a, com.rescuelink.app.models.Incident b) {
                int pa = a.isCritical() ? 0 : 1;
                int pb = b.isCritical() ? 0 : 1;
                if (pa != pb) return Integer.compare(pa, pb);
                double da = a.hasPosition() ? GeoUtils.distanceFromMeKm(a.getLat(), a.getLon()) : Double.MAX_VALUE;
                double db = b.hasPosition() ? GeoUtils.distanceFromMeKm(b.getLat(), b.getLon()) : Double.MAX_VALUE;
                return Double.compare(da, db);
            }
            });
        }
        adapter.setItems(out);
        if (admin && sub != null) {
            boolean cat = "Medical".equals(filter) || "Police".equals(filter) || "Fire".equals(filter) || "DRRMO".equals(filter);
            sub.setText(cat ? out.size() + " " + filter + " report" + (out.size() == 1 ? "" : "s") + ". Tap to manage, hold to delete."
                    : "Every report. Tap to manage, hold to delete.");
        }
        empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLiveUpdate() { if (isAdded()) apply(); }
}
