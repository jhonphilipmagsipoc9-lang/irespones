package com.rescuelink.app.activities;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.rescuelink.app.R;
import com.rescuelink.app.fragments.AssistantFragment;
import com.rescuelink.app.fragments.IncidentListFragment;
import com.rescuelink.app.fragments.MapFragment;
import com.rescuelink.app.fragments.ProfileFragment;
import com.rescuelink.app.fragments.ResponderDashboardFragment;
import com.rescuelink.app.utils.LocationHelper;
import com.rescuelink.app.utils.LocationShareService;

/** Responder app: Dashboard, Tasks, Map, Assistant, Profile. */
public class ResponderActivity extends RoleShellActivity {
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            new androidx.activity.result.ActivityResultCallback<java.util.Map<String, Boolean>>() {
                @Override public void onActivityResult(java.util.Map<String, Boolean> r) {
                    LocationShareService.startIfResponder(ResponderActivity.this);
                }
            });

    @Override protected String requiredRole() { return com.rescuelink.app.models.User.ROLE_RESPONDER; }

    @Override protected boolean askForCallPermission() { return false; }

    @Override protected int menuRes() { return R.menu.bottom_nav_responder; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;
        java.util.List<String> need = new java.util.ArrayList<>();
        if (!LocationHelper.hasPermission(this)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && !com.rescuelink.app.net.Net.has(this, Manifest.permission.POST_NOTIFICATIONS)) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);   // the "sharing your location" notice
        }
        if (!com.rescuelink.app.net.Net.canCallDirectly(this)) need.add(Manifest.permission.CALL_PHONE);   // so Call dials at once
        if (need.isEmpty()) LocationShareService.startIfResponder(this);
        else permissions.launch(need.toArray(new String[0]));
    }

    @Override
    protected Fragment createFragment(int id) {
        if (id == R.id.nav_tasks) return new IncidentListFragment();
        if (id == R.id.nav_map) return new MapFragment();
        if (id == R.id.nav_assistant) return new AssistantFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        return new ResponderDashboardFragment();
    }
}
