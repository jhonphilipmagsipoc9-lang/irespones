package com.rescuelink.app.activities;

/** Implemented by the role shells so fragments can switch tabs without knowing menu ids. */
public interface TabHost {
    void showMapTab();
    void showReportsTab();
}
