package com.crop.retention.service;

import com.crop.retention.model.RetentionOffer;
import com.crop.retention.repository.RetentionOfferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application services for retention offers.
 *
 * <p>Controllers call this class; they never touch the repository directly. Status
 * transitions are deliberately <em>not</em> handled here — they belong to
 * {@code OfferWorkflow}, which is the single authority on what movement is legal.</p>
 */
@Service
public class OfferService {

    private final RetentionOfferRepository repository;

    /** Cheap in-process counter for case references; seeded from the database on first use. */
    private final AtomicInteger caseSequence = new AtomicInteger(0);

    public OfferService(RetentionOfferRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<RetentionOffer> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public RetentionOffer require(Long id) {
        return repository.findById(id).orElseThrow(() -> new OfferNotFoundException(id));
    }

    /**
     * Persists a brand-new offer in {@code DRAFT} with a generated case reference and
     * the raising agent recorded against it.
     */
    @Transactional
    public RetentionOffer create(RetentionOffer offer, String username) {
        offer.setId(null);
        offer.setCaseRef(nextCaseRef());
        offer.setCreatedBy(username);
        // status defaults to DRAFT on the entity; set explicitly so the intent is visible
        offer.setStatus(com.crop.retention.model.OfferStatus.DRAFT);
        return repository.save(offer);
    }

    /**
     * Produces the next free {@code CRO-####} reference. The counter starts from the
     * number of existing rows and skips forward on collision.
     *
     * <p><strong>Constraint:</strong> this is safe within one JVM only. Two application
     * instances sharing the same H2 file would each hold their own counter and could
     * collide, leaving this loop to spin until one of them wins. The deployment is a
     * single container or a single Tomcat instance, so a database sequence would be
     * over-engineering for the MVP — but the limitation is real and is recorded here
     * rather than left to be discovered later.</p>
     */
    private String nextCaseRef() {
        if (caseSequence.get() == 0) {
            caseSequence.set((int) repository.count());
        }
        String candidate;
        do {
            candidate = String.format("CRO-%04d", caseSequence.incrementAndGet());
        } while (repository.existsByCaseRef(candidate));
        return candidate;
    }

    /** Thrown when a requested offer id does not exist; mapped to HTTP 404. */
    public static class OfferNotFoundException extends RuntimeException {
        public OfferNotFoundException(Long id) {
            super("No retention offer with id " + id);
        }
    }
}
