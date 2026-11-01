package com.rescuelink.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.rescuelink.app.R;
import com.rescuelink.app.emergency.EmergencyActions.Svc;

/**
 * Home-screen widget with the four emergency buttons. A tap opens {@link WidgetActionActivity},
 * which hands over to the same call/SMS code the Home screen uses.
 */
public class SosWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) manager.updateAppWidget(id, build(context));
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_sos);
        bind(c, v, R.id.btn_w_police, Svc.POLICE, 1);
        bind(c, v, R.id.btn_w_fire, Svc.FIRE, 2);
        bind(c, v, R.id.btn_w_drrmo, Svc.DRRMO, 3);
        bind(c, v, R.id.btn_w_medical, Svc.MEDICAL, 4);
        return v;
    }

    /** Each button needs its own request code, otherwise Android would reuse one PendingIntent for all four. */
    private static void bind(Context c, RemoteViews v, int viewId, Svc s, int requestCode) {
        Intent i = new Intent(c, WidgetActionActivity.class);
        i.putExtra(WidgetActionActivity.EXTRA_SVC, s.key);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, pi);
    }
}
