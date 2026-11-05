package com.rescuelink.app.utils;

import android.content.Context;
import android.content.Intent;

import com.rescuelink.app.activities.IncidentDetailActivity;
import com.rescuelink.app.activities.IncidentManageActivity;
import com.rescuelink.app.models.User;

/** Role-aware navigation shortcuts. */
public final class Nav {
    private Nav() { }

    public static void openIncident(Context ctx, String ref) {
        boolean staff = !User.ROLE_CITIZEN.equals(SessionManager.role());
        Intent i = new Intent(ctx, staff ? IncidentManageActivity.class : IncidentDetailActivity.class);
        i.putExtra("ref", ref);
        ctx.startActivity(i);
    }
}
