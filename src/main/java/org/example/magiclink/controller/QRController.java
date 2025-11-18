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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/qr")
public class QRController {

    private final QRService qrService;
    private final UserService userService;

    @GetMapping("/generate")
    public String generateQR(Principal principal, HttpServletRequest request, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        try {
            String email = extractEmail(principal);
            String sessionId = request.getSession().getId();

            QRTokenEntity token = qrService.generateQRToken(email, sessionId);
            String qrImage = qrService.generateQRCodeImage(token.getToken());

            model.addAttribute("qrImage", qrImage);
            model.addAttribute("token", token.getToken());
            model.addAttribute("expiresAt", token.getExpiresAt());

            return "qr-generate";
        } catch (Exception e) {
            log.error("Error generating QR code", e);
            return "redirect:/?error=qr_generation_failed";
        }
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

    @GetMapping("/scan")
    public String scanQR(@RequestParam("token") String token, Model model, HttpServletRequest request) {
        Optional<QRTokenEntity> opt = qrService.validateToken(token);
        if (opt.isEmpty()) {
            return "redirect:/login?error=invalid_qr";
        }

        QRTokenEntity qrToken = opt.get();
        if (qrToken.getLoginType() != QRTokenEntity.QRLoginType.PHONE_TO_BROWSER) {
            return "redirect:/login?error=invalid_qr_type";
        }

        HttpSession session = request.getSession(true);
        String currentSessionId = session.getId();

        if (Objects.equals(qrToken.getGeneratingSessionId(), currentSessionId)) {
            return "redirect:/login?error=cannot_scan_own_qr";
        }

        if (qrToken.getScanningSessionId() != null && !Objects.equals(qrToken.getScanningSessionId(), currentSessionId)) {
            return "redirect:/login?error=qr_already_scanned";
        }

        if (qrToken.getScanningSessionId() == null) {
            qrToken.setScanningSessionId(currentSessionId);
            qrToken.setDeviceInfo(request.getHeader("User-Agent"));
            qrToken.setIp(request.getRemoteAddr());
            qrToken.setScannedAt(LocalDateTime.now());
            qrService.saveToken(qrToken);
        }

        session.setAttribute("pending_qr_token", token);
        model.addAttribute("token", token);
        model.addAttribute("userEmail", qrToken.getUserEmail());

        return "qr-scan";
    }
    @GetMapping("/approve")
    public String approveQR(
            @RequestParam("token") String token,
            Principal principal,
            Model model,
            HttpServletRequest request) {
        if (principal == null) {
            return "redirect:/login";
        }

        Optional<QRTokenEntity> opt = qrService.validateToken(token);
        if (opt.isEmpty()) {
            return "redirect:/?error=invalid_qr";
        }

        QRTokenEntity qrToken = opt.get();
        if (qrToken.getLoginType() != QRTokenEntity.QRLoginType.PHONE_TO_BROWSER) {
            return "redirect:/?error=invalid_qr_type";
        }

        String email = extractEmail(principal);
        if (!qrToken.getUserEmail().equalsIgnoreCase(email)) {
            return "redirect:/?error=unauthorized";
        }

        HttpSession session = request.getSession(false);
        if (session == null || !Objects.equals(session.getId(), qrToken.getGeneratingSessionId())) {
            return "redirect:/?error=approval_wrong_device";
        }

        model.addAttribute("token", token);
        model.addAttribute("deviceInfo", qrToken.getDeviceInfo());
        model.addAttribute("ip", qrToken.getIp());
        model.addAttribute("awaitingScan", qrToken.getScanningSessionId() == null);

        return "qr-approve";
    }

    @PostMapping("/approve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> approveQRPost(
            @RequestParam("token") String token,
            Principal principal,
            HttpServletRequest request) {
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
        String email = extractEmail(principal);
        if (!qrToken.getUserEmail().equalsIgnoreCase(email)) {
            response.put("success", false);
            response.put("error", "Unauthorized");
            return ResponseEntity.status(403).body(response);
        }

        HttpSession session = request.getSession(false);
        if (session == null || !Objects.equals(session.getId(), qrToken.getGeneratingSessionId())) {
            response.put("success", false);
            response.put("error", "Approval must be completed from the device that generated the QR code");
            return ResponseEntity.status(403).body(response);
        }

        if (qrToken.getScanningSessionId() == null) {
            response.put("success", false);
            response.put("error", "No device has scanned this QR code yet");
            return ResponseEntity.status(409).body(response);
        }

        boolean approved = qrService.approveToken(token);
        response.put("success", approved);
        if (!approved) {
            response.put("error", "Unable to approve token");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/deny")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> denyQR(
            @RequestParam("token") String token,
            Principal principal,
            HttpServletRequest request) {
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

        QRTokenEntity qrToken = opt.get();
        if (qrToken.getLoginType() != QRTokenEntity.QRLoginType.PHONE_TO_BROWSER) {
            response.put("success", false);
            response.put("error", "Invalid QR type");
            return ResponseEntity.badRequest().body(response);
        }

        String email = extractEmail(principal);
        if (!qrToken.getUserEmail().equalsIgnoreCase(email)) {
            response.put("success", false);
            return ResponseEntity.status(403).body(response);
        }

        HttpSession session = request.getSession(false);
        if (session == null || !Objects.equals(session.getId(), qrToken.getGeneratingSessionId())) {
            response.put("success", false);
            response.put("error", "Denial must be completed from the device that generated the QR code");
            return ResponseEntity.status(403).body(response);
        }

        boolean denied = qrService.denyToken(token);
        response.put("success", denied);
        if (!denied) {
            response.put("error", "Unable to deny token");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkStatus(
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        Map<String, Object> responseMap = new HashMap<>();

        Optional<QRTokenEntity> optToken = qrService.findToken(token);
        if (optToken.isEmpty()) {
            responseMap.put("status", QRTokenEntity.QRTokenStatus.EXPIRED.toString());
            responseMap.put("scanned", false);
            return ResponseEntity.ok(responseMap);
        }

        QRTokenEntity qrToken = optToken.get();
        responseMap.put("deviceInfo", qrToken.getDeviceInfo());
        responseMap.put("ip", qrToken.getIp());

        if (qrService.hasExpired(qrToken)) {
            qrService.markExpired(qrToken);
            responseMap.put("status", QRTokenEntity.QRTokenStatus.EXPIRED.toString());
            responseMap.put("scanned", qrToken.getScannedAt() != null);
            clearPendingToken(request.getSession(false), token);
            return ResponseEntity.ok(responseMap);
        }

        QRTokenEntity.QRTokenStatus status = qrToken.getStatus();
        responseMap.put("status", status.toString());
        responseMap.put("scanned", qrToken.getScannedAt() != null);

        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.ok(responseMap);
        }

        String pendingToken = (String) session.getAttribute("pending_qr_token");
        String sessionId = session.getId();
        boolean isScanningSession = Objects.equals(sessionId, qrToken.getScanningSessionId());
        boolean isGeneratingSession = Objects.equals(sessionId, qrToken.getGeneratingSessionId());
        boolean isAuthorizedScanningSession = isScanningSession && token.equals(pendingToken);

        if (!isAuthorizedScanningSession) {
            if (isGeneratingSession) {
                responseMap.put("awaitingApproval", status == QRTokenEntity.QRTokenStatus.PENDING);
            }
            return ResponseEntity.ok(responseMap);
        }

        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth != null && currentAuth.isAuthenticated() && !"anonymousUser".equals(currentAuth.getName())) {
            responseMap.put("authenticated", true);
            responseMap.put("redirectUrl", "/");
            clearPendingToken(session, token);
            return ResponseEntity.ok(responseMap);
        }

        if (status == QRTokenEntity.QRTokenStatus.APPROVED) {
            try {
                Optional<String> emailOpt = qrService.consumeToken(token, sessionId);
                if (emailOpt.isPresent()) {
                    String email = emailOpt.get();
                    UserDetails userDetails = (UserDetails) userService.loadUserByUsername(email);

                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
                    securityContextRepository.saveContext(
                            SecurityContextHolder.getContext(), request, response
                    );

                    userService.updateLastLogin(email);

                    responseMap.put("authenticated", true);
                    responseMap.put("redirectUrl", "/");
                    clearPendingToken(session, token);

                    log.info("QR auth success for: {}", email);
                } else {
                    responseMap.put("error", "Token already consumed or session not authorized");
                }
            } catch (Exception e) {
                log.error("Auth failed during QR login", e);
                qrService.denyToken(token);
                responseMap.put("status", QRTokenEntity.QRTokenStatus.DENIED.toString());
                responseMap.put("error", "Authentication failed");
                clearPendingToken(session, token);
            }
        } else if (status == QRTokenEntity.QRTokenStatus.DENIED) {
            clearPendingToken(session, token);
        }

        return ResponseEntity.ok(responseMap);
    }

    private void clearPendingToken(HttpSession session, String token) {
        if (session == null) {
            return;
        }
        Object pending = session.getAttribute("pending_qr_token");
        if (pending instanceof String pendingToken && token.equals(pendingToken)) {
            session.removeAttribute("pending_qr_token");
        }
    }
}
