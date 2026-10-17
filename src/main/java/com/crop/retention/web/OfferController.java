package com.crop.retention.web;

import com.crop.retention.model.OfferStatus;
import com.crop.retention.model.OfferType;
import com.crop.retention.model.RetentionOffer;
import com.crop.retention.model.Segment;
import com.crop.retention.service.OfferService;
import com.crop.retention.service.OfferWorkflow;
import com.crop.retention.service.WorkflowAction;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/offers")
public class OfferController {

    private static final int PAGE_SIZE = 10;

    /**
     * Default ordering for the offer list. The list is the agent's work queue, so the
     * customers most likely to churn come first and recent activity is only the
     * tie-breaker (issue #17).
     */
    private static final Sort HIGHEST_RISK_FIRST =
            Sort.by(Sort.Order.desc("churnRisk"), Sort.Order.desc("updatedAt"));

    private final OfferService offers;
    private final OfferWorkflow workflow;

    public OfferController(OfferService offers, OfferWorkflow workflow) {
        this.offers = offers;
        this.workflow = workflow;
    }

    @ModelAttribute("segments")
    public Segment[] segments() {
        return Segment.values();
    }

    @ModelAttribute("offerTypes")
    public OfferType[] offerTypes() {
        return OfferType.values();
    }

    @ModelAttribute("statuses")
    public OfferStatus[] statuses() {
        return OfferStatus.values();
    }

    // ------------------------------------------------------------------ read

    /**
     * List with combined search and filters (M3 + M6). Every criterion is optional, and
     * the active ones are echoed back into the model so that the controls keep their
     * values and the pagination links carry them (US-06 AC4).
     */
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) OfferStatus status,
                       @RequestParam(required = false) Segment segment,
                       @RequestParam(required = false) Integer minRisk,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {

        // Conflict resolution (develop <- bugfix/17): this branch added the search and
        // filter parameters while develop replaced the inline Sort with the named
        // HIGHEST_RISK_FIRST constant. Both are kept - the search signature from here,
        // the named ordering from develop - because they are the same intent expressed
        // in two places, and the constant says why the ordering exists.
        Page<RetentionOffer> results = offers.search(q, status, segment, minRisk,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, HIGHEST_RISK_FIRST));

        model.addAttribute("results", results);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("segment", segment);
        model.addAttribute("minRisk", minRisk);
        model.addAttribute("filtered",
                (q != null && !q.isBlank()) || status != null || segment != null || (minRisk != null && minRisk > 0));
        return "offers/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        RetentionOffer offer = offers.require(id);
        model.addAttribute("offer", offer);
        // NOT "actions": the layout fragment takes a parameter of that name, which would
        // shadow this model attribute inside the rendered page body.
        model.addAttribute("workflowActions", workflow.availableFor(offer, roleNames(auth)));
        return "offers/detail";
    }

    // ------------------------------------------------------------ create/edit

    @GetMapping("/new")
    @PreAuthorize("hasRole('AGENT')")
    public String newForm(Model model) {
        model.addAttribute("offer", new RetentionOffer());
        model.addAttribute("editing", false);
        return "offers/form";
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENT')")
    public String create(@Valid @ModelAttribute("offer") RetentionOffer offer,
                         BindingResult binding,
                         Principal principal,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            return "offers/form";
        }
        RetentionOffer saved = offers.create(offer, principal.getName());
        flash.addFlashAttribute("created", saved.getCaseRef());
        return "redirect:/offers/" + saved.getId();
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('AGENT')")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes flash) {
        RetentionOffer offer = offers.require(id);
        if (!offer.isEditable()) {
            flash.addFlashAttribute("error",
                    "%s cannot be edited while it is in status %s"
                            .formatted(offer.getCaseRef(), offer.getStatus().getLabel()));
            return "redirect:/offers/" + id;
        }
        model.addAttribute("offer", offer);
        model.addAttribute("editing", true);
        return "offers/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('AGENT')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("offer") RetentionOffer offer,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", true);
            return "offers/form";
        }
        // the service refuses the edit if the offer has left DRAFT/REJECTED, so a direct
        // POST cannot bypass the rule the hidden Edit button implies (US-05 AC2)
        RetentionOffer saved = offers.update(id, offer);
        flash.addFlashAttribute("message", saved.getCaseRef() + " updated.");
        return "redirect:/offers/" + id;
    }

    // -------------------------------------------------------------- workflow

    /**
     * Single entry point for every status transition. The controller does not decide
     * anything: it hands the action to {@link OfferWorkflow} and reports the outcome.
     */
    @PostMapping("/{id}/{action}")
    public String transition(@PathVariable Long id,
                             @PathVariable String action,
                             @RequestParam(required = false) String comment,
                             Authentication auth,
                             RedirectAttributes flash) {

        WorkflowAction requested;
        try {
            requested = WorkflowAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", "Unknown action: " + action);
            return "redirect:/offers/" + id;
        }

        RetentionOffer offer = offers.require(id);
        workflow.apply(offer, requested, auth.getName(), roleNames(auth), comment);
        offers.save(offer);

        flash.addFlashAttribute("message",
                "%s is now %s.".formatted(offer.getCaseRef(), offer.getStatus().getLabel()));
        return "redirect:/offers/" + id;
    }

    private static List<String> roleNames(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
