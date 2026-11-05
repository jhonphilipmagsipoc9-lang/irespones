package com.rescuelink.app.models;

public class StatusEvent {
    private final IncidentStatus status;
    private final long timeMillis;
    private final String note;

    public StatusEvent(IncidentStatus status, long timeMillis, String note) {
        this.status = status;
        this.timeMillis = timeMillis;
        this.note = note;
    }

    public IncidentStatus getStatus() { return status; }
    public long getTimeMillis() { return timeMillis; }
    public String getNote() { return note; }
}
