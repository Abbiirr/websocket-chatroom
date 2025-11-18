package org.example.magiclink.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.magiclink.entity.User;
import org.example.magiclink.service.TokenService;
import org.example.magiclink.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) throws ServletException, IOException {

        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauth2User = oauth2Token.getPrincipal();
            String registrationId = oauth2Token.getAuthorizedClientRegistrationId();

            // Extract email - works for both Revo and Google
            String email = oauth2User.getAttribute("email");
            if (email == null) {
                email = oauth2User.getAttribute("preferred_username");
            }

            // Extract user ID - works for both Revo and Google
            String userId = oauth2User.getAttribute("sub");
            if (userId == null) {
                userId = oauth2User.getAttribute("preferred_username");
            }

            if (email == null) {
                log.error("No email in OAuth2 response from provider: {}", registrationId);
                response.sendRedirect("/login?error=no_email");
                return;
            }

            log.info("OAuth2 authentication from provider: {}, email: {}", registrationId, email);

            HttpSession session = request.getSession(false);

            // Check if this is part of registration magic link flow
            if (session != null) {
                // Check for form flow
                String formToken = (String) session.getAttribute("form_token");
                if (formToken != null) {
                    log.info("Form flow detected, redirecting to form registration page");
                    // Store OAuth user info in session for form page
                    session.setAttribute("form_oauth_email", email);
                    session.setAttribute("form_oauth_user_id", userId);
                    response.sendRedirect("/form/register");
                    return;
                }

                String registrationToken = (String) session.getAttribute("registration_token");
                if (registrationToken != null) {
                    log.info("Registration flow detected, redirecting to registration form");
                    // Store OAuth user info in session for registration page
                    session.setAttribute("oauth_email", email);
                    session.setAttribute("oauth_user_id", userId);
                    response.sendRedirect("/register/form");
                    return;
                }

                String pendingToken = (String) session.getAttribute("pending_magic_token");
                String pendingEmail = (String) session.getAttribute("pending_magic_email");

                if (pendingToken != null && pendingEmail != null) {
                    // This is magic link flow - verify email matches
                    if (!email.equalsIgnoreCase(pendingEmail)) {
                        log.warn("Email mismatch in magic link flow: expected={}, got={}", pendingEmail, email);
                        response.sendRedirect("/login?error=email_mismatch");
                        return;
                    }

                    // Consume the token
                    Optional<String> consumedEmail = tokenService.validateAndConsume(pendingToken);
                    if (consumedEmail.isEmpty()) {
                        log.warn("Failed to consume token in magic link flow");
                        response.sendRedirect("/login?error=token_consumed");
                        return;
                    }

                    // Clean up session
                    session.removeAttribute("pending_magic_token");
                    session.removeAttribute("pending_magic_email");

                    // Update last login
                    userService.updateLastLogin(email);

                    log.info("Magic link authentication successful for {}", email);
                    response.sendRedirect("/");
                    return;
                }
            }

            // Regular OAuth signup/signin flow
            User user = userService.findOrCreateFromGoogle(email, userId);
            log.info("OAuth authentication successful for {} from provider {}", email, registrationId);
            response.sendRedirect("/");
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}