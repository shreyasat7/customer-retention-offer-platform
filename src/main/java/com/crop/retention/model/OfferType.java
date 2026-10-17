package com.crop.retention.model;

import java.math.BigDecimal;

/** The kind of incentive being offered to retain the customer. */
public enum OfferType {

    DISCOUNT_PERCENT("Discount %"),
    FREE_MONTHS("Free months"),
    PLAN_UPGRADE("Free plan upgrade"),
    LOYALTY_CREDIT("Loyalty credit");

    private final String label;

    OfferType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Renders an offer value the way a human reads it — {@code 20%}, {@code 1 month},
     * {@code 2 months}, {@code 1 tier}, {@code ₹3,000}.
     *
     * <p>Formatting lives here rather than in the templates so that the list, the detail
     * page and anything added later all agree, and so that singular and plural units are
     * handled in one place.</p>
     */
    public String format(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        String number = trimmed.scale() <= 0
                ? String.format("%,d", trimmed.toBigInteger())
                : String.format("%,.2f", trimmed);
        boolean singular = value.compareTo(BigDecimal.ONE) == 0;

        return switch (this) {
            case DISCOUNT_PERCENT -> number + "%";
            case FREE_MONTHS -> number + (singular ? " month" : " months");
            case PLAN_UPGRADE -> number + (singular ? " tier" : " tiers");
            case LOYALTY_CREDIT -> "₹" + number;
        };
    }
}
