package com.crop.retention.model;

/** Commercial tier the customer sits in. Drives how much discount is reasonable. */
public enum Segment {

    BASIC("Basic"),
    PLUS("Plus"),
    PREMIUM("Premium"),
    ENTERPRISE("Enterprise");

    private final String label;

    Segment(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
