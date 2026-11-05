package com.rescuelink.app.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.models.Station;
import com.rescuelink.app.models.User;

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
        return TileSourceFactory.MAPNIK;
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

    // ------------------------------------------------------------------ profile-picture pins
    private static final android.util.LruCache<String, Bitmap> PHOTOS = new android.util.LruCache<>(40);

    /** Small square copy of a saved profile picture, cached so the pins can be redrawn every few seconds cheaply. */
    private static Bitmap photoFor(Context c, String uriText, int px) {
        if (uriText == null || uriText.isEmpty()) return null;
        String key = uriText + "@" + px;
        Bitmap hit = PHOTOS.get(key);
        if (hit != null) return hit;
        try {
            Uri uri = Uri.parse(uriText);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decode(c, uri, bounds);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / Math.max(1, px));
            Bitmap raw = decode(c, uri, o);
            if (raw == null) return null;
            int side = Math.min(raw.getWidth(), raw.getHeight());
            Bitmap sq = Bitmap.createBitmap(raw, (raw.getWidth() - side) / 2, (raw.getHeight() - side) / 2, side, side);
            Bitmap out = Bitmap.createScaledBitmap(sq, px, px, true);
            PHOTOS.put(key, out);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decode(Context c, Uri uri, BitmapFactory.Options o) throws java.io.IOException {
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) return BitmapFactory.decodeFile(uri.getPath(), o);
        try (java.io.InputStream in = c.getContentResolver().openInputStream(uri)) {
            return in == null ? null : BitmapFactory.decodeStream(in, null, o);
        }
    }

    /**
     * Round map pin showing the person's profile picture (or the first letter of their name when they have none),
     * with a white border, a status-coloured ring and a pointer. Used for responders.
     * The ring colour is the status; the title and the card under the map say it in words as well.
     */
    public static Marker addAvatarMarker(MapView map, double lat, double lon, String title, int ringColor, User u) {
        Context c = map.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int size = (int) (52 * d), tail = (int) (9 * d);
        Bitmap bmp = Bitmap.createBitmap(size, size + tail, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = size / 2f, cy = size / 2f - d, r = size / 2f - 2 * d;
        p.setColor(0x33000000);                                            // soft shadow
        cv.drawCircle(cx, cy + 2 * d, r, p);
        p.setColor(0xFFFFFFFF);                                            // white border + pointer
        cv.drawCircle(cx, cy, r, p);
        Path t = new Path();
        t.moveTo(cx - 8 * d, cy + r - 3 * d);
        t.lineTo(cx, size + tail - d);
        t.lineTo(cx + 8 * d, cy + r - 3 * d);
        t.close();
        cv.drawPath(t, p);
        p.setColor(ringColor);                                             // status ring
        cv.drawCircle(cx, cy, r - 2 * d, p);
        float pr = r - 5 * d;                                              // picture area
        int px = (int) (pr * 2);
        Bitmap photo = u == null ? null : photoFor(c, u.getPhotoUri(), px);
        if (photo != null) {
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            BitmapShader sh = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setTranslate(cx - pr, cy - pr);
            sh.setLocalMatrix(m);
            pp.setShader(sh);
            cv.drawCircle(cx, cy, pr, pp);
        } else {
            p.setColor((ringColor & 0x00FFFFFF) | 0xFF000000);             // no picture: coloured disc with the initial
            cv.drawCircle(cx, cy, pr, p);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(22 * d);
            p.setTextAlign(Paint.Align.CENTER);
            String ini = AvatarUtil.initial(u == null ? "" : u.getFullName());
            cv.drawText(ini, cx, cy - (p.descent() + p.ascent()) / 2f, p);
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

    // ------------------------------------------------------------------ road routes (OpenStreetMap OSRM, free)
    /** A route that follows the roads. */
    public static final class RoadRoute {
        public final java.util.List<GeoPoint> pts;
        public final double distM, durS;

        RoadRoute(java.util.List<GeoPoint> pts, double distM, double durS) {
            this.pts = pts;
            this.distM = distM;
            this.durS = durS;
        }
    }

    // The maps are touched on the main thread only.
    private static final java.util.HashMap<String, RoadRoute> ROADS = new java.util.HashMap<>();
    private static final java.util.HashSet<String> LOADING = new java.util.HashSet<>();
    private static final java.util.HashMap<String, Long> FAILED = new java.util.HashMap<>();
    private static final java.util.concurrent.ExecutorService ROUTER = java.util.concurrent.Executors.newFixedThreadPool(2);

    private static String roadKey(double aLat, double aLon, double bLat, double bLon) {
        return String.format(Locale.US, "%.4f,%.4f;%.4f,%.4f", aLat, aLon, bLat, bLon);
    }

    public static RoadRoute cachedRoad(double aLat, double aLon, double bLat, double bLon) {
        return ROADS.get(roadKey(aLat, aLon, bLat, bLon));
    }

    /**
     * Fetches the road route in the background, two at a time, once per pair of points (a failure is retried after
     * a minute). {@code done} runs on the main thread when a new answer arrived, whether it worked or not.
     */
    public static void requestRoad(final double aLat, final double aLon, final double bLat, final double bLon,
                                   final Runnable done) {
        final String key = roadKey(aLat, aLon, bLat, bLon);
        if (ROADS.containsKey(key) || LOADING.contains(key)) return;
        Long failedAt = FAILED.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < 60000L) return;
        LOADING.add(key);
        ROUTER.execute(new Runnable() {
            @Override
            public void run() {
                RoadRoute found = null;
                try {
                    String url = String.format(Locale.US,
                            "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                            aLon, aLat, bLon, bLat);
                    org.json.JSONObject route = new org.json.JSONObject(httpGet(url))
                            .getJSONArray("routes").getJSONObject(0);
                    org.json.JSONArray c = route.getJSONObject("geometry").getJSONArray("coordinates");
                    java.util.List<GeoPoint> pts = new java.util.ArrayList<>();
                    pts.add(new GeoPoint(aLat, aLon));            // joins the line to the real pins
                    for (int i = 0; i < c.length(); i++) {
                        org.json.JSONArray p = c.getJSONArray(i);
                        pts.add(new GeoPoint(p.getDouble(1), p.getDouble(0)));
                    }
                    pts.add(new GeoPoint(bLat, bLon));
                    found = new RoadRoute(pts, route.getDouble("distance"), route.getDouble("duration"));
                } catch (Exception e) {
                    found = null;
                }
                final RoadRoute result = found;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        LOADING.remove(key);
                        if (result != null) {
                            if (ROADS.size() > 60) ROADS.clear();
                            ROADS.put(key, result);
                        } else {
                            FAILED.put(key, System.currentTimeMillis());
                        }
                        if (done != null) done.run();
                    }
                });
            }
        });
    }

    /**
     * The animated route to draw now, in the given colour: it follows the roads once the road route is known, and is
     * the old curved straight line until then (or when there is no internet). Pair it with requestRoad.
     */
    public static RouteAnim.Route roadRoute(double aLat, double aLon, double bLat, double bLon, int color) {
        RoadRoute r = cachedRoad(aLat, aLon, bLat, bLon);
        if (r != null) return new RouteAnim.Route(new java.util.ArrayList<>(r.pts), color);
        return new RouteAnim.Route(new GeoPoint(aLat, aLon), new GeoPoint(bLat, bLon), color);
    }

    /** One animated road route on an overlay: straight first, the road route as soon as it arrives. */
    public static void showRoadRoute(final RouteAnim anim, final double aLat, final double aLon,
                                     final double bLat, final double bLon, final int color) {
        anim.setRoutes(single(roadRoute(aLat, aLon, bLat, bLon, color)));
        requestRoad(aLat, aLon, bLat, bLon, new Runnable() {
            @Override
            public void run() {
                anim.setRoutes(single(roadRoute(aLat, aLon, bLat, bLon, color)));
            }
        });
    }

    private static java.util.List<RouteAnim.Route> single(RouteAnim.Route r) {
        java.util.List<RouteAnim.Route> l = new java.util.ArrayList<>();
        l.add(r);
        return l;
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
