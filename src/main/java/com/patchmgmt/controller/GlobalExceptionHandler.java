package com.patchmgmt.controller;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.exception.ResourceNotFoundException;
import com.patchmgmt.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final IntegrationProperties integrationProperties;

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("errorCode", "404");
        model.addAttribute("errorTitle", "Resource Not Found");
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("demoMode", integrationProperties.isDemoMode());
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        log.warn("Access denied: {}", ex.getMessage());
        model.addAttribute("errorCode", "403");
        model.addAttribute("errorTitle", "Access Denied");
        model.addAttribute("errorMessage", "You don't have permission to access this resource.");
        model.addAttribute("demoMode", integrationProperties.isDemoMode());
        return "error/403";
    }

    @ExceptionHandler(ValidationException.class)
    public String handleValidation(ValidationException ex, HttpServletRequest request,
                                   RedirectAttributes ra) {
        log.warn("Validation error: {}", ex.getMessage());
        ra.addFlashAttribute("error", ex.getMessage());
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex, HttpServletRequest request,
                                     RedirectAttributes ra) {
        log.warn("Illegal state: {}", ex.getMessage());
        ra.addFlashAttribute("error", ex.getMessage());
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @ExceptionHandler(BindException.class)
    public String handleBindException(BindException ex, HttpServletRequest request,
                                      RedirectAttributes ra) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst().orElse("Validation failed");
        ra.addFlashAttribute("error", msg);
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneral(Exception ex, Model model) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        model.addAttribute("errorCode", "500");
        model.addAttribute("errorTitle", "Internal Server Error");
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again or contact support.");
        model.addAttribute("errorDetail", ex.getMessage());
        model.addAttribute("demoMode", integrationProperties.isDemoMode());
        return "error/500";
    }
}
