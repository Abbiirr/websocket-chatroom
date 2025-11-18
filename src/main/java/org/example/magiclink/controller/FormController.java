package org.example.magiclink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.magiclink.service.MagicLinkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/form")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Form Registration", description = "Magic link with OAuth registration flow endpoints")
public class FormController {

    private final MagicLinkService magicLinkService;

    @Value("${app.magic-link.frontend-redirect-url:http://localhost:3000/form}")
    private String frontendRedirectUrl;

    /**
     * API 1: Generate magic link with OAuth token (no auth, no params)
     * Returns JSON response with the magic link
     */
    @Operation(
        summary = "Generate magic link for form registration",
        description = "Generates a magic link with OAuth token for form-based registration flow"
    )
    @GetMapping("/generate-link")
    @ResponseBody
    public ResponseEntity<Map<String, String>> generateMagicLink() {
        log.info("Generating magic link for form registration");

        // Generate magic link with OAuth token
        MagicLinkService.MagicLinkResponse response = magicLinkService.generateMagicLink("/form/verify");

        Map<String, String> result = new HashMap<>();
        result.put("magicLink", response.getMagicLink());
        result.put("token", response.getToken());

        log.info("Generated magic link: {}", response.getMagicLink());

        return ResponseEntity.ok(result);
    }

    /**
     * Handle magic link click - redirects to frontend with OAuth token
     */
    @Operation(
        summary = "Verify magic link token",
        description = "Validates the magic link token and redirects to OAuth2 authorization"
    )
    @GetMapping("/verify")
    public String verifyMagicLink(@RequestParam String token) {
        log.info("Form magic link clicked with token: {}", token);

        // Validate the OAuth token
        if (!magicLinkService.validateToken(token)) {
            log.warn("Invalid or expired token: {}", token);
            return "redirect:" + frontendRedirectUrl + "?error=invalid_token";
        }

        // Redirect to frontend with the OAuth token
        String redirectUrl = frontendRedirectUrl + "?token=" + token;
        log.info("Redirecting to frontend: {}", redirectUrl);

        return "redirect:" + redirectUrl;
    }

    /**
     * API to validate token (called from frontend)
     * Returns token validity status
     */
    @GetMapping("/validate-token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        log.info("Validating token from frontend: {}", token);

        Map<String, Object> response = new HashMap<>();
        boolean isValid = magicLinkService.validateToken(token);

        response.put("valid", isValid);
        response.put("token", token);

        if (isValid) {
            log.info("Token is valid: {}", token);
        } else {
            log.warn("Token is invalid or expired: {}", token);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Page 2: Registration form page (after OAuth)
     */
    @Operation(
        summary = "Show registration form",
        description = "Displays the registration form after successful OAuth authentication"
    )
    @GetMapping("/register")
    public String showRegistrationForm(HttpSession session, Model model) {
        String oauthEmail = (String) session.getAttribute("form_oauth_email");
        String formToken = (String) session.getAttribute("form_token");

        if (oauthEmail == null || formToken == null) {
            log.warn("Registration form accessed without proper OAuth flow");
            return "redirect:/form/generate-link";
        }

        // Validate token is still valid
        if (!magicLinkService.validateToken(formToken)) {
            log.warn("Token expired during registration flow");
            return "redirect:/form/generate-link?error=token_expired";
        }

        model.addAttribute("email", oauthEmail);
        model.addAttribute("token", formToken);
        log.info("Showing form registration page for email: {}", oauthEmail);

        return "form-register";
    }

    /**
     * API 2: Submit registration data - shows loading page
     */
    @Operation(
        summary = "Submit registration form",
        description = "Processes user registration data and displays a loading page"
    )
    @PostMapping("/submit")
    public String submitForm(
            @Parameter(description = "User's full name") @RequestParam String name,
            @Parameter(description = "User's email address") @RequestParam String email,
            @Parameter(description = "User's password") @RequestParam String password,
            HttpSession session,
            Model model) {

        log.info("Form submission received - name: {}, email: {}", name, email);

        String oauthEmail = (String) session.getAttribute("form_oauth_email");
        String userId = (String) session.getAttribute("form_oauth_user_id");
        String formToken = (String) session.getAttribute("form_token");

        log.info("OAuth email: {}, User ID: {}, Token: {}", oauthEmail, userId, formToken);

        // Consume the token (one-time use)
        if (formToken != null) {
            magicLinkService.consumeToken(formToken);
        }

        // Clean up session
        session.removeAttribute("form_token");
        session.removeAttribute("form_oauth_email");
        session.removeAttribute("form_oauth_user_id");

        // Show loading page (as requested - keep loading)
        model.addAttribute("name", name);
        model.addAttribute("email", email);

        return "form-loading";
    }

}
