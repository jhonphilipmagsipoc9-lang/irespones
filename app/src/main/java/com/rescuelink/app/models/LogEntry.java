package com.rescuelink.app.models;

/** One line in the admin activity log. */
public class LogEntry {
    private final long timeMillis;
    private final String actor;
    private final String action;

    public LogEntry(long timeMillis, String actor, String action) {
        this.timeMillis = timeMillis;
        this.actor = actor;
        this.action = action;
    }

    public long getTimeMillis() { return timeMillis; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
}
