package org.example.magiclink.controller;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Principal principal, Model model) {
        if (principal != null) {
            model.addAttribute("username", extractEmail(principal));
        }
        return "home";
    }

    private String extractEmail(Principal principal) {
        if (principal instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) principal;
            OAuth2User oauth2User = oauth.getPrincipal();
            return oauth2User.getAttribute("name");
        }
        return principal.getName();
    }
}

