package org.example.magiclink.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/register")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController {

    @Value("${app.magic-link.base-url}")
    private String baseUrl;

    /**
     * Simple GET API to generate a magic link for registration
     * No authentication, no parameters required
     */
    @GetMapping("/generate-link")
    public String generateMagicLink(Model model) {
        // Generate a unique token for this registration flow
        String token = UUID.randomUUID().toString();

        // Create the magic link URL
        String magicLink = baseUrl + "/register/verify?token=" + token;

        log.info("Generated registration magic link with token: {}", token);

        model.addAttribute("magicLink", magicLink);
        model.addAttribute("token", token);

        return "register-link-generated";
    }

    /**
     * Handle magic link click - initiates OAuth flow
     */
    @GetMapping("/verify")
    public String verifyMagicLink(@RequestParam String token, HttpSession session) {
        log.info("Magic link clicked with token: {}", token);

        // Store token in session for later use
        session.setAttribute("registration_token", token);

        // Redirect to OAuth2 authorization with Google
        return "redirect:/oauth2/authorization/google";
    }

    /**
     * Show registration form after OAuth success
     */
    @GetMapping("/form")
    public String showRegistrationForm(HttpSession session, Model model) {
        String oauthEmail = (String) session.getAttribute("oauth_email");
        String registrationToken = (String) session.getAttribute("registration_token");

        if (oauthEmail == null || registrationToken == null) {
            log.warn("Registration form accessed without proper OAuth flow");
            return "redirect:/register/generate-link";
        }

        model.addAttribute("email", oauthEmail);
        log.info("Showing registration form for email: {}", oauthEmail);

        return "registration-form";
    }

    /**
     * Handle registration form submission
     */
    @PostMapping("/submit")
    public String submitRegistration(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            Model model) {

        log.info("Registration submitted - name: {}, email: {}", name, email);

        // Here you would typically:
        // 1. Validate the data
        // 2. Create/update user in database
        // 3. Store password securely (hashed)
        // 4. Link with OAuth data from session

        String oauthEmail = (String) session.getAttribute("oauth_email");
        String googleId = (String) session.getAttribute("oauth_google_id");
        String registrationToken = (String) session.getAttribute("registration_token");

        log.info("OAuth email: {}, Google ID: {}, Token: {}", oauthEmail, googleId, registrationToken);

        // Clean up session
        session.removeAttribute("registration_token");
        session.removeAttribute("oauth_email");
        session.removeAttribute("oauth_google_id");

        // Show loading page (as requested - keep loading)
        model.addAttribute("name", name);
        model.addAttribute("email", email);

        return "registration-loading";
    }
}
