package com.rescuelink.app.models;

/** One chat message in the conversation attached to a report. */
public class IncidentMessage {
    private final String reference;
    private final String senderUsername;
    private final String senderName;
    private final String senderRole;
    private final String text;
    private final long timeMillis;

    public IncidentMessage(String reference, String senderUsername, String senderName,
                           String senderRole, String text, long timeMillis) {
        this.reference = reference;
        this.senderUsername = senderUsername;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.text = text;
        this.timeMillis = timeMillis;
    }

    public String getReference() { return reference; }
    public String getSenderUsername() { return senderUsername; }
    public String getSenderName() { return senderName; }
    public String getSenderRole() { return senderRole; }
    public String getText() { return text; }
    public long getTimeMillis() { return timeMillis; }
}
