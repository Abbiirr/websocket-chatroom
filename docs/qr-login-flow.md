# QR Login Flow

## Objective
Deliver complementary cross-device login experiences that support both (a) a signed-in browser session approving a mobile device and (b) an authenticated phone session unlocking a browser login request, all while preserving explicit user approval and traceability of every hand-off.

## Flow A – Browser Mints QR, Mobile Requests Access

### Implementation Overview
- **Generate QR** – `src\main\java\org\example\magiclink\controller\QRController.java:36` (`generateQR`) issues the token through `QRService.generateQRToken`, renders the QR image, and tracks the issuing session.
- **Scan QR** – `QRController.scanQR` binds the first scanning session to the token, records device/IP metadata, and prevents the creator session or subsequent scanners from reusing it.
- **Approve / Deny** – `QRController.approveQR` and the POST handlers at the same path ensure only the original session can approve or deny and only after a device has scanned the code.
- **Status Polling & Login** – `QRController.checkStatus` mediates polling from both devices, exposes scan metadata to the creator session, and authenticates the scanning session once approval occurs.
- **Token Lifecycle** – `src\main\java\org\example\magiclink\service\QRService.java` centralises expiry checks (`hasExpired`/`markExpired`), enforces single-session consumption, and rejects approvals without a recorded scan. `QRTokenEntity` tracks both generating and scanning session IDs.

### Front-End Support
- `src\main\resources\templates\qr-approve.html` shows live device/IP details, disables the action buttons until a scan occurs, and keeps polling so the approving browser immediately sees success/denial outcomes while surfacing inline errors if the approval request fails.
- `src\main\resources\templates\qr-scan.html` remains responsible for polling until the approval arrives and relaying success or denial states to the scanning device.

### Key Safeguards
- Creator session ID must match on approval/denial (`QRController.approveQRPost` / `denyQR`).
- Scanning session ID must match on consumption (`QRService.consumeToken`).
- Tokens auto-expire and transition to `EXPIRED` without leaking metadata.
- Duplicate scans and self-consumption attempts are rejected early in `scanQR`.

## Flow B – Authenticated Mobile Unlocks Browser Login

### Objective
Allow a user who is already signed in on their phone to complete a pending browser login by scanning a QR code that represents the browser’s login request—mirroring WhatsApp/Discord pairing flows while keeping the phone as the trusted authenticator.

### UX Sequence
1. User navigates to the desktop login page and selects “Use phone to sign in.”
2. Browser generates a short-lived login request token and renders it as a QR code.
3. Authenticated phone session opens the app’s “Approve browser login” scanner.
4. Phone scans the QR, reviews browser metadata (device, IP, approximate location), and confirms.
5. Browser polls for the approval outcome, then finalises the session once the phone confirms.

### Implementation Details
- **Token issuance** - `QRService.generateBrowserLoginToken` (`src/main/java/org/example/magiclink/service/QRService.java`) creates `BROWSER_TO_BROWSER` tokens, locks them to the requesting browser session, and records the browser fingerprint (user agent + IP).
- **Browser endpoints** - `BrowserLoginController` (`src/main/java/org/example/magiclink/controller/BrowserLoginController.java`) exposes `/qr/login/generate`, `/scan`, `/approve`, `/deny`, and `/status`. The controller guards authenticated states, persists approvals coming from phone sessions, and signs the browser in once the token is consumed.
- **Entity changes** - `QRTokenEntity` (`src/main/java/org/example/magiclink/entity/QRTokenEntity.java`) now stores a `loginType` enum and optional `userEmail` so Flow A and Flow B can share the same persistence model without nullability conflicts.
- **Shared service logic** - `QRService.generateQRCodeImage` now picks `/qr/scan` vs `/qr/login/scan` URLs based on token type while reusing the approve/deny/consume helpers with Flow-specific guards.

### Front-End Support
- Desktop login uses `qr-login-generate.html` (`src/main/resources/templates/qr-login-generate.html`) to render the QR code and poll `/qr/login/status` until approval, surfacing success, denial, or expiry states in place.
- Mobile approval relies on `qr-login-scan.html` (`src/main/resources/templates/qr-login-scan.html`) to review browser metadata and POST approve/deny actions from an authenticated phone session.
- The password login page (`src/main/resources/templates/login.html`) now links to the QR option so Flow B is discoverable alongside existing login choices.\r\n\r\n### Security Considerations
- Require the mobile session to re-confirm biometric/PIN if the login request appears risky (new IP, device fingerprint score below threshold).
- Embed browser fingerprint hash and CSRF token inside the QR payload so the phone can cryptographically bind its approval to that specific browser.
- Enforce single-consumption semantics and TTL parity with Flow A to limit replay windows.
- Log both the origin browser IP and approving phone device for audit trails.

## Next Steps
- Persist approval timestamps for both flows to support comprehensive audit logging.
- Add integration/e2e coverage that exercises Flow B alongside the existing Flow A happy path and denial scenarios.
- Evaluate mobile re-auth prompts (biometric/PIN) during Flow B to balance usability with security.



