package com.rescuelink.app.utils;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.view.animation.LinearInterpolator;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated dispatch lines for the maps. For every route a dashed line joins a station to a citizen,
 * a bright dot travels from the station to the citizen leaving a fading trail, and the citizen's
 * position pulses. One shared animator drives all routes on a map.
 */
public class RouteAnim extends Overlay {

    /** One station-to-citizen route. */
    public static class Route {
        final List<GeoPoint> pts;
        final int color;

        /** Straight (gently curved) route when no road points are known. */
        public Route(GeoPoint from, GeoPoint to, int color) {
            this(curve(from, to), color);
        }

        /** Route that follows the given points, for example a road route. */
        public Route(List<GeoPoint> pts, int color) {
            this.pts = pts;
            this.color = color;
        }

        public GeoPoint end() { return pts.get(pts.size() - 1); }
    }

    /** A shallow arc between two points, so overlapping routes stay readable. */
    static List<GeoPoint> curve(GeoPoint a, GeoPoint b) {
        List<GeoPoint> out = new ArrayList<>();
        double dLat = b.getLatitude() - a.getLatitude();
        double dLon = b.getLongitude() - a.getLongitude();
        double cLat = (a.getLatitude() + b.getLatitude()) / 2 - dLon * 0.12;
        double cLon = (a.getLongitude() + b.getLongitude()) / 2 + dLat * 0.12;
        int n = 24;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            out.add(new GeoPoint(u * u * a.getLatitude() + 2 * u * t * cLat + t * t * b.getLatitude(),
                    u * u * a.getLongitude() + 2 * u * t * cLon + t * t * b.getLongitude()));
        }
        return out;
    }

    private final MapView map;
    private final List<Route> routes = new ArrayList<>();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tmp = new Path();
    private final Point pt = new Point();
    private final float density;
    private ValueAnimator anim;
    private float phase;

    public RouteAnim(MapView map) {
        this.map = map;
        this.density = map.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(3f * density);
        line.setAlpha(120);
        line.setPathEffect(new DashPathEffect(new float[]{10f * density, 8f * density}, 0));
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
        trail.setStrokeWidth(5f * density);
        fill.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f * density);
    }

    /** Replaces the routes. Starts the animation when there is something to show. */
    public void setRoutes(List<Route> list) {
        routes.clear();
        for (Route r : list) if (r.pts != null && r.pts.size() >= 2) routes.add(r);
        if (routes.isEmpty()) stop(); else start();
        map.invalidate();
    }

    public void clear() { setRoutes(new ArrayList<Route>()); }

    private void start() {
        if (anim != null) return;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(5200);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                map.postInvalidate();
            }
        });
        anim.start();
    }

    public void stop() {
        if (anim != null) { anim.cancel(); anim = null; }
    }

    public void pause() { if (anim != null) anim.pause(); }

    public void resume() { if (anim != null) anim.resume(); }

    @Override
    public void onDetach(MapView mapView) {
        stop();
    }

    @Override
    public void draw(Canvas c, Projection pj) {
        for (int idx = 0; idx < routes.size(); idx++) {
            Route r = routes.get(idx);
            int n = r.pts.size();
            float[] xs = new float[n];
            float[] ys = new float[n];
            float[] cum = new float[n];
            for (int i = 0; i < n; i++) {
                pj.toPixels(r.pts.get(i), pt);
                xs[i] = pt.x;
                ys[i] = pt.y;
                if (i > 0) {
                    float dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1];
                    cum[i] = cum[i - 1] + (float) Math.sqrt(dx * dx + dy * dy);
                }
            }
            float total = cum[n - 1];
            if (total < 1f) continue;

            line.setColor(r.color);
            line.setAlpha(110);
            tmp.reset();
            tmp.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) tmp.lineTo(xs[i], ys[i]);
            c.drawPath(tmp, line);

            // moving dot, each route slightly out of step with the others
            float p = (phase + idx * 0.17f) % 1f;
            float head = total * p;
            float tail = Math.max(0f, head - total * 0.22f);
            float[] hp = at(xs, ys, cum, head);
            float[] tp = at(xs, ys, cum, tail);

            trail.setColor(r.color);
            trail.setAlpha(190);
            tmp.reset();
            tmp.moveTo(tp[0], tp[1]);
            for (int i = 1; i < n; i++) {
                if (cum[i] <= tail) continue;
                if (cum[i] >= head) break;
                tmp.lineTo(xs[i], ys[i]);
            }
            tmp.lineTo(hp[0], hp[1]);
            c.drawPath(tmp, trail);

            fill.setColor(r.color);
            fill.setAlpha(60);
            c.drawCircle(hp[0], hp[1], 11f * density, fill);
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(255);
            c.drawCircle(hp[0], hp[1], 6.5f * density, fill);
            fill.setColor(r.color);
            c.drawCircle(hp[0], hp[1], 4.5f * density, fill);

            // pulse at the citizen
            float pulse = (phase * 2f + idx * 0.17f) % 1f;
            ring.setColor(r.color);
            ring.setAlpha((int) (200 * (1f - pulse)));
            c.drawCircle(xs[n - 1], ys[n - 1], (6f + 18f * pulse) * density, ring);
            // start marker at the station
            fill.setColor(r.color);
            fill.setAlpha(255);
            c.drawCircle(xs[0], ys[0], 4f * density, fill);
        }
    }

    private static float[] at(float[] xs, float[] ys, float[] cum, float d) {
        int n = xs.length;
        if (d <= 0f) return new float[]{xs[0], ys[0]};
        for (int i = 1; i < n; i++) {
            if (cum[i] >= d) {
                float seg = cum[i] - cum[i - 1];
                float f = seg <= 0f ? 0f : (d - cum[i - 1]) / seg;
                return new float[]{xs[i - 1] + (xs[i] - xs[i - 1]) * f, ys[i - 1] + (ys[i] - ys[i - 1]) * f};
            }
        }
        return new float[]{xs[n - 1], ys[n - 1]};
    }
}
