package com.rescuelink.app.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.rescuelink.app.R;

/**
 * Light, GPU-friendly motion shared by every screen. Only alpha, translation and scale are animated, and nothing here
 * ever blocks or delays a tap, so emergency buttons stay instantly usable while a screen is animating in.
 */
public final class UiAnim {
    private static final int ENTER_MS = 260;
    private static final int STAGGER_MS = 40;
    private static final int MAX_STAGGER = 9;

    private static final String PREFS = "rescuelink_settings";
    private static boolean reduced;

    private UiAnim() { }

    /** Reads the saved "Reduce motion" choice. Called once from the Application class. */
    public static void load(android.content.Context c) {
        reduced = c.getApplicationContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean("reduce_motion", false);
    }

    public static boolean isReduced() { return reduced; }

    public static void setReduced(android.content.Context c, boolean on) {
        reduced = on;
        c.getApplicationContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("reduce_motion", on).apply();
    }

    // ------------------------------------------------------------------ press feedback

    /** Slight scale-down while a finger is on the view. Never consumes the touch, so clicks and ripples are untouched. */
    public static void press(final View v) {
        if (reduced || v == null || v.getTag(R.id.tag_press) != null) return;
        v.setTag(R.id.tag_press, Boolean.TRUE);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** Adds press feedback to every button and clickable card under root (skips map views). */
    public static void decorate(View root) {
        if (root == null) return;
        if (root.getClass().getName().contains("MapView")) return;
        if (root instanceof RecyclerView) {
            watchRecycler((RecyclerView) root);
            return;
        }
        if ((root instanceof MaterialButton || root instanceof MaterialCardView) && root.isClickable()) {
            press(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) decorate(g.getChildAt(i));
        }
    }

    // ------------------------------------------------------------------ entrance

    /** Fades and lifts the first few blocks of a screen in, one after another. Runs once, before the first frame. */
    public static void enter(final View screen) {
        if (reduced || screen == null || screen.getTag(R.id.tag_entered) != null) return;
        screen.setTag(R.id.tag_entered, Boolean.TRUE);
        screen.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (screen.getViewTreeObserver().isAlive()) screen.getViewTreeObserver().removeOnPreDrawListener(this);
                ViewGroup list = contentList(screen);
                if (list == null) return true;
                float lift = 16f * screen.getResources().getDisplayMetrics().density;
                int n = Math.min(list.getChildCount(), MAX_STAGGER);
                for (int i = 0; i < n; i++) {
                    View c = list.getChildAt(i);
                    if (c.getVisibility() != View.VISIBLE) continue;
                    c.setAlpha(0f);
                    c.setTranslationY(lift);
                    c.animate().alpha(1f).translationY(0f).setStartDelay(i * STAGGER_MS)
                            .setDuration(ENTER_MS).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                }
                return true;
            }
        });
    }

    /** The vertical list of blocks inside the first ScrollView found, or the root itself when it is a LinearLayout. */
    private static ViewGroup contentList(View v) {
        if (v instanceof ScrollView && ((ScrollView) v).getChildCount() > 0
                && ((ScrollView) v).getChildAt(0) instanceof ViewGroup) {
            return (ViewGroup) ((ScrollView) v).getChildAt(0);
        }
        if (v instanceof android.widget.LinearLayout
                && ((android.widget.LinearLayout) v).getOrientation() == android.widget.LinearLayout.VERTICAL) {
            return (ViewGroup) v;
        }
        return null;
    }

    /** Finds the first ScrollView near the top of an activity layout and animates its content in. */
    public static void enterActivity(View decor) {
        View sv = findScroll(decor, 0);
        if (sv != null) enter(sv);
    }

    private static View findScroll(View v, int depth) {
        if (v instanceof ScrollView) return v;
        if (depth > 4 || !(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            View r = findScroll(g.getChildAt(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------ lists

    private static void watchRecycler(final RecyclerView rv) {
        if (rv.getTag(R.id.tag_decorated) != null) return;
        rv.setTag(R.id.tag_decorated, Boolean.TRUE);
        rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View child) {
                press(child);
                if (reduced || rv.getChildAdapterPosition(child) > 7) return;
                child.setAlpha(0f);
                child.setTranslationY(12f * child.getResources().getDisplayMetrics().density);
                child.animate().alpha(1f).translationY(0f).setDuration(220)
                        .setStartDelay(Math.max(0, rv.getChildAdapterPosition(child)) * 30L).start();
            }

            @Override
            public void onChildViewDetachedFromWindow(View child) {
                // A recycled row must never keep a half-finished animation state.
                child.animate().cancel();
                child.setAlpha(1f);
                child.setTranslationY(0f);
                child.setScaleX(1f);
                child.setScaleY(1f);
            }
        });
    }

    // ------------------------------------------------------------------ attention

    /** Endless soft pulse (scale + fade) for a ring behind the SOS button. Call stop() when the screen goes away. */
    public static Animator pulse(View ring) {
        if (reduced) return null;
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.25f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.25f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f));
        a.setDuration(1600);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setInterpolator(new DecelerateInterpolator());
        a.start();
        return a;
    }

    public static void stop(Animator a) {
        if (a != null) a.cancel();
    }

    /** Small horizontal shake for a field or card that failed validation. */
    public static void shake(View v) {
        float d = 8f * v.getResources().getDisplayMetrics().density;
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0, d, -d, d * 0.6f, -d * 0.6f, 0).setDuration(340).start();
    }

    /** Fades a view in (or out) and toggles visibility at the end. */
    public static void fadeTo(final View v, final boolean show) {
        if (v == null) return;
        if (show) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1f).setDuration(180).setListener(null).start();
        } else {
            v.animate().alpha(0f).setDuration(140).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    v.setVisibility(View.GONE);
                    v.setAlpha(1f);
                }
            }).start();
        }
    }

    /** Counts a number up to its value, for dashboard tiles. */
    public static void countUp(final android.widget.TextView tv, final int to) {
        if (reduced || to <= 0) { tv.setText(String.valueOf(to)); return; }
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(0, to);
        va.setDuration(600);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                tv.setText(String.valueOf((Integer) a.getAnimatedValue()));
            }
        });
        va.start();
    }
}
