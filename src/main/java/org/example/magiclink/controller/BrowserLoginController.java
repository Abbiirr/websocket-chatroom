package org.example.magiclink.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.magiclink.entity.QRTokenEntity;
import org.example.magiclink.service.QRService;
import org.example.magiclink.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/qr/login")
public class BrowserLoginController {

    private final QRService qrService;
    private final UserService userService;

    @GetMapping("/generate")
    public String generateLoginQR(HttpServletRequest request, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "redirect:/";
        }

        try {
            String deviceInfo = request.getHeader("User-Agent");
            String ip = request.getRemoteAddr();
            HttpSession session = request.getSession(true);
            String sessionId = session.getId();

            QRTokenEntity token = qrService.generateBrowserLoginToken(deviceInfo, ip, sessionId);
            String qrImage = qrService.generateQRCodeImage(token.getToken());

            session.setAttribute("browser_login_token", token.getToken());

            model.addAttribute("qrImage", qrImage);
            model.addAttribute("token", token.getToken());
            model.addAttribute("expiresAt", token.getExpiresAt());

            return "qr-login-generate";
        } catch (Exception e) {
            log.error("Error generating login QR code", e);
            return "redirect:/login?error=qr_generation_failed";
        }
    }

    @GetMapping("/scan")
    public String scanLoginQR(@RequestParam("token") String token, Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login?returnUrl=/qr/login/scan?token=" + token;
        }

        Optional<QRTokenEntity> opt = qrService.validateToken(token);
        if (opt.isEmpty()) {
            return "redirect:/login?error=invalid_qr";
        }

        QRTokenEntity qrToken = opt.get();
        if (qrToken.getLoginType() != QRTokenEntity.QRLoginType.BROWSER_TO_BROWSER) {
            return "redirect:/login?error=invalid_qr_type";
        }

        String email = extractEmail(principal);

        model.addAttribute("token", token);
        model.addAttribute("deviceInfo", qrToken.getDeviceInfo());
        model.addAttribute("ip", qrToken.getIp());
        model.addAttribute("userEmail", email);

        return "qr-login-scan";
    }

    @PostMapping("/approve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> approveLogin(
            @RequestParam("token") String token,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();

        if (principal == null) {
            response.put("success", false);
            response.put("error", "Not authenticated");
            return ResponseEntity.status(401).body(response);
        }

        Optional<QRTokenEntity> opt = qrService.validateToken(token);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Invalid token");
            return ResponseEntity.badRequest().body(response);
        }

        QRTokenEntity qrToken = opt.get();
        if (qrToken.getLoginType() != QRTokenEntity.QRLoginType.BROWSER_TO_BROWSER) {
            response.put("success", false);
            response.put("error", "Invalid QR type");
            return ResponseEntity.badRequest().body(response);
        }

        String email = extractEmail(principal);

        qrToken.setUserEmail(email);
        qrToken.setStatus(QRTokenEntity.QRTokenStatus.APPROVED);
        qrService.saveToken(qrToken);

        response.put("success", true);
        log.info("Browser login approved by: {}", email);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/deny")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> denyLogin(
            @RequestParam("token") String token,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();

        if (principal == null) {
            response.put("success", false);
            return ResponseEntity.status(401).body(response);
        }

        Optional<QRTokenEntity> opt = qrService.validateToken(token);
        if (opt.isEmpty()) {
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }

        qrService.denyToken(token);
        response.put("success", true);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkLoginStatus(
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {

        Map<String, Object> responseMap = new HashMap<>();
        HttpSession session = request.getSession(false);

        if (session == null || !token.equals(session.getAttribute("browser_login_token"))) {
            responseMap.put("error", "Invalid session");
            return ResponseEntity.status(403).body(responseMap);
        }

        Optional<QRTokenEntity> opt = qrService.findToken(token);
        if (opt.isEmpty()) {
            responseMap.put("status", QRTokenEntity.QRTokenStatus.EXPIRED.toString());
            session.removeAttribute("browser_login_token");
            return ResponseEntity.ok(responseMap);
        }

        QRTokenEntity qrToken = opt.get();
        if (!session.getId().equals(qrToken.getScanningSessionId())) {
            responseMap.put("error", "Session mismatch");
            return ResponseEntity.status(403).body(responseMap);
        }

        if (qrService.hasExpired(qrToken)) {
            qrService.markExpired(qrToken);
            responseMap.put("status", QRTokenEntity.QRTokenStatus.EXPIRED.toString());
            session.removeAttribute("browser_login_token");
            return ResponseEntity.ok(responseMap);
        }

        responseMap.put("status", qrToken.getStatus().toString());

        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        boolean alreadyAuth = currentAuth != null &&
                currentAuth.isAuthenticated() &&
                !"anonymousUser".equals(currentAuth.getName());

        if (alreadyAuth) {
            responseMap.put("authenticated", true);
            responseMap.put("redirectUrl", "/");
            session.removeAttribute("browser_login_token");
            return ResponseEntity.ok(responseMap);
        }

        if (qrToken.getStatus() == QRTokenEntity.QRTokenStatus.APPROVED) {
            try {
                Optional<String> emailOpt = qrService.consumeToken(token, session.getId());

                if (emailOpt.isPresent()) {
                    String email = emailOpt.get();
                    UserDetails userDetails = (UserDetails) userService.loadUserByUsername(email);

                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );

                    SecurityContextHolder.getContext().setAuthentication(auth);

                    SecurityContextRepository securityContextRepository =
                            new HttpSessionSecurityContextRepository();
                    securityContextRepository.saveContext(
                            SecurityContextHolder.getContext(), request, response
                    );

                    userService.updateLastLogin(email);

                    responseMap.put("authenticated", true);
                    responseMap.put("redirectUrl", "/");
                    session.removeAttribute("browser_login_token");

                    log.info("Browser login success for: {}", email);
                }
            } catch (Exception e) {
                log.error("Browser login auth failed", e);
                qrService.denyToken(token);
                responseMap.put("status", QRTokenEntity.QRTokenStatus.DENIED.toString());
                responseMap.put("error", "Authentication failed");
                session.removeAttribute("browser_login_token");
            }
        }

        return ResponseEntity.ok(responseMap);
    }

    private String extractEmail(Principal principal) {
        if (principal instanceof OAuth2AuthenticationToken oauth) {
            OAuth2User oauth2User = oauth.getPrincipal();
            Object emailAttr = oauth2User.getAttributes().get("email");
            if (emailAttr instanceof String email && !email.isBlank()) {
                return email;
            }
            Object preferredAttr = oauth2User.getAttributes().get("preferred_username");
            if (preferredAttr instanceof String preferred && !preferred.isBlank()) {
                return preferred;
            }
            Object loginAttr = oauth2User.getAttributes().get("login");
            if (loginAttr instanceof String login && !login.isBlank()) {
                return login;
            }
            Object nameAttr = oauth2User.getAttributes().get("name");
            if (nameAttr instanceof String name && !name.isBlank()) {
                return name;
            }
        }
        return principal.getName();
    }
}

