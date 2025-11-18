# Magic Link Implementation Guide

This guide explains how to implement the magic link feature with OAuth authentication in any Spring Boot application.

## Overview

The magic link feature allows users to:
1. Generate a secure link containing an OAuth token
2. Click the link to access a registration form
3. Complete OAuth authentication automatically
4. Fill out a form with their details
5. Submit and see a loading page

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  Magic Link Generation Flow                         │
├─────────────────────────────────────────────────────┤
│  1. User hits: GET /form/generate-link              │
│  2. OAuthTokenService generates token                │
│  3. MagicLinkService creates link with token         │
│  4. User receives link: /form/verify?token=xxx       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  Magic Link Usage Flow                               │
├─────────────────────────────────────────────────────┤
│  1. User clicks link: /form/verify?token=xxx         │
│  2. Token is validated                               │
│  3. Redirects to OAuth2 provider                     │
│  4. After OAuth: Shows form with email pre-filled    │
│  5. User submits form → Loading page                 │
│  6. Token is consumed (one-time use)                 │
└─────────────────────────────────────────────────────┘
```

## Required Files

### 1. Core Services (MUST COPY)

#### OAuthTokenService.java
**Location:** `src/main/java/org/example/magiclink/service/OAuthTokenService.java`

**Purpose:** Generates, validates, and manages OAuth tokens

**Key Features:**
- Generates unique OAuth tokens
- Stores token metadata (creation time, expiry)
- Validates tokens (checks existence and expiry)
- Consumes tokens (one-time use)
- Configurable expiry (default: 1 hour)

**Configuration Required:**
```yaml
# In application.yml
GOOGLE_CLIENT_ID: your-client-id
GOOGLE_CLIENT_SECRET: your-client-secret
```

---

#### MagicLinkService.java
**Location:** `src/main/java/org/example/magiclink/service/MagicLinkService.java`

**Purpose:** Handles magic link generation logic

**Key Features:**
- Generates complete magic link URLs
- Uses OAuthTokenService for token generation
- Returns both link and token
- Provides validation and consumption methods

**Configuration Required:**
```yaml
# In application.yml
app:
  magic-link:
    base-url: https://your-domain.com
```

---

### 2. Controller (ADAPT TO YOUR NEEDS)

#### FormController.java
**Location:** `src/main/java/org/example/magiclink/controller/FormController.java`

**Purpose:** Handles HTTP endpoints for magic link flow

**Endpoints:**
- `GET /form/generate-link` - Generates the magic link
- `GET /form/verify?token=xxx` - Validates token and redirects to OAuth
- `GET /form/register` - Shows registration form (after OAuth)
- `POST /form/submit` - Handles form submission

**Customization Points:**
- Change `/form` prefix to match your app structure
- Modify form fields in registration page
- Add your own business logic in form submission
- Customize redirect URLs

---

### 3. OAuth Configuration (MUST CONFIGURE)

#### OAuth2SuccessHandler.java
**Location:** `src/main/java/org/example/magiclink/config/OAuth2SuccessHandler.java`

**Purpose:** Handles successful OAuth authentication

**Key Addition:**
Add this code block to detect the form flow:

```java
// Check for form flow
String formToken = (String) session.getAttribute("form_token");
if (formToken != null) {
    log.info("Form flow detected, redirecting to form registration page");
    // Store OAuth user info in session for form page
    session.setAttribute("form_oauth_email", email);
    session.setAttribute("form_oauth_google_id", userId);
    response.sendRedirect("/form/register");
    return;
}
```

**Where to add:** Inside the `onAuthenticationSuccess` method, after extracting email and userId

---

#### SecurityConfig.java
**Location:** `src/main/java/org/example/magiclink/config/SecurityConfig.java`

**Purpose:** Configure Spring Security to allow public access to form endpoints

**Required Change:**
Add `/form/**` to the public access list:

```java
.requestMatchers(
    "/",
    "/login/**",
    "/oauth2/**",
    "/login/oauth2/**",
    "/form/**"  // Add this line
).permitAll()
```

---

### 4. Templates (CUSTOMIZE UI)

#### form-link-generated.html
**Location:** `src/main/resources/templates/form-link-generated.html`

**Purpose:** Displays the generated magic link

**Customization:**
- Change styling to match your brand
- Add additional instructions
- Modify layout structure

**Keep:**
- `th:value="${magicLink}"` attribute
- Copy to clipboard functionality
- Link to click for users

---

#### form-register.html
**Location:** `src/main/resources/templates/form-register.html`

**Purpose:** Registration form shown after OAuth

**Customization:**
- Add/remove form fields as needed
- Change field names
- Modify validation rules
- Update styling

**Keep:**
- Email field with `th:value="${email}"`
- Form action: `/form/submit`
- Form method: `POST`

**Important:** Email should be readonly since it comes from OAuth

---

#### form-loading.html
**Location:** `src/main/resources/templates/form-loading.html`

**Purpose:** Loading page after form submission

**Customization:**
- Change loading animation
- Modify messages
- Add your branding

**Keep:**
- Display submitted name: `th:text="${name}"`
- Display submitted email: `th:text="${email}"`

---

## Configuration

### application.yml
```yaml
# OAuth Configuration
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - openid
              - email
              - profile
            redirect-uri: "https://your-domain.com/login/oauth2/code/google"
        provider:
          google:
            issuer-uri: https://accounts.google.com

# Magic Link Configuration
app:
  magic-link:
    base-url: https://your-domain.com  # Your app's public URL
    token-expiry-minutes: 15            # Optional: customize token expiry
```

### Environment Variables
```bash
# Required
export GOOGLE_CLIENT_ID="your-google-client-id"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"

# Optional
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="your-email@gmail.com"
export MAIL_PASSWORD="your-app-password"
```

---

## Implementation Steps

### Step 1: Copy Core Services
1. Copy `OAuthTokenService.java` to your project
2. Copy `MagicLinkService.java` to your project
3. Update package names to match your project structure

### Step 2: Create Controller
1. Copy `FormController.java` or create your own
2. Update the `@RequestMapping` path if needed
3. Customize form fields and validation

### Step 3: Update OAuth Configuration
1. Add form flow detection to `OAuth2SuccessHandler`
2. Update `SecurityConfig` to allow `/form/**` endpoints
3. Configure redirect URLs

### Step 4: Create Templates
1. Create `form-link-generated.html`
2. Create `form-register.html` with your desired fields
3. Create `form-loading.html` for post-submission

### Step 5: Configure Application
1. Update `application.yml` with OAuth credentials
2. Set `app.magic-link.base-url` to your domain
3. Configure environment variables

### Step 6: Test the Flow
1. Start your application
2. Visit `/form/generate-link`
3. Click the generated link
4. Complete OAuth authentication
5. Fill out and submit the form
6. Verify loading page appears

---

## Customization Options

### Change OAuth Provider
To use a different OAuth provider (not Google):

1. Update `application.yml`:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:  # or facebook, okta, etc.
            client-id: ${OAUTH_CLIENT_ID}
            client-secret: ${OAUTH_CLIENT_SECRET}
```

2. Update redirect in `FormController`:
```java
return "redirect:/oauth2/authorization/github";
```

3. Update `OAuth2SuccessHandler` to extract correct user attributes

---

### Add Email Sending
To send the magic link via email instead of displaying it:

1. Add email service dependency
2. Create email template
3. Update `FormController.generateMagicLink()`:
```java
@PostMapping("/generate-link")
public String generateAndSendMagicLink(@RequestParam String email, Model model) {
    MagicLinkService.MagicLinkResponse response =
        magicLinkService.generateMagicLink("/form/verify");

    // Send email
    emailService.sendMagicLink(email, response.getMagicLink());

    model.addAttribute("email", email);
    return "link-sent-confirmation";
}
```

---

### Add Additional Form Fields
In `form-register.html`, add new fields:
```html
<div class="form-group">
    <label for="phone">Phone Number</label>
    <input type="tel" id="phone" name="phone" placeholder="Enter phone">
</div>
```

Update `FormController.submitForm()` to accept new parameters:
```java
@PostMapping("/submit")
public String submitForm(
        @RequestParam String name,
        @RequestParam String email,
        @RequestParam String password,
        @RequestParam String phone,  // Add new field
        HttpSession session,
        Model model) {
    // Process form...
}
```

---

### Store User Data
Add database persistence in `FormController.submitForm()`:

```java
@PostMapping("/submit")
public String submitForm(...) {
    // Create user entity
    User user = new User();
    user.setName(name);
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(password));
    user.setGoogleId(googleId);

    // Save to database
    userRepository.save(user);

    // Show loading page
    return "form-loading";
}
```

---

### Change Token Expiry
Modify `OAuthTokenService.java`:
```java
tokenInfo.setExpiresIn(3600000); // 1 hour in milliseconds
// Change to:
tokenInfo.setExpiresIn(7200000); // 2 hours
```

Or make it configurable:
```java
@Value("${app.magic-link.token-expiry-minutes:60}")
private long tokenExpiryMinutes;

tokenInfo.setExpiresIn(tokenExpiryMinutes * 60 * 1000);
```

---

## Security Considerations

### 1. Token Security
- Tokens are stored in-memory (not persistent)
- Tokens expire after 1 hour by default
- Tokens are one-time use (consumed after form submission)
- Use HTTPS in production

### 2. Session Security
- OAuth data stored in HTTP session
- Session cleaned after form submission
- Use secure session cookies in production

### 3. CSRF Protection
- CSRF is disabled in this example
- Enable CSRF for production:
```java
.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
```

### 4. Input Validation
Add validation annotations:
```java
@PostMapping("/submit")
public String submitForm(
        @NotBlank @Size(min=2, max=100) String name,
        @Email @NotBlank String email,
        @NotBlank @Size(min=6, max=100) String password,
        ...
) {
    // Process form
}
```

---

## Troubleshooting

### "Invalid or expired token" error
- Check token expiry time in `OAuthTokenService`
- Ensure token is being stored correctly
- Verify token parameter in URL matches stored token

### OAuth redirect doesn't work
- Verify `redirect-uri` in `application.yml` matches Google Console
- Check `/form/**` is in `permitAll()` list in `SecurityConfig`
- Ensure `OAuth2SuccessHandler` has form flow detection code

### Form doesn't show email
- Check OAuth provider returns email in response
- Verify email is stored in session as `form_oauth_email`
- Check template uses `th:value="${email}"`

### Loading page doesn't show user data
- Verify model attributes in `FormController.submitForm()`
- Check template uses `th:text="${name}"` and `th:text="${email}"`

---

## Dependencies Required

### build.gradle
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### pom.xml
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## Complete Flow Diagram

```
User                  App Server              OAuth Provider         Database
  |                       |                         |                    |
  |-- GET /generate-link→|                         |                    |
  |                       |-- Generate Token        |                    |
  |                       |-- Create Magic Link     |                    |
  |←─ Show Link ─────────|                         |                    |
  |                       |                         |                    |
  |-- Click Link ────────→|                         |                    |
  |                       |-- Validate Token        |                    |
  |                       |-- Store in Session      |                    |
  |←─ Redirect to OAuth ─|                         |                    |
  |                       |                         |                    |
  |────── OAuth Flow ─────────────────────────────→|                    |
  |←───── User Info ──────────────────────────────|                    |
  |                       |                         |                    |
  |                       |←─ OAuth Success         |                    |
  |                       |-- Store email in session                     |
  |←─ Show Form ─────────|                         |                    |
  |                       |                         |                    |
  |-- Submit Form ───────→|                         |                    |
  |                       |-- Validate Data         |                    |
  |                       |-- Consume Token         |                    |
  |                       |───────── Save User ─────────────────────────→|
  |                       |←─────── Confirm ─────────────────────────────|
  |←─ Loading Page ──────|                         |                    |
```

---

## FAQ

**Q: Can I use this without OAuth?**
A: Yes, but you'll need to modify the flow to skip OAuth and directly show the form.

**Q: How do I change the token expiry time?**
A: Modify `setExpiresIn()` in `OAuthTokenService.java` or make it configurable via `application.yml`.

**Q: Can I send the link via email?**
A: Yes, integrate an email service and send the link instead of displaying it.

**Q: Is the token reusable?**
A: No, tokens are consumed after form submission (one-time use).

**Q: How do I add more OAuth providers?**
A: Add provider configuration to `application.yml` and update redirect URLs in the controller.

**Q: Can I customize the form fields?**
A: Yes, modify `form-register.html` and update the controller to handle new fields.

**Q: Where is token data stored?**
A: In-memory using `ConcurrentHashMap`. For production, consider using Redis or database.

**Q: How do I enable HTTPS?**
A: Configure SSL certificate in `application.yml` or use a reverse proxy like nginx.

---

## Production Checklist

- [ ] Configure production OAuth client ID and secret
- [ ] Set correct base URL in `application.yml`
- [ ] Enable HTTPS
- [ ] Add proper error handling
- [ ] Implement rate limiting for magic link generation
- [ ] Add logging and monitoring
- [ ] Implement token cleanup for expired tokens
- [ ] Add input validation and sanitization
- [ ] Enable CSRF protection
- [ ] Use secure session cookies
- [ ] Store tokens in Redis or database (not in-memory)
- [ ] Add email sending for magic links
- [ ] Implement user data persistence
- [ ] Add proper error pages
- [ ] Test all OAuth flows
- [ ] Set up proper environment variables
- [ ] Configure session timeout
- [ ] Add audit logging for security events

---

## License

This implementation guide is provided as-is for educational and development purposes.

