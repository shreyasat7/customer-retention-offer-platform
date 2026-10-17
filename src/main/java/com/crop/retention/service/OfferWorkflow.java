package com.crop.retention.service;

import com.crop.retention.model.OfferStatus;
import com.crop.retention.model.RetentionOffer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

import static com.crop.retention.model.OfferStatus.*;
import static com.crop.retention.service.WorkflowAction.*;

/**
 * The single authority on how a retention offer may move between states.
 *
 * <p>Nothing else in the application is allowed to assign {@link OfferStatus}. Controllers
 * call {@link #apply} and either get a changed record back or an
 * {@link IllegalTransitionException}; they never decide for themselves what is legal.
 * That choke point is what makes success criterion SC1 — <em>no offer reaches a customer
 * without a manager's approval</em> — provable by a unit test rather than by inspection.</p>
 *
 * <p>The table below is the whole state machine. Any (status, action) pair absent from it
 * is refused.</p>
 *
 * <pre>
 *   DRAFT            --submit (AGENT)----> PENDING_APPROVAL
 *   PENDING_APPROVAL --approve (MANAGER)--> APPROVED
 *   PENDING_APPROVAL --reject (MANAGER)---> REJECTED
 *   REJECTED         --revise (AGENT)-----> DRAFT
 *   APPROVED         --send (AGENT)-------> SENT
 *   SENT             --accept (AGENT)-----> ACCEPTED   (terminal)
 *   SENT             --decline (AGENT)----> DECLINED   (terminal)
 * </pre>
 */
@Component
public class OfferWorkflow {

    /** The complete transition table. Absence means "refused". */
    private static final Map<OfferStatus, Map<WorkflowAction, OfferStatus>> TRANSITIONS = Map.of(
            DRAFT,            Map.of(SUBMIT, PENDING_APPROVAL),
            PENDING_APPROVAL, Map.of(APPROVE, APPROVED, REJECT, REJECTED),
            REJECTED,         Map.of(REVISE, DRAFT),
            APPROVED,         Map.of(SEND, SENT),
            SENT,             Map.of(ACCEPT, ACCEPTED, DECLINE, DECLINED),
            ACCEPTED,         Map.of(),
            DECLINED,         Map.of()
    );

    /** Actions legal from a status, ignoring who is asking. */
    public Set<WorkflowAction> actionsFrom(OfferStatus status) {
        return TRANSITIONS.getOrDefault(status, Map.of()).keySet();
    }

    /**
     * Actions this user may actually perform on this offer — legal from the current status
     * <em>and</em> permitted for one of the roles held. This drives which buttons the
     * detail page renders; it is a convenience, not the control. {@link #apply} re-checks.
     */
    public List<WorkflowAction> availableFor(RetentionOffer offer, Collection<String> roles) {
        Set<String> plain = new HashSet<>();
        for (String r : roles) {
            plain.add(r.startsWith("ROLE_") ? r.substring(5) : r);
        }
        List<WorkflowAction> available = new ArrayList<>();
        for (WorkflowAction action : actionsFrom(offer.getStatus())) {
            if (plain.contains(action.getRequiredRole())) {
                available.add(action);
            }
        }
        available.sort(Comparator.comparing(Enum::ordinal));
        return available;
    }

    public boolean isLegal(OfferStatus from, WorkflowAction action) {
        return TRANSITIONS.getOrDefault(from, Map.of()).containsKey(action);
    }

    /**
     * Applies an action, stamping the acting user and the time onto the record.
     *
     * @throws IllegalTransitionException when the action is not legal from the current
     *                                    status, or the caller does not hold the required role
     */
    public RetentionOffer apply(RetentionOffer offer,
                                WorkflowAction action,
                                String username,
                                Collection<String> roles,
                                String comment) {

        OfferStatus from = offer.getStatus();
        OfferStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(action);

        if (to == null) {
            throw new IllegalTransitionException(
                    "Cannot %s an offer in status %s".formatted(action.getLabel().toLowerCase(), from.getLabel()));
        }

        Set<String> plain = new HashSet<>();
        for (String r : roles) {
            plain.add(r.startsWith("ROLE_") ? r.substring(5) : r);
        }
        if (!plain.contains(action.getRequiredRole())) {
            throw new IllegalTransitionException(
                    "%s requires the %s role".formatted(action.getLabel(), action.getRequiredRole()));
        }

        if (action == REJECT && (comment == null || comment.isBlank())) {
            throw new IllegalTransitionException("A rejection must carry a reason");
        }

        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case SUBMIT -> {
                offer.setSubmittedBy(username);
                offer.setSubmittedAt(now);
            }
            case APPROVE, REJECT -> {
                offer.setDecisionBy(username);
                offer.setDecisionAt(now);
                offer.setDecisionComment(comment == null || comment.isBlank() ? null : comment.trim());
            }
            case REVISE -> {
                // the offer goes back to the agent; the previous decision stays on the
                // record as history, but submission is cleared so the trail stays truthful
                offer.setSubmittedBy(null);
                offer.setSubmittedAt(null);
            }
            case SEND -> {
                offer.setSentBy(username);
                offer.setSentAt(now);
            }
            case ACCEPT, DECLINE -> offer.setOutcomeAt(now);
        }

        offer.setStatus(to);
        offer.setUpdatedAt(now);
        return offer;
    }

    /** Raised when a caller asks for a transition the state machine does not allow. */
    public static class IllegalTransitionException extends RuntimeException {
        public IllegalTransitionException(String message) {
            super(message);
        }
    }
}
