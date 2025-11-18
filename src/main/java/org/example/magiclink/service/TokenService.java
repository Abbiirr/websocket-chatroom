package org.example.magiclink.service;

import lombok.RequiredArgsConstructor;
import org.example.magiclink.entity.OneTimeTokenEntity;
import org.example.magiclink.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;

    @Value("${app.magic-link.token-expiry-minutes:15}")
    private int tokenExpiryMinutes;

    public String createToken(String username, HttpServletRequest request) {
        String token = UUID.randomUUID().toString();
        OneTimeTokenEntity e = new OneTimeTokenEntity();
        e.setToken(token);
        e.setUsername(username);
        e.setExpiresAt(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));
        e.setUsed(false);
        if (request != null) {
            e.setIp(request.getRemoteAddr());
            String ua = request.getHeader("User-Agent");
            e.setUserAgent(ua);
        }
        tokenRepository.save(e);
        return token;
    }

    public Optional<String> validateToken(String token) {
        Optional<OneTimeTokenEntity> opt = tokenRepository.findByToken(token);
        if (opt.isEmpty()) return Optional.empty();
        OneTimeTokenEntity e = opt.get();
        if (e.isUsed()) return Optional.empty();
        if (e.getExpiresAt() == null || e.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(e.getUsername());
    }

    public Optional<String> validateAndConsume(String token) {
        Optional<OneTimeTokenEntity> opt = tokenRepository.findByToken(token);
        if (opt.isEmpty()) return Optional.empty();
        OneTimeTokenEntity e = opt.get();
        if (e.isUsed()) return Optional.empty();
        if (e.getExpiresAt() == null || e.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        e.setUsed(true);
        tokenRepository.save(e);
        return Optional.of(e.getUsername());
    }
}