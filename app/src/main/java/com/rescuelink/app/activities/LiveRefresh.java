package com.rescuelink.app.activities;

/** A screen that redraws itself when new data arrives from other phones. */
public interface LiveRefresh {
    void onLiveUpdate();
}
