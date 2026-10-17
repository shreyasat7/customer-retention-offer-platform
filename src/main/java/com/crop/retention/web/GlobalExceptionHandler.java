package com.crop.retention.web;

import com.crop.retention.service.OfferService;
import com.crop.retention.service.OfferWorkflow;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Turns domain exceptions into the styled error page instead of a stack trace
 * (acceptance criterion US-04 AC3).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OfferService.OfferNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String offerNotFound(OfferService.OfferNotFoundException ex, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("error", "Offer not found");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    /**
     * A refused transition or a refused edit is a business outcome, not a crash: the user
     * goes back to the offer with the reason, and the record is unchanged.
     */
    @ExceptionHandler({OfferWorkflow.IllegalTransitionException.class,
                       OfferService.OfferNotEditableException.class})
    public String businessRuleRefused(RuntimeException ex,
                                      HttpServletRequest request,
                                      RedirectAttributes flash) {
        flash.addFlashAttribute("error", ex.getMessage());
        String path = request.getRequestURI();
        // /offers/{id}/... -> /offers/{id}
        String[] parts = path.split("/");
        String id = parts.length > 2 ? parts[2] : "";
        return "redirect:/offers/" + id;
    }
}
