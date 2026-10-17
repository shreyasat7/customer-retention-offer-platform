package com.crop.retention.service;

/** The actions a user can take on a retention offer. */
public enum WorkflowAction {

    SUBMIT("Submit for approval", "AGENT"),
    APPROVE("Approve", "MANAGER"),
    REJECT("Reject", "MANAGER"),
    REVISE("Revise", "AGENT"),
    SEND("Send to customer", "AGENT"),
    ACCEPT("Customer accepted", "AGENT"),
    DECLINE("Customer declined", "AGENT");

    private final String label;
    private final String requiredRole;

    WorkflowAction(String label, String requiredRole) {
        this.label = label;
        this.requiredRole = requiredRole;
    }

    public String getLabel() {
        return label;
    }

    /** Role name without the {@code ROLE_} prefix. */
    public String getRequiredRole() {
        return requiredRole;
    }
}
