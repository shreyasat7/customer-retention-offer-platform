package com.crop.retention.web;

import com.crop.retention.model.OfferType;
import com.crop.retention.model.RetentionOffer;
import com.crop.retention.model.Segment;
import com.crop.retention.service.OfferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/offers")
public class OfferController {

    private static final int PAGE_SIZE = 10;

    private final OfferService offers;

    public OfferController(OfferService offers) {
        this.offers = offers;
    }

    /** Values the segment and offer-type selects are built from. */
    @ModelAttribute("segments")
    public Segment[] segments() {
        return Segment.values();
    }

    @ModelAttribute("offerTypes")
    public OfferType[] offerTypes() {
        return OfferType.values();
    }

    // ------------------------------------------------------------------ read

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<RetentionOffer> results = offers.list(
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "updatedAt")));
        model.addAttribute("results", results);
        return "offers/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("offer", offers.require(id));
        return "offers/detail";
    }

    // ---------------------------------------------------------------- create

    @GetMapping("/new")
    @PreAuthorize("hasRole('AGENT')")
    public String newForm(Model model) {
        model.addAttribute("offer", new RetentionOffer());
        return "offers/form";
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENT')")
    public String create(@Valid @ModelAttribute("offer") RetentionOffer offer,
                         BindingResult binding,
                         Principal principal,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            // redisplay the form with field-level messages; nothing is persisted
            return "offers/form";
        }
        RetentionOffer saved = offers.create(offer, principal.getName());
        flash.addFlashAttribute("created", saved.getCaseRef());
        return "redirect:/offers/" + saved.getId();
    }
}
