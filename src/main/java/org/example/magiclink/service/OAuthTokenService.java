package org.example.magiclink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    @Value("${REVO_CLIENT_ID:revo-playwright-service-account-client}")
    private String clientId;

    @Value("${REVO_CLIENT_SECRET:pVFISliG6vHD6B8S2wSiMAZ6IAbClTRr}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.revo.token-uri:http://localhost:8081/realms/revo/protocol/openid-connect/token}")
    private String tokenUri;

    private final RestTemplate restTemplate = new RestTemplate();

    // Store tokens with their metadata
    private final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();

    /**
     * Generate OAuth token using client credentials flow
     */
    public String generateOAuthToken() {
        try {
            log.info("Generating OAuth token using client credentials flow");

            // Prepare request headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Prepare request body with client credentials
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", "client_credentials");
            requestBody.add("client_id", clientId);
            requestBody.add("client_secret", clientSecret);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Make POST request to token endpoint
            log.info("Requesting OAuth token from: {}", tokenUri);
            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUri,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );

            // Extract access token from response
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("access_token")) {
                log.error("No access_token in OAuth response");
                throw new RuntimeException("Failed to obtain access token from OAuth provider");
            }

            String accessToken = (String) responseBody.get("access_token");
            Integer expiresIn = (Integer) responseBody.get("expires_in");

            // Store token info
            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.setToken(accessToken);
            tokenInfo.setClientId(clientId);
            tokenInfo.setCreatedAt(System.currentTimeMillis());
            tokenInfo.setExpiresIn(expiresIn != null ? expiresIn * 1000L : 3600000L); // Convert to milliseconds

            tokenStore.put(accessToken, tokenInfo);

            log.info("Successfully generated OAuth token with expiry: {} seconds", expiresIn);
            return accessToken;

        } catch (Exception e) {
            log.error("Error generating OAuth token", e);
            throw new RuntimeException("Failed to generate OAuth token", e);
        }
    }

    /**
     * Validate if token exists and is not expired
     */
    public boolean validateToken(String token) {
        TokenInfo tokenInfo = tokenStore.get(token);

        if (tokenInfo == null) {
            log.warn("Token not found: {}", token);
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long expiresAt = tokenInfo.getCreatedAt() + tokenInfo.getExpiresIn();

        if (currentTime > expiresAt) {
            log.warn("Token expired: {}", token);
            tokenStore.remove(token);
            return false;
        }

        log.info("Token is valid: {}", token);
        return true;
    }

    /**
     * Consume token (mark as used)
     */
    public void consumeToken(String token) {
        tokenStore.remove(token);
        log.info("Token consumed: {}", token);
    }

    /**
     * Get token info
     */
    public TokenInfo getTokenInfo(String token) {
        return tokenStore.get(token);
    }

    // Inner class to store token metadata
    public static class TokenInfo {
        private String token;
        private String clientId;
        private long createdAt;
        private long expiresIn;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }
    }
}
