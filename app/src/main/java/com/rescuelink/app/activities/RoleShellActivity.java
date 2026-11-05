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
        if (askForCallPermission()) {
            // One request for everything this shell needs: calling directly, and notifications for messages and updates.
            java.util.List<String> need = new java.util.ArrayList<>();
            if (!com.rescuelink.app.net.Net.canCallDirectly(this)) need.add(android.Manifest.permission.CALL_PHONE);
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && !com.rescuelink.app.net.Net.has(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                need.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
            if (!need.isEmpty()) {
                androidx.core.app.ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 4011);
            }
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
        if (!isFinishing()) com.rescuelink.app.utils.LocationGate.require(this);        // the app only works with location ON
        com.rescuelink.app.data.VerifySync.uploadMine(this);              // citizen: share the sign-up ID photo and face scan once
        com.rescuelink.app.utils.LocationShareService.startIfNeeded(this);      // citizen with an open report, responder on duty
        com.rescuelink.app.utils.AlarmCenter.onScreenResumed(this, null);
        refreshBadges();
    }

    /**
     * Red counters on the bottom bar: unread messages on the first tab, and for citizens the number of
     * report updates (accepted, assigned, resolved...) they have not opened on the My reports tab.
     */
    protected void refreshBadges() {
        if (nav == null) return;
        com.rescuelink.app.models.User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        setBadge(R.id.nav_home, com.rescuelink.app.data.DataRepository.unreadMessagesFor(me));
        setBadge(R.id.nav_reports, com.rescuelink.app.data.DataRepository.unreadUpdatesFor(me));
    }

    private void setBadge(int menuId, int count) {
        if (nav.getMenu().findItem(menuId) == null) return;
        if (count > 0) {
            com.google.android.material.badge.BadgeDrawable b = nav.getOrCreateBadge(menuId);
            b.setVisible(true);
            b.setMaxCharacterCount(3);
            b.setNumber(count);
        } else {
            nav.removeBadge(menuId);
        }
    }

    @Override
    public void onLiveUpdate() {
        refreshBadges();
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
