package com.crop.retention.model;

/** The kind of incentive being offered to retain the customer. */
public enum OfferType {

    DISCOUNT_PERCENT("Discount %", "%"),
    FREE_MONTHS("Free months", " months"),
    PLAN_UPGRADE("Free plan upgrade", " tier"),
    LOYALTY_CREDIT("Loyalty credit", " INR");

    private final String label;
    private final String unit;

    OfferType(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String getLabel() {
        return label;
    }

    /** Suffix shown after the numeric offer value in the UI. */
    public String getUnit() {
        return unit;
    }
}
