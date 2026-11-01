package com.rescuelink.app.models;

public class EmergencyContact {
    private final String name;
    private final String number;
    private final String relation;

    public EmergencyContact(String name, String number, String relation) {
        this.name = name;
        this.number = number;
        this.relation = relation;
    }

    public String getName() { return name; }
    public String getNumber() { return number; }
    public String getRelation() { return relation; }
}
