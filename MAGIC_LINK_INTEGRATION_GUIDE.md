# Magic Link with OAuth Integration Guide

## Overview

This guide explains how to integrate the magic link generation system with OAuth client credentials flow into any Spring Boot project. The system generates magic links that contain real OAuth tokens obtained from an OAuth2 provider (like Keycloak/Revo).

### Flow Diagram

```
1. GET /form/generate-link
   └─> Calls OAuth server with client credentials
   └─> Returns magic link with real OAuth token

2. User clicks magic link (http://backend:18080/form/verify?token=<oauth-token>)
   └─> Validates token
   └─> Redirects to frontend (http://frontend:3000/form?token=<oauth-token>)

3. Frontend calls GET /form/validate-token?token=<oauth-token>
   └─> Validates token and returns status
```

---

## Prerequisites

- **Spring Boot 3.4.0+**
- **Java 21+**
- **OAuth2 Provider** (Keycloak, Auth0, Okta, or custom OAuth server)
- **Service Account Client** with client credentials grant type enabled
- **Lombok** (for reducing boilerplate)
- **Spring Web** and **Spring Security** dependencies

---

## Project Structure

```
src/main/java/com/yourproject/
├── controller/
│   └── FormController.java
├── service/
│   ├── MagicLinkService.java
│   └── OAuthTokenService.java
└── config/
    └── SecurityConfig.java (modifications)

src/main/resources/
└── application.yml (configuration)
```

---

## Step 1: Add Dependencies

Add these dependencies to your `pom.xml`:

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- Spring OAuth2 Client -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## Step 2: Create OAuthTokenService

This service handles OAuth token generation using client credentials flow.

**File**: `src/main/java/com/yourproject/service/OAuthTokenService.java`

```java
package com.yourproject.service;

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

    @Value("${oauth.client-id}")
    private String clientId;

    @Value("${oauth.client-secret}")
    private String clientSecret;

    @Value("${oauth.token-uri}")
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
```

---

## Step 3: Create MagicLinkService

This service generates magic links with OAuth tokens.

**File**: `src/main/java/com/yourproject/service/MagicLinkService.java`

```java
package com.yourproject.service;

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
```

---

## Step 4: Create FormController

This controller provides the API endpoints.

**File**: `src/main/java/com/yourproject/controller/FormController.java`

```java
package com.yourproject.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.yourproject.service.MagicLinkService;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/form")
@RequiredArgsConstructor
@Slf4j
public class FormController {

    private final MagicLinkService magicLinkService;

    @Value("${app.magic-link.frontend-redirect-url}")
    private String frontendRedirectUrl;

    /**
     * API 1: Generate magic link with OAuth token (no auth, no params)
     * Returns JSON response with the magic link
     */
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
}
```

---

## Step 5: Update SecurityConfig

Add the form endpoints to the permitAll list.

**File**: `src/main/java/com/yourproject/config/SecurityConfig.java`

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/form/**",     // Add this line
                "/login/**",
                "/oauth2/**",
                // ... other public endpoints
            ).permitAll()
            .anyRequest().authenticated()
        )
        // ... rest of your security config
        .csrf(csrf -> csrf.disable());  // Disable CSRF if needed

    return http.build();
}
```

---

## Step 6: Configuration (application.yml)

Add the following configuration to your `application.yml`:

```yaml
# OAuth Configuration
oauth:
  client-id: ${OAUTH_CLIENT_ID:your-service-account-client-id}
  client-secret: ${OAUTH_CLIENT_SECRET:your-client-secret}
  token-uri: ${OAUTH_TOKEN_URI:http://localhost:8081/realms/your-realm/protocol/openid-connect/token}

# Application Configuration
app:
  magic-link:
    base-url: ${BACKEND_URL:http://localhost:8080}
    frontend-redirect-url: ${FRONTEND_URL:http://localhost:3000/form}
```

### Configuration Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `oauth.client-id` | OAuth service account client ID | `my-service-client` |
| `oauth.client-secret` | OAuth service account client secret | `abc123xyz` |
| `oauth.token-uri` | OAuth token endpoint URL | `http://oauth-server/token` |
| `app.magic-link.base-url` | Your backend base URL | `http://localhost:8080` |
| `app.magic-link.frontend-redirect-url` | Frontend URL to redirect after verification | `http://localhost:3000/form` |

---

## Step 7: OAuth Provider Setup

### For Keycloak/Revo:

1. **Create a Service Account Client**:
   - Go to your realm → Clients → Create Client
   - Client Type: `OpenID Connect`
   - Client ID: `your-service-account-client`
   - Enable: `Client authentication`
   - Enable: `Service accounts roles`
   - Authentication flow: Enable only `Service account roles`

2. **Get Client Credentials**:
   - Go to Credentials tab
   - Copy the Client Secret

3. **Note the Token URI**:
   - Format: `http://<keycloak-host>/realms/<realm-name>/protocol/openid-connect/token`
   - Example: `http://localhost:8081/realms/myrealm/protocol/openid-connect/token`

### For Other OAuth Providers:

- **Auth0**: Use Machine-to-Machine application
- **Okta**: Use Client Credentials flow
- **AWS Cognito**: Use App Client with client credentials

---

## API Endpoints

### 1. Generate Magic Link

**Request:**
```http
GET /form/generate-link
```

**Response:**
```json
{
  "magicLink": "http://localhost:8080/form/verify?token=eyJhbGciOiJSUzI1NiIs...",
  "token": "eyJhbGciOiJSUzI1NiIs..."
}
```

### 2. Verify Magic Link

**Request:**
```http
GET /form/verify?token=eyJhbGciOiJSUzI1NiIs...
```

**Response:**
```
HTTP 302 Redirect to: http://localhost:3000/form?token=eyJhbGciOiJSUzI1NiIs...
```

### 3. Validate Token

**Request:**
```http
GET /form/validate-token?token=eyJhbGciOiJSUzI1NiIs...
```

**Response:**
```json
{
  "valid": true,
  "token": "eyJhbGciOiJSUzI1NiIs..."
}
```

---

## Testing

### 1. Test OAuth Token Generation

```bash
# Test the token endpoint directly
curl -X POST http://localhost:8081/realms/your-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=your-client-id" \
  -d "client_secret=your-client-secret"
```

### 2. Test Magic Link Generation

```bash
curl http://localhost:8080/form/generate-link
```

### 3. Test Token Validation

```bash
curl "http://localhost:8080/form/validate-token?token=YOUR_TOKEN"
```

---

## Frontend Integration Example

### React Example:

```javascript
// Generate magic link
const generateLink = async () => {
  const response = await fetch('http://localhost:8080/form/generate-link');
  const data = await response.json();
  console.log('Magic Link:', data.magicLink);
  return data;
};

// Handle redirect from magic link (on your frontend page)
useEffect(() => {
  const urlParams = new URLSearchParams(window.location.search);
  const token = urlParams.get('token');

  if (token) {
    validateToken(token);
  }
}, []);

// Validate token
const validateToken = async (token) => {
  const response = await fetch(
    `http://localhost:8080/form/validate-token?token=${token}`
  );
  const data = await response.json();

  if (data.valid) {
    console.log('Token is valid!');
    // Proceed with your flow
  } else {
    console.log('Token is invalid or expired');
  }
};
```

---

## Security Considerations

1. **Token Storage**:
   - Current implementation uses in-memory storage (ConcurrentHashMap)
   - For production, consider using Redis or a database

2. **Token Expiry**:
   - Tokens expire based on OAuth server's `expires_in` value
   - Expired tokens are automatically cleaned up on validation

3. **HTTPS**:
   - Always use HTTPS in production
   - Update `base-url` and `frontend-redirect-url` to use HTTPS

4. **CORS**:
   - Configure CORS if frontend is on different domain
   - Add `@CrossOrigin` annotation or configure globally

5. **Rate Limiting**:
   - Consider adding rate limiting to `/form/generate-link`
   - Prevent abuse of token generation

---

## Troubleshooting

### Issue: "Failed to obtain access token"

**Solution**:
- Verify client ID and secret are correct
- Check that service account is enabled in OAuth provider
- Verify token URI is accessible from your application

### Issue: "Token not found"

**Solution**:
- Token might have expired
- In-memory storage is lost on application restart
- Consider using persistent storage for production

### Issue: "Redirect not working"

**Solution**:
- Check `frontend-redirect-url` is correct
- Verify frontend is running and accessible
- Check browser console for CORS errors

---

## Production Enhancements

1. **Persistent Token Storage**:
   ```java
   // Use Redis instead of ConcurrentHashMap
   @Autowired
   private RedisTemplate<String, TokenInfo> redisTemplate;
   ```

2. **Token Introspection**:
   - Validate tokens against OAuth server
   - Use introspection endpoint for real-time validation

3. **Monitoring & Logging**:
   - Add metrics for token generation
   - Track token validation success/failure rates
   - Monitor OAuth provider latency

4. **Error Handling**:
   - Implement retry logic for OAuth requests
   - Add circuit breaker pattern
   - Graceful degradation if OAuth provider is down

---

## Summary

You've successfully integrated the magic link system! The flow:

1. **Backend generates** real OAuth token via client credentials
2. **Magic link contains** the actual OAuth access token
3. **User clicks link** → validates token → redirects to frontend
4. **Frontend validates** token and proceeds with authenticated flow

This provides a secure, OAuth-based magic link system that can be integrated into any Spring Boot project.
