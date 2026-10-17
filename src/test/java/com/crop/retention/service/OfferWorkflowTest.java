package com.crop.retention.service;

import com.crop.retention.model.OfferStatus;
import com.crop.retention.model.OfferType;
import com.crop.retention.model.RetentionOffer;
import com.crop.retention.model.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the state machine.
 *
 * <p>The decisive test is {@link SentIsReachableOnlyFromApproved} — it is the automated
 * proof of success criterion <strong>SC1</strong>: no offer can reach a customer without a
 * manager's approval. Because every transition goes through {@link OfferWorkflow}, that
 * property can be proven here rather than argued in a review.</p>
 */
class OfferWorkflowTest {

    private final OfferWorkflow workflow = new OfferWorkflow();

    private static final List<String> AGENT = List.of("ROLE_AGENT");
    private static final List<String> MANAGER = List.of("ROLE_MANAGER");
    private static final List<String> ADMIN = List.of("ROLE_ADMIN", "ROLE_AGENT", "ROLE_MANAGER");

    private RetentionOffer offerIn(OfferStatus status) {
        RetentionOffer o = new RetentionOffer();
        o.setCaseRef("CRO-0001");
        o.setCustomerName("Test Customer");
        o.setCustomerEmail("test@example.com");
        o.setAccountId("ACC-00001");
        o.setSegment(Segment.PLUS);
        o.setChurnRisk(70);
        o.setMonthlyValue(new BigDecimal("1000.00"));
        o.setOfferType(OfferType.DISCOUNT_PERCENT);
        o.setOfferValue(new BigDecimal("10.00"));
        o.setCreatedBy("agent");
        o.setStatus(status);
        return o;
    }

    // ------------------------------------------------------------ happy path

    @Test
    @DisplayName("the full lifecycle draft -> pending -> approved -> sent -> accepted")
    void fullLifecycle() {
        RetentionOffer o = offerIn(OfferStatus.DRAFT);

        workflow.apply(o, WorkflowAction.SUBMIT, "agent", AGENT, null);
        assertThat(o.getStatus()).isEqualTo(OfferStatus.PENDING_APPROVAL);

        workflow.apply(o, WorkflowAction.APPROVE, "manager", MANAGER, "Within policy");
        assertThat(o.getStatus()).isEqualTo(OfferStatus.APPROVED);

        workflow.apply(o, WorkflowAction.SEND, "agent", AGENT, null);
        assertThat(o.getStatus()).isEqualTo(OfferStatus.SENT);

        workflow.apply(o, WorkflowAction.ACCEPT, "agent", AGENT, null);
        assertThat(o.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(o.getStatus().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("a rejected offer can be revised back to draft")
    void rejectThenRevise() {
        RetentionOffer o = offerIn(OfferStatus.PENDING_APPROVAL);

        workflow.apply(o, WorkflowAction.REJECT, "manager", MANAGER, "Above the discount ceiling");
        assertThat(o.getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(o.getDecisionComment()).isEqualTo("Above the discount ceiling");

        workflow.apply(o, WorkflowAction.REVISE, "agent", AGENT, null);
        assertThat(o.getStatus()).isEqualTo(OfferStatus.DRAFT);
        assertThat(o.isEditable()).isTrue();
    }

    // ------------------------------------------------- SC1: the decisive rule

    @Nested
    @DisplayName("SC1 - an offer reaches a customer only through an approval")
    class SentIsReachableOnlyFromApproved {

        @ParameterizedTest(name = "send is refused from {0}")
        @EnumSource(value = OfferStatus.class,
                names = {"DRAFT", "PENDING_APPROVAL", "REJECTED", "SENT", "ACCEPTED", "DECLINED"})
        void sendIsRefusedFromEveryStatusExceptApproved(OfferStatus status) {
            RetentionOffer o = offerIn(status);

            assertThatThrownBy(() -> workflow.apply(o, WorkflowAction.SEND, "agent", AGENT, null))
                    .isInstanceOf(OfferWorkflow.IllegalTransitionException.class);

            assertThat(o.getStatus())
                    .as("the record must be untouched after a refused transition")
                    .isEqualTo(status);
            assertThat(o.getSentAt()).isNull();
            assertThat(o.getSentBy()).isNull();
        }

        @Test
        @DisplayName("send is allowed from APPROVED, and only from APPROVED")
        void sendIsAllowedOnlyFromApproved() {
            for (OfferStatus status : OfferStatus.values()) {
                boolean legal = workflow.isLegal(status, WorkflowAction.SEND);
                assertThat(legal)
                        .as("SEND legal from %s", status)
                        .isEqualTo(status == OfferStatus.APPROVED);
            }
        }

        @Test
        @DisplayName("an agent cannot approve their own offer")
        void agentCannotApprove() {
            RetentionOffer o = offerIn(OfferStatus.PENDING_APPROVAL);

            assertThatThrownBy(() -> workflow.apply(o, WorkflowAction.APPROVE, "agent", AGENT, "ok"))
                    .isInstanceOf(OfferWorkflow.IllegalTransitionException.class)
                    .hasMessageContaining("MANAGER");

            assertThat(o.getStatus()).isEqualTo(OfferStatus.PENDING_APPROVAL);
            assertThat(o.getDecisionBy()).isNull();
        }
    }

    // ------------------------------------------------------ role enforcement

    @Test
    @DisplayName("a manager cannot send an offer to the customer")
    void managerCannotSend() {
        RetentionOffer o = offerIn(OfferStatus.APPROVED);

        assertThatThrownBy(() -> workflow.apply(o, WorkflowAction.SEND, "manager", MANAGER, null))
                .isInstanceOf(OfferWorkflow.IllegalTransitionException.class)
                .hasMessageContaining("AGENT");

        assertThat(o.getStatus()).isEqualTo(OfferStatus.APPROVED);
    }

    @Test
    @DisplayName("an administrator holds both roles and may do either")
    void adminMayDoBoth() {
        RetentionOffer o = offerIn(OfferStatus.PENDING_APPROVAL);
        workflow.apply(o, WorkflowAction.APPROVE, "admin", ADMIN, "fine");
        workflow.apply(o, WorkflowAction.SEND, "admin", ADMIN, null);
        assertThat(o.getStatus()).isEqualTo(OfferStatus.SENT);
    }

    // ------------------------------------------------------- rejection rules

    @Test
    @DisplayName("a rejection without a reason is refused")
    void rejectionRequiresAComment() {
        RetentionOffer o = offerIn(OfferStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> workflow.apply(o, WorkflowAction.REJECT, "manager", MANAGER, "   "))
                .isInstanceOf(OfferWorkflow.IllegalTransitionException.class)
                .hasMessageContaining("reason");

        assertThat(o.getStatus()).isEqualTo(OfferStatus.PENDING_APPROVAL);
    }

    // ---------------------------------------------------------- audit fields

    @Test
    @DisplayName("every transition stamps the acting user and the time")
    void transitionsStampTheActor() {
        RetentionOffer o = offerIn(OfferStatus.DRAFT);

        workflow.apply(o, WorkflowAction.SUBMIT, "asha", AGENT, null);
        assertThat(o.getSubmittedBy()).isEqualTo("asha");
        assertThat(o.getSubmittedAt()).isNotNull();

        workflow.apply(o, WorkflowAction.APPROVE, "manav", MANAGER, "Approved at 10%");
        assertThat(o.getDecisionBy()).isEqualTo("manav");
        assertThat(o.getDecisionAt()).isNotNull();
        assertThat(o.getDecisionComment()).isEqualTo("Approved at 10%");

        workflow.apply(o, WorkflowAction.SEND, "asha", AGENT, null);
        assertThat(o.getSentBy()).isEqualTo("asha");
        assertThat(o.getSentAt()).isNotNull();
    }

    // -------------------------------------------- terminal states are closed

    @ParameterizedTest(name = "{0} accepts no further action")
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "DECLINED"})
    void terminalStatesAcceptNothing(OfferStatus terminal) {
        assertThat(workflow.actionsFrom(terminal)).isEmpty();
        for (WorkflowAction action : WorkflowAction.values()) {
            assertThat(workflow.isLegal(terminal, action)).isFalse();
        }
    }

    // ------------------------------------------- what the UI is allowed to show

    @Test
    @DisplayName("available actions depend on both status and role")
    void availableActionsDependOnStatusAndRole() {
        RetentionOffer pending = offerIn(OfferStatus.PENDING_APPROVAL);

        assertThat(workflow.availableFor(pending, AGENT)).isEmpty();
        assertThat(workflow.availableFor(pending, MANAGER))
                .containsExactlyInAnyOrder(WorkflowAction.APPROVE, WorkflowAction.REJECT);

        RetentionOffer approved = offerIn(OfferStatus.APPROVED);
        assertThat(workflow.availableFor(approved, MANAGER)).isEmpty();
        assertThat(workflow.availableFor(approved, AGENT)).containsExactly(WorkflowAction.SEND);
    }
}
