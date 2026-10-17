package com.crop.retention.web;

import com.crop.retention.service.OfferService;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

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
}
