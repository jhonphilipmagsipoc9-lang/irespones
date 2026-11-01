package com.rescuelink.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.ActivityLogActivity;
import com.rescuelink.app.activities.AlertsActivity;
import com.rescuelink.app.activities.CreateAccountActivity;
import com.rescuelink.app.activities.ManageStationsActivity;
import com.rescuelink.app.activities.ManageUsersActivity;
import com.rescuelink.app.activities.ProfileActivity;
import com.rescuelink.app.adapters.RowAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.AdminTools;

import java.util.ArrayList;
import java.util.List;

/** Command Center tools: accounts, stations, broadcasts and profile. */
public class AdminManageFragment extends Fragment {
    private RowAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        ((TextView) v.findViewById(R.id.tv_list_title)).setText("Manage");
        ((TextView) v.findViewById(R.id.tv_list_sub)).setText("Create accounts for each service and set up the stations. Personal details and passwords stay private to each user.");
        adapter = new RowAdapter(new java.util.function.Consumer<com.rescuelink.app.adapters.RowAdapter.Row>() {
            @Override
            public void accept(com.rescuelink.app.adapters.RowAdapter.Row row) {
            if (row.tag instanceof Class) {
                startActivity(new Intent(requireContext(), (Class<?>) row.tag));
            } else if ("cleanup".equals(row.tag)) {
                AdminTools.showCleanup(requireActivity(), new Runnable() {
                    @Override
                    public void run() { onResume(); }
                });
            }
        }
        });
        RecyclerView rv = v.findViewById(R.id.rv_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        boolean scoped = DataRepository.isScoped(me);
        int responders = 0;
        for (User t : DataRepository.getUsersFor(me)) if (User.ROLE_RESPONDER.equals(t.getRole())) responders++;
        List<RowAdapter.Row> rows = new ArrayList<>();
        rows.add(new RowAdapter.Row("Create account", scoped
                ? "Add a " + me.getService() + " responder"
                : "Add an admin for Police, Fire, DRRMO or Medical. Each service admin then creates their own responders.")
                .icon(R.drawable.ic_add, 0xFFDDEFE5).tag(CreateAccountActivity.class));
        rows.add(new RowAdapter.Row("Accounts",
                DataRepository.getUsersFor(me).size() + " accounts, " + responders + " responders")
                .icon(R.drawable.ic_people, 0xFFECE9F5).tag(ManageUsersActivity.class));
        if (scoped) {
            DataRepository.getStationsForService(me.getService());
            rows.add(new RowAdapter.Row("My station",
                    me.getStationName().isEmpty() ? "Not set. Add your station's name and location." : me.getStationName())
                    .icon(R.drawable.ic_pin, 0xFFE3ECF7).tag(com.rescuelink.app.activities.StationEditActivity.class));
            rows.add(new RowAdapter.Row("My profile and sign out", "Account details")
                    .icon(R.drawable.ic_nav_profile, 0xFFEEF1F5).tag(ProfileActivity.class));
            adapter.setItems(rows);
            return;
        }
        rows.add(new RowAdapter.Row("Stations",
                DataRepository.getStations().size() + " stations. Set phone numbers and locations.")
                .icon(R.drawable.ic_pin, 0xFFE3ECF7).tag(com.rescuelink.app.activities.ManageStationsActivity.class));
        rows.add(new RowAdapter.Row("Alerts and broadcasts",
                DataRepository.getAlerts().size() + " published")
                .icon(R.drawable.ic_broadcast, 0xFFFBF1DE).tag(AlertsActivity.class));
        rows.add(new RowAdapter.Row("Activity log",
                DataRepository.getLog().size() + " recorded changes")
                .icon(R.drawable.ic_clock, 0xFFEEF1F5).tag(ActivityLogActivity.class));
        rows.add(new RowAdapter.Row("Data clean-up",
                "Delete stations, closed reports, drafts, disabled accounts and old broadcasts")
                .icon(R.drawable.ic_delete, 0xFFFBE9EB).tag("cleanup"));
        rows.add(new RowAdapter.Row("My profile and sign out", "Account details")
                .icon(R.drawable.ic_nav_profile, 0xFFEEF1F5).tag(ProfileActivity.class));
        adapter.setItems(rows);
    }
}
