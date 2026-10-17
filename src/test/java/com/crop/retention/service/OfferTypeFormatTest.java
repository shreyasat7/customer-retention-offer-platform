package com.crop.retention.service;

import com.crop.retention.model.OfferType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression tests for the pluralisation defect found in the PR #16 review. */
class OfferTypeFormatTest {

    @Test
    @DisplayName("a single free month is singular, more than one is plural")
    void freeMonthsPluralise() {
        assertThat(OfferType.FREE_MONTHS.format(new BigDecimal("1.00"))).isEqualTo("1 month");
        assertThat(OfferType.FREE_MONTHS.format(new BigDecimal("2.00"))).isEqualTo("2 months");
        assertThat(OfferType.FREE_MONTHS.format(new BigDecimal("3"))).isEqualTo("3 months");
    }

    @Test
    @DisplayName("each offer type renders its own unit")
    void unitsPerType() {
        assertThat(OfferType.DISCOUNT_PERCENT.format(new BigDecimal("20.00"))).isEqualTo("20%");
        assertThat(OfferType.PLAN_UPGRADE.format(BigDecimal.ONE)).isEqualTo("1 tier");
        assertThat(OfferType.LOYALTY_CREDIT.format(new BigDecimal("15000"))).isEqualTo("₹15,000");
    }

    @Test
    @DisplayName("fractional values keep two decimals, whole values drop them")
    void decimalHandling() {
        assertThat(OfferType.DISCOUNT_PERCENT.format(new BigDecimal("12.50"))).isEqualTo("12.50%");
        assertThat(OfferType.DISCOUNT_PERCENT.format(new BigDecimal("12.00"))).isEqualTo("12%");
    }

    @Test
    @DisplayName("a null value renders as empty rather than throwing")
    void nullSafe() {
        assertThat(OfferType.DISCOUNT_PERCENT.format(null)).isEmpty();
    }
}
