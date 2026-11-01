package com.rescuelink.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.utils.MapHelper;
import com.rescuelink.app.utils.SessionManager;
import com.rescuelink.app.utils.UiAnim;

public class RescueLinkApp extends Application {
    private static final androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks FRAGMENT_MOTION =
            new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(androidx.fragment.app.FragmentManager fm,
                                                  androidx.fragment.app.Fragment f, android.view.View v,
                                                  Bundle savedInstanceState) {
                    UiAnim.decorate(v);
                    UiAnim.enter(v);
                }
            };

    private Activity top;
    private int started;

    /** New data arrived from other phones: ring the alarm if needed and redraw the screen being looked at. */
    private void onCloudSynced(boolean changed) {
        Activity a = top;
        if (a == null) return;
        com.rescuelink.app.models.User me = SessionManager.getCurrentUser();
        if (me != null && !com.rescuelink.app.models.User.ROLE_CITIZEN.equals(me.getRole())) {
            boolean was = com.rescuelink.app.utils.AlarmService.isRinging();
            com.rescuelink.app.utils.AlarmCenter.check(this);
            if (!was && com.rescuelink.app.utils.AlarmService.isRinging()) {
                com.rescuelink.app.utils.AlarmCenter.onScreenResumed(a, null);   // show the Stop box
            }
        }
        if (changed && a instanceof com.rescuelink.app.activities.LiveRefresh) {
            ((com.rescuelink.app.activities.LiveRefresh) a).onLiveUpdate();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        UiAnim.load(this);
        MapHelper.init(this);
        DataRepository.init(this);
        SessionManager.restore(this);      // signed in last time: open straight to the home screen
        com.rescuelink.app.data.CloudSync.start(this);
        com.rescuelink.app.data.CloudSync.addListener(new com.rescuelink.app.data.CloudSync.Listener() {
            @Override public void onSynced(boolean changed) { onCloudSynced(changed); }
        });
        // Save whenever a screen leaves the foreground, so edits made directly on a record are never lost.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityPaused(Activity a) { DataRepository.save(); if (top == a) top = null; }
            @Override public void onActivityStopped(Activity a) {
                DataRepository.save();
                started = Math.max(0, started - 1);
                com.rescuelink.app.data.CloudSync.setForeground(started > 0);
            }
            @Override public void onActivityResumed(Activity a) {
                top = a;
                com.rescuelink.app.utils.ThemeManager.refreshIfStale(a);   // theme colour changed while this screen waited
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {
                com.rescuelink.app.utils.ThemeManager.apply(a);          // the person's chosen theme colour
                // Every fragment screen gets press feedback and a staggered entrance automatically.
                if (a instanceof androidx.fragment.app.FragmentActivity) {
                    ((androidx.fragment.app.FragmentActivity) a).getSupportFragmentManager()
                            .registerFragmentLifecycleCallbacks(FRAGMENT_MOTION, true);
                }
            }
            @Override public void onActivityStarted(final Activity a) {
                started++;
                com.rescuelink.app.data.CloudSync.setForeground(true);
                // Plain screens: add press feedback once the layout exists, and animate content in once.
                final android.view.View decor = a.getWindow().getDecorView();
                if (decor.getTag(com.rescuelink.app.R.id.tag_decorated) != null) return;
                decor.setTag(com.rescuelink.app.R.id.tag_decorated, Boolean.TRUE);
                decor.post(new Runnable() {
                    @Override public void run() {
                        android.view.View content = a.findViewById(android.R.id.content);
                        UiAnim.decorate(content);
                        if (!(a instanceof com.rescuelink.app.activities.TabHost)) UiAnim.enterActivity(content);
                    }
                });
            }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
