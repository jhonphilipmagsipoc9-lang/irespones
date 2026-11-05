package com.rescuelink.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.rescuelink.app.R;
import com.rescuelink.app.fragments.ProfileFragment;

/** Hosts the profile screen for roles that have no profile tab. */
public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.rescuelink.app.utils.SessionManager.requireLogin(this)) return;
        setContentView(R.layout.activity_host);
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new ProfileFragment()).commit();
        }
    }
}
