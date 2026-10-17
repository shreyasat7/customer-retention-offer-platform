package com.crop.retention.model;

/**
 * The seven states a retention offer can occupy.
 *
 * <p>The order of the constants is the order of the funnel shown on the dashboard.
 * Legal movement between states is decided by {@link com.crop.retention.service.OfferWorkflow},
 * not by this enum — the enum only describes the states themselves.</p>
 */
public enum OfferStatus {

    DRAFT("Draft", "draft", false),
    PENDING_APPROVAL("Pending approval", "pending", false),
    APPROVED("Approved", "approved", false),
    REJECTED("Rejected", "rejected", false),
    SENT("Sent to customer", "sent", false),
    ACCEPTED("Accepted", "accepted", true),
    DECLINED("Declined", "declined", true);

    private final String label;
    private final String cssClass;
    private final boolean terminal;

    OfferStatus(String label, String cssClass, boolean terminal) {
        this.label = label;
        this.cssClass = cssClass;
        this.terminal = terminal;
    }

    public String getLabel() {
        return label;
    }

    /** Suffix used by the status pill styles in app.css, e.g. {@code pill--approved}. */
    public String getCssClass() {
        return cssClass;
    }

    /** True when the customer has responded and the offer can no longer move. */
    public boolean isTerminal() {
        return terminal;
    }
}
