package org.example.magiclink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service to handle magic link generation with OAuth tokens
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MagicLinkService {

    private final OAuthTokenService oAuthTokenService;

    @Value("${app.magic-link.base-url}")
    private String baseUrl;

    /**
     * Generate a magic link with OAuth token
     * @param endpoint The endpoint to redirect to (e.g., "/form/verify")
     * @return The complete magic link URL with OAuth token
     */
    public MagicLinkResponse generateMagicLink(String endpoint) {
        log.info("Generating magic link for endpoint: {}", endpoint);

        // Generate OAuth token
        String oauthToken = oAuthTokenService.generateOAuthToken();

        // Create the magic link URL
        String magicLink = baseUrl + endpoint + "?token=" + oauthToken;

        log.info("Generated magic link: {}", magicLink);

        return new MagicLinkResponse(magicLink, oauthToken);
    }

    /**
     * Validate if a token is valid
     */
    public boolean validateToken(String token) {
        return oAuthTokenService.validateToken(token);
    }

    /**
     * Consume a token (mark as used)
     */
    public void consumeToken(String token) {
        oAuthTokenService.consumeToken(token);
    }

    /**
     * Response object containing magic link and token
     */
    public static class MagicLinkResponse {
        private final String magicLink;
        private final String token;

        public MagicLinkResponse(String magicLink, String token) {
            this.magicLink = magicLink;
            this.token = token;
        }

        public String getMagicLink() {
            return magicLink;
        }

        public String getToken() {
            return token;
        }
    }
}
