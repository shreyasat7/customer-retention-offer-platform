package com.crop.retention.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A retention offer raised for a single at-risk customer.
 *
 * <p>This is the only aggregate in the MVP. It carries the commercial detail of the
 * offer, its position in the approval workflow, and the audit fields that let Finance
 * and Audit answer "who approved this discount, and when?".</p>
 */
@Entity
@Table(name = "retention_offer", indexes = {
        @Index(name = "idx_offer_status", columnList = "status"),
        @Index(name = "idx_offer_customer", columnList = "customerName")
})
public class RetentionOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-facing reference, e.g. CRO-0042. Assigned on first save. */
    @Column(unique = true, length = 16)
    private String caseRef;

    // ---------------------------------------------------------------- customer

    @NotBlank(message = "Customer name is required")
    @Size(max = 120, message = "Customer name must be 120 characters or fewer")
    @Column(nullable = false, length = 120)
    private String customerName;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String customerEmail;

    @NotBlank(message = "Account ID is required")
    @Pattern(regexp = "[A-Za-z0-9-]{4,24}", message = "Account ID must be 4-24 letters, digits or hyphens")
    @Column(nullable = false, length = 24)
    private String accountId;

    @NotNull(message = "Select a customer segment")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Segment segment;

    /** Churn-risk score supplied by the analytics team, 0 (safe) to 100 (certain churn). */
    @NotNull(message = "Churn risk score is required")
    @Min(value = 0, message = "Churn risk must be between 0 and 100")
    @Max(value = 100, message = "Churn risk must be between 0 and 100")
    @Column(nullable = false)
    private Integer churnRisk;

    /** What the customer pays each month today, in INR. */
    @NotNull(message = "Monthly value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Monthly value must be greater than zero")
    @Digits(integer = 9, fraction = 2)
    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal monthlyValue;

    // ------------------------------------------------------------------- offer

    @NotNull(message = "Select an offer type")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OfferType offerType;

    @NotNull(message = "Offer value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Offer value must be greater than zero")
    @Digits(integer = 9, fraction = 2)
    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal offerValue;

    @Size(max = 1000, message = "Notes must be 1000 characters or fewer")
    @Column(length = 1000)
    private String notes;

    // ---------------------------------------------------------------- workflow

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OfferStatus status = OfferStatus.DRAFT;

    // ------------------------------------------------------------------- audit

    @Column(nullable = false, length = 60)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 60)
    private String submittedBy;

    private LocalDateTime submittedAt;

    @Column(length = 60)
    private String decisionBy;

    private LocalDateTime decisionAt;

    @Column(length = 500)
    private String decisionComment;

    @Column(length = 60)
    private String sentBy;

    private LocalDateTime sentAt;

    private LocalDateTime outcomeAt;

    // --------------------------------------------------------------- lifecycle

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------- derived, for UI

    /** Risk band used by the dashboard breakdown and the list colour coding. */
    @Transient
    public String getRiskBand() {
        if (churnRisk == null) {
            return "Unknown";
        }
        if (churnRisk >= 80) {
            return "Critical";
        }
        if (churnRisk >= 60) {
            return "High";
        }
        if (churnRisk >= 40) {
            return "Medium";
        }
        return "Low";
    }

    @Transient
    public String getRiskCssClass() {
        return getRiskBand().toLowerCase();
    }

    /**
     * Monthly revenue this offer protects if the customer accepts, i.e. the current
     * monthly value less the monthly cost of the incentive.
     */
    @Transient
    public BigDecimal getRetainedMonthlyValue() {
        if (monthlyValue == null || offerValue == null || offerType == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = switch (offerType) {
            case DISCOUNT_PERCENT -> monthlyValue.multiply(offerValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            // a free month is amortised across a 12-month contract
            case FREE_MONTHS -> monthlyValue.multiply(offerValue)
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case LOYALTY_CREDIT -> offerValue.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case PLAN_UPGRADE -> BigDecimal.ZERO;
        };
        BigDecimal retained = monthlyValue.subtract(cost);
        return retained.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Transient
    public boolean isEditable() {
        return status == OfferStatus.DRAFT || status == OfferStatus.REJECTED;
    }

    // --------------------------------------------------------- getters/setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCaseRef() { return caseRef; }
    public void setCaseRef(String caseRef) { this.caseRef = caseRef; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public Segment getSegment() { return segment; }
    public void setSegment(Segment segment) { this.segment = segment; }

    public Integer getChurnRisk() { return churnRisk; }
    public void setChurnRisk(Integer churnRisk) { this.churnRisk = churnRisk; }

    public BigDecimal getMonthlyValue() { return monthlyValue; }
    public void setMonthlyValue(BigDecimal monthlyValue) { this.monthlyValue = monthlyValue; }

    public OfferType getOfferType() { return offerType; }
    public void setOfferType(OfferType offerType) { this.offerType = offerType; }

    public BigDecimal getOfferValue() { return offerValue; }
    public void setOfferValue(BigDecimal offerValue) { this.offerValue = offerValue; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getDecisionBy() { return decisionBy; }
    public void setDecisionBy(String decisionBy) { this.decisionBy = decisionBy; }

    public LocalDateTime getDecisionAt() { return decisionAt; }
    public void setDecisionAt(LocalDateTime decisionAt) { this.decisionAt = decisionAt; }

    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }

    public String getSentBy() { return sentBy; }
    public void setSentBy(String sentBy) { this.sentBy = sentBy; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getOutcomeAt() { return outcomeAt; }
    public void setOutcomeAt(LocalDateTime outcomeAt) { this.outcomeAt = outcomeAt; }
}
