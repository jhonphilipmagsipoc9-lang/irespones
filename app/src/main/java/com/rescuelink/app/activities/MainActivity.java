package com.rescuelink.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuelink.app.R;
import com.rescuelink.app.models.User;
import com.rescuelink.app.utils.SessionManager;

/**
 * Opens the app. Someone who is already signed in goes straight to their home screen with no splash,
 * because in an emergency every second counts. Everyone else sees a very short splash, then Login.
 */
public class MainActivity extends AppCompatActivity {
    private static final long SPLASH_MS = 700;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        User me = SessionManager.getCurrentUser();
        if (me != null && me.isActive()) {
            LoginActivity.openHomeFor(this, me);
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        View content = findViewById(R.id.splash_content);
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(350).start();
        content.postDelayed(new Runnable() {
            @Override public void run() {
                if (isFinishing()) return;
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        }, SPLASH_MS);
    }
}
