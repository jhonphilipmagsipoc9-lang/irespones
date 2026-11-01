package com.rescuelink.app.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.Station;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.util.Locale;

/** Shared helpers for the OpenStreetMap views and for handing locations to other apps. */
public final class MapHelper {
    public static final int COLOR_CRITICAL = 0xFFD92D20;
    public static final int COLOR_HIGH = 0xFFD77B80;
    public static final int COLOR_MODERATE = 0xFFD97706;
    public static final int COLOR_DONE = 0xFF15803D;
    public static final int COLOR_STATION = 0xFF0A1F44;
    public static final int COLOR_SAFE = 0xFF0F8B6D;
    public static final int COLOR_RESPONDER = 0xFF6D5BD0;
    public static final int COLOR_PICK = 0xFF2563EB;

    private static Context appCtx;
    private static ITileSource mapTiler;
    private static String mapTilerKey = "";

    private MapHelper() { }

    /** Call once before any MapView is inflated. Keeps the tile cache in app-private storage. */
    public static void init(Context ctx) {
        appCtx = ctx.getApplicationContext();
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.setUserAgentValue(ctx.getPackageName());
        File base = new File(ctx.getCacheDir(), "osmdroid");
        File tiles = new File(base, "tiles");
        //noinspection ResultOfMethodCallIgnored
        tiles.mkdirs();
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(tiles);
    }

    /** The standard OpenStreetMap street tiles. Free to use, no API key or account needed. */
    public static ITileSource streetSource() {
        String key = appCtx == null ? "" : com.rescuelink.app.net.Config.mapKey(appCtx);
        if (key.isEmpty()) return TileSourceFactory.MAPNIK;       // no key: plain OpenStreetMap, as before
        if (mapTiler == null || !key.equals(mapTilerKey)) {
            mapTilerKey = key;
            final String k = key;
            mapTiler = new org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase("MapTilerOSM", 0, 19, 256, ".jpg",
                    new String[]{"https://api.maptiler.com/maps/openstreetmap/256/"},
                    "\u00A9 MapTiler \u00A9 OpenStreetMap contributors") {
                @Override public String getTileURLString(long tile) {
                    return getBaseUrl() + org.osmdroid.util.MapTileIndex.getZoom(tile) + "/"
                            + org.osmdroid.util.MapTileIndex.getX(tile) + "/"
                            + org.osmdroid.util.MapTileIndex.getY(tile) + mImageFilenameEnding + "?key=" + k;
                }
            };
        }
        return mapTiler;
    }

    public static void setup(MapView map, double lat, double lon, double zoom) {
        map.setTileSource(streetSource());
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(4.0);
        map.setMaxZoomLevel(19.0);
        map.setTilesScaledToDpi(true); // keeps street names readable on phone screens
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.getController().setZoom(zoom);
        map.getController().setCenter(new GeoPoint(lat, lon));
        addAttribution(map);
    }

    /**
     * Frames some points on the map. One point, or points on top of each other, would make the map zoom out to
     * the whole world, so a minimum area is always kept and the view is centred explicitly.
     */
    public static void fitPoints(final MapView map, final java.util.List<GeoPoint> pts, final boolean animate) {
        if (pts == null || pts.isEmpty()) return;
        map.post(new Runnable() {
            @Override public void run() {
                double n = -90, s = 90, e = -180, w = 180;
                for (GeoPoint p : pts) {
                    n = Math.max(n, p.getLatitude()); s = Math.min(s, p.getLatitude());
                    e = Math.max(e, p.getLongitude()); w = Math.min(w, p.getLongitude());
                }
                GeoPoint center = new GeoPoint((n + s) / 2, (e + w) / 2);
                if (n - s < 0.0008 && e - w < 0.0008) {            // basically one spot: street level
                    map.getController().setZoom(17.0);
                    map.getController().setCenter(center);
                    return;
                }
                double padLat = Math.max((n - s) * 0.3, 0.002), padLon = Math.max((e - w) * 0.3, 0.002);
                org.osmdroid.util.BoundingBox box = new org.osmdroid.util.BoundingBox(
                        Math.min(n + padLat, 85), Math.min(e + padLon, 180), Math.max(s - padLat, -85), Math.max(w - padLon, -180));
                map.zoomToBoundingBox(box, animate);
                map.getController().setCenter(center);
            }
        });
    }

    /** Map credit line required by the tile providers. Safe to call more than once. */
    public static void addAttribution(MapView map) {
        for (org.osmdroid.views.overlay.Overlay o : map.getOverlays()) {
            if (o instanceof CopyrightOverlay) return;
        }
        map.getOverlays().add(new CopyrightOverlay(map.getContext()));
    }

    /** Switches the tile style. Both styles are built from OpenStreetMap data. */
    public static void useSource(MapView map, ITileSource source) {
        map.setTileSource(source);
        map.setMaxZoomLevel(source == TileSourceFactory.OpenTopo ? 17.0 : 19.0);
        if (map.getZoomLevelDouble() > map.getMaxZoomLevel()) map.getController().setZoom(map.getMaxZoomLevel());
    }

    public static Marker addMarker(MapView map, double lat, double lon, String title, int color) {
        Context c = map.getContext();
        Marker m = new Marker(map);
        m.setPosition(new GeoPoint(lat, lon));
        m.setTitle(title);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Drawable d = ContextCompat.getDrawable(c, R.drawable.ic_map_pin);
        if (d != null) {
            d = d.mutate();
            d.setTint(color);
            m.setIcon(d);
        }
        map.getOverlays().add(m);
        return m;
    }

    /** Icon that stands for a station type or report category. */
    public static int iconFor(String category) {
        if (category == null) return R.drawable.ic_pin;
        switch (category) {
            case "Police": return R.drawable.ic_cat_police;
            case "Fire": return R.drawable.ic_cat_fire;
            case "DRRMO": case "Rescue": return R.drawable.ic_cat_drrmo;
            case "Medical": case "Hospital": return R.drawable.ic_cat_medical;
            default: return R.drawable.ic_pin;
        }
    }

    /**
     * Rounded-square map pin with a white border, a soft tinted tile and the service icon, in the style of the
     * redesigned map: easy to tell apart at a glance and readable on any map tile.
     */
    public static Marker addBadgeMarker(MapView map, double lat, double lon, String title, int color, int iconRes) {
        Context c = map.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int size = (int) (46 * d), tail = (int) (8 * d);
        Bitmap bmp = Bitmap.createBitmap(size, size + tail, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x33000000);                                           // soft shadow
        cv.drawRoundRect(new RectF(2 * d, 3 * d, size - 2 * d, size), 12 * d, 12 * d, p);
        p.setColor(0xFFFFFFFF);                                           // white border
        cv.drawRoundRect(new RectF(0, 0, size, size - 2 * d), 13 * d, 13 * d, p);
        Path t = new Path();                                              // pointer
        t.moveTo(size / 2f - 7 * d, size - 4 * d);
        t.lineTo(size / 2f, size + tail - d);
        t.lineTo(size / 2f + 7 * d, size - 4 * d);
        t.close();
        cv.drawPath(t, p);
        p.setColor((color & 0x00FFFFFF) | 0x26000000);                    // tinted tile
        cv.drawRoundRect(new RectF(3 * d, 3 * d, size - 3 * d, size - 5 * d), 10 * d, 10 * d, p);
        Drawable ic = ContextCompat.getDrawable(c, iconRes);
        if (ic != null) {
            ic = ic.mutate();
            ic.setTint(color);
            int pad = (int) (10 * d);
            ic.setBounds(pad, pad - (int) (1 * d), size - pad, size - pad - (int) (3 * d));
            ic.draw(cv);
        }
        Marker m = new Marker(map);
        m.setPosition(new GeoPoint(lat, lon));
        m.setTitle(title);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setIcon(new BitmapDrawable(c.getResources(), bmp));
        map.getOverlays().add(m);
        return m;
    }

    /** The station answering a report: the assigned one, otherwise the nearest one of the right type. */
    public static Station stationFor(Incident i) {
        if (i == null || !i.hasPosition()) return null;
        Station s = com.rescuelink.app.data.DataRepository.findStation(i.getAssignedStation());
        if (s == null) s = com.rescuelink.app.data.DataRepository.nearestStationFor(i.getCategory(), i.getLat(), i.getLon());
        return s;
    }

    public static int incidentColor(Incident i) {
        if (!i.isActive()) return COLOR_DONE;
        if (i.isSos() || "Critical".equals(i.getSeverity())) return COLOR_CRITICAL;
        if ("High".equals(i.getSeverity())) return COLOR_HIGH;
        return COLOR_MODERATE;
    }

    public static int stationColor(Station s) {
        if (!s.isAvailable()) return 0xFF9AA5B1;
        String c = s.getCategory();
        if ("Evacuation".equals(c) || "Relief".equals(c)) return COLOR_SAFE;
        return COLOR_STATION;
    }

    /** Shows directions on the app's own OpenStreetMap screen. Nothing opens in another app. */
    public static void navigateTo(Context ctx, double lat, double lon, String label) {
        Intent i = new Intent(ctx, com.rescuelink.app.activities.DirectionsActivity.class);
        i.putExtra(com.rescuelink.app.activities.DirectionsActivity.EXTRA_LAT, lat);
        i.putExtra(com.rescuelink.app.activities.DirectionsActivity.EXTRA_LON, lon);
        i.putExtra(com.rescuelink.app.activities.DirectionsActivity.EXTRA_LABEL, label == null ? "Destination" : label);
        ctx.startActivity(i);
    }

    /** Small blocking GET for OpenStreetMap services (routing, address lookup). Call off the main thread. */
    public static String httpGet(String url) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", Configuration.getInstance().getUserAgentValue());
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    public static void dial(Context ctx, String number) {
        String n = number == null ? "" : number.replaceAll("[^0-9+*#]", "");
        if (n.isEmpty()) {
            Toast.makeText(ctx, "No phone number is saved for this place yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = Uri.parse("tel:" + Uri.encode(n));
            if (com.rescuelink.app.net.Net.canCallDirectly(ctx)) {
                // Calls straight away. Works without internet, so 911 and station numbers still go through offline.
                Intent call = new Intent(Intent.ACTION_CALL, uri);
                if (!(ctx instanceof android.app.Activity)) call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(call);
            } else {
                ctx.startActivity(new Intent(Intent.ACTION_DIAL, uri));   // permission not given yet: number is filled in
            }
        } catch (SecurityException | ActivityNotFoundException e) {
            Toast.makeText(ctx, "No dialer available on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Calls the nearest available response station (police, hospital, fire, DRRMO...) directly. */
    public static void callNearestStation(Context ctx) {
        Station s = com.rescuelink.app.data.DataRepository.nearestResponseStation(GeoUtils.myLat(), GeoUtils.myLon());
        if (s == null) {
            Toast.makeText(ctx, "No station is available right now. Opening the station list.", Toast.LENGTH_LONG).show();
            ctx.startActivity(new Intent(ctx, com.rescuelink.app.activities.StationsActivity.class));
            return;
        }
        Toast.makeText(ctx, "Calling " + s.getName(), Toast.LENGTH_SHORT).show();
        dial(ctx, s.getPhone());
    }

    public static void shareText(Context ctx, String subject, String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, text);
        ctx.startActivity(Intent.createChooser(send, "Share via"));
    }

    public static String locationLink(double lat, double lon) {
        return String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%f&mlon=%f#map=17/%f/%f",
                lat, lon, lat, lon);
    }
}
