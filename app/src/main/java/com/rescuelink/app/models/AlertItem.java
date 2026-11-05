package com.rescuelink.app.models;

/** A public advisory broadcast to citizens and responders. */
public class AlertItem {
    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int CRITICAL = 2;

    private final String id;
    private final String title;
    private final String message;
    private final int level;
    private final long timeMillis;
    private final String author;

    public AlertItem(String id, String title, String message, int level, long timeMillis, String author) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.level = level;
        this.timeMillis = timeMillis;
        this.author = author;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public int getLevel() { return level; }
    public long getTimeMillis() { return timeMillis; }
    public String getAuthor() { return author; }

    public String levelLabel() {
        return level == CRITICAL ? "Critical" : level == WARNING ? "Warning" : "Advisory";
    }
}
