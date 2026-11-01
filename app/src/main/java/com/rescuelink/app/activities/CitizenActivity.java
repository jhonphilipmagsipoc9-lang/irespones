package com.rescuelink.app.activities;

import androidx.fragment.app.Fragment;

import com.rescuelink.app.R;
import com.rescuelink.app.fragments.AssistantFragment;
import com.rescuelink.app.fragments.HomeFragment;
import com.rescuelink.app.fragments.MapFragment;
import com.rescuelink.app.fragments.ProfileFragment;
import com.rescuelink.app.fragments.ReportsFragment;

/** Citizen app: Home, Map, Reports, Profile. */
public class CitizenActivity extends RoleShellActivity {
    @Override protected String requiredRole() { return com.rescuelink.app.models.User.ROLE_CITIZEN; }

    @Override protected int menuRes() { return R.menu.bottom_nav_citizen; }

    @Override
    protected Fragment createFragment(int id) {
        if (id == R.id.nav_map) return new MapFragment();
        if (id == R.id.nav_assistant) return new AssistantFragment();
        if (id == R.id.nav_reports) return new ReportsFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        return new HomeFragment();
    }

    public void selectTab(int menuId) { select(menuId); }
}
