package com.rescuelink.app.activities;

import androidx.fragment.app.Fragment;

import com.rescuelink.app.R;
import com.rescuelink.app.fragments.AdminManageFragment;
import com.rescuelink.app.fragments.AdminControlFragment;
import com.rescuelink.app.fragments.AssistantFragment;
import com.rescuelink.app.fragments.IncidentListFragment;
import com.rescuelink.app.fragments.MapFragment;

/** Admin app: Control centre, Incidents, Map, Assistant, Manage. */
public class AdminActivity extends RoleShellActivity {
    @Override protected String requiredRole() { return com.rescuelink.app.models.User.ROLE_ADMIN; }

    @Override protected int menuRes() { return R.menu.bottom_nav_admin; }

    @Override
    protected Fragment createFragment(int id) {
        if (id == R.id.nav_incidents) return new IncidentListFragment();
        if (id == R.id.nav_map) return new MapFragment();
        if (id == R.id.nav_assistant) return new AssistantFragment();
        if (id == R.id.nav_manage) return new AdminManageFragment();
        return new AdminControlFragment();
    }
}
