package com.rescuelink.app.activities;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.rescuelink.app.R;

/** Bottom-navigation shell shared by the citizen, responder and admin apps. */
public abstract class RoleShellActivity extends AppCompatActivity implements TabHost, LiveRefresh {
    private BottomNavigationView nav;

    @MenuRes
    protected abstract int menuRes();

    protected abstract Fragment createFragment(int menuId);

    /** Shells ask once for permission to place calls directly. Responders ask together with location. */
    protected boolean askForCallPermission() { return true; }

    /** The only role allowed to open this shell. */
    protected abstract String requiredRole();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireRole(this, requiredRole())) return;
        setContentView(R.layout.activity_shell);
        if (askForCallPermission() && !com.rescuelink.app.net.Net.canCallDirectly(this)) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CALL_PHONE}, 4011);
        }
        nav = findViewById(R.id.bottom_nav);
        nav.inflateMenu(menuRes());
        nav.setOnItemSelectedListener(new com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(android.view.MenuItem item) {
            show(item.getItemId());
            return true;
        }
        });
        if (savedInstanceState == null) nav.setSelectedItemId(R.id.nav_home);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (nav.getSelectedItemId() != R.id.nav_home) {
                    nav.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.rescuelink.app.utils.AlarmCenter.onScreenResumed(this, null);
    }

    @Override
    public void onLiveUpdate() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.container);
        if (f instanceof LiveRefresh && f.isAdded() && f.getView() != null) ((LiveRefresh) f).onLiveUpdate();
    }

    protected void select(int menuId) {
        if (nav.getMenu().findItem(menuId) != null) nav.setSelectedItemId(menuId);
    }

    private void show(int id) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.frag_in, R.anim.frag_out)
                .replace(R.id.container, createFragment(id))
                .commit();
    }

    @Override
    public void showMapTab() { select(R.id.nav_map); }

    @Override
    public void showReportsTab() {
        if (nav.getMenu().findItem(R.id.nav_reports) != null) select(R.id.nav_reports);
        else if (nav.getMenu().findItem(R.id.nav_tasks) != null) select(R.id.nav_tasks);
        else select(R.id.nav_incidents);
    }
}
