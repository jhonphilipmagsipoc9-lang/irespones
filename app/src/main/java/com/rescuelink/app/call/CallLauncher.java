package com.rescuelink.app.call;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.rescuelink.app.activities.CallActivity;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.User;
import com.rescuelink.app.net.Config;
import com.rescuelink.app.net.Net;
import com.rescuelink.app.utils.SessionManager;

import java.util.Random;

/** Starts an internet voice call to somebody who is online. */
public final class CallLauncher {
    private CallLauncher() { }

    /** True when the online service is set up and this phone has internet. */
    public static boolean available(Context c) {
        return Config.cloudEnabled(c) && Net.hasInternet(c);
    }

    /** Calls a person if they are online right now. Returns false (and does nothing) when that is not possible. */
    public static boolean call(Activity a, User target, double lat, double lon) {
        if (target == null || !target.isLive() || !available(a)) return false;
        if (CallSession.busy()) {
            Toast.makeText(a, "You are already in a call.", Toast.LENGTH_SHORT).show();
            return true;
        }
        User me = SessionManager.getCurrentUser();
        String myUser = me == null ? "guest-" + (10000 + new Random().nextInt(90000)) : me.getUsername();
        String myName = me == null ? "Caller without an account" : me.getFullName();
        if (me != null && User.ROLE_CITIZEN.equals(me.getRole()) && !me.getContact().isEmpty()) {
            myName = me.getFullName();
        }
        CallSession.startOutgoing(a, target.getUsername(), target.getFullName(), myUser, myName, lat, lon);
        a.startActivity(new Intent(a, CallActivity.class));
        return true;
    }

    /** Calls the closest online responder of a service. Returns false when nobody of that service is online. */
    public static boolean callNearest(Activity a, String category, double lat, double lon) {
        User target = DataRepository.nearestLiveResponder(category, lat, lon);
        return call(a, target, lat, lon);
    }
}
