package org.example.magiclink.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.magiclink.entity.User;
import org.example.magiclink.service.EmailService;
import org.example.magiclink.service.TokenService;
import org.example.magiclink.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;
    private final EmailService emailService;
    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login/ott/generate")
    public String generateToken(HttpServletRequest request, @RequestParam("username") String username) {
        String email = username == null ? null : username.trim().toLowerCase();
        if (email == null || email.isEmpty()) {
            return "redirect:/login?error";
        }

        // Check if user has a Google account
        if (!userService.hasGoogleAccount(email)) {
            return "redirect:/login?error=no_google_account";
        }

        String token = tokenService.createToken(email, request);

        String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), "") + request.getContextPath();
        String magicLink = baseUrl + "/login/ott?token=" + token;

        emailService.sendMagicLink(email, magicLink);

        return "redirect:/check-email";
    }

    @GetMapping("/check-email")
    public String checkEmail() {
        return "check-email";
    }

    @GetMapping("/login/ott")
    public String consumeToken(
            HttpServletRequest request,
            @RequestParam("token") String token,
            Model model) {

        // Validate token without consuming it yet
        Optional<String> emailOpt = tokenService.validateToken(token);
        if (emailOpt.isEmpty()) {
            return "redirect:/login?error=invalid_token";
        }

        String expectedEmail = emailOpt.get();

        // Store token in session for verification after OAuth
        HttpSession session = request.getSession(true);
        session.setAttribute("pending_magic_token", token);
        session.setAttribute("pending_magic_email", expectedEmail);

        model.addAttribute("email", expectedEmail);

        // Redirect to silent check page
        return "magic-link-verify";
    }

    @GetMapping("/login/ott/verify-manual")
    public String verifyManual(HttpSession session, Model model) {
        String expectedEmail = (String) session.getAttribute("pending_magic_email");
        if (expectedEmail == null) {
            return "redirect:/login?error=session_expired";
        }
        model.addAttribute("email", expectedEmail);
        return "verify-manual";
    }

    private void authenticateUser(HttpServletRequest request, String email) {
        UserDetails userDetails = (UserDetails) userService.loadUserByUsername(email);
        Authentication auth = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.getSession(true);
        userService.updateLastLogin(email);
    }
}