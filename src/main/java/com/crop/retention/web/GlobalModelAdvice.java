package com.crop.retention.web;

import com.crop.retention.model.OfferStatus;
import com.crop.retention.repository.RetentionOfferRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Values every page needs. Keeping them here means the sidebar badge and the footer
 * version do not have to be re-added to each controller.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final RetentionOfferRepository repository;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    public GlobalModelAdvice(RetentionOfferRepository repository) {
        this.repository = repository;
    }

    /** Badge on the "Approvals" sidebar item. */
    @ModelAttribute("pendingCount")
    public long pendingCount() {
        return repository.countByStatus(OfferStatus.PENDING_APPROVAL);
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return appVersion;
    }
}
