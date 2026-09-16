# Veyra Feature Plan

## Identity
- Veyra is a messenger-first app.
- Account identity uses `username@veyra.com`.
- Current model: 1 phone -> 1 account.
- Phone-number privacy/account fields are not part of Veyra.

## Chat
- No fake/automatic starter messages.
- Message status text: `Delivered` until the recipient actually reads it, then `Read`.
- Every message shows its sent time.
- Chat list swipe left -> Archive.
- Chat list swipe right -> Delete.
- Delete has Undo confirmation.
- Archive is a separate section.
- Text, image and file messages.
- Audio message recording, sending and local playback UI.
- Audio calls and video calls UI; production signaling/WebRTC remains a backend integration task.

## Settings / Privacy & Security
### Security
- Two-Step Verification
- Auto-Delete Messages
- Local passcode
- Passkeys
- Login Email
- Blocked users
- Active Sessions: visible only when the account is marked Premium.

### Privacy
- Last seen & online
- Profile photos
- Forwarded messages
- Calls
- Voice messages
- Messages
- Birthday
- Gifts
- Bio
- Saved Music
- Invites
- Bots and websites

### Language
Settings provides:
- English
- Русский
- O‘zbekcha

The selected language is persisted locally. User-authored messages are never translated by the UI.

## Security scope
Veyra Security protects content accessed through Veyra chats only.

### APK
Quarantine -> scan -> risk analysis -> Safe / Suspicious / Dangerous.
Checks include signature, permissions, manifest, suspicious components, URLs/domains, native libraries, hashes/reputation and other indicators.

### Links
Domain/reputation -> redirect checks -> phishing/malware indicators -> Safe / Suspicious / Dangerous.

Veyra does not claim antivirus protection for APKs or links obtained outside Veyra.

## App security architecture
- Android app sandbox
- Android Keystore
- secure token/session storage
- minimal exported components
- deep-link validation
- isolated scanner/sandbox
- E2EE architecture
- session/device security
- server-side validation

## Backend
Current API:
- POST `/v1/auth/register`
- POST `/v1/auth/login`
- GET `/v1/auth/me`
- POST `/v1/auth/logout`
- GET `/health`

Development server runs on the Linux laptop; Cloudflare Quick Tunnel is development/testing only.

## Threat model
- Brute force -> rate limiting
- Credential stuffing -> anomaly detection/rate limiting
- Session hijacking -> session rotation/device binding
- Phishing -> link reputation/warnings
- Malicious APK -> quarantine/sandbox/scanner
- SQL injection -> parameterized queries/validation
- DDoS -> rate limiting/firewall/upstream protection
- Port scanning -> minimal exposed ports/firewall
- Privilege escalation -> least privilege/service isolation
- Database theft -> encryption/E2EE
- MITM -> TLS/certificate validation
- Replay attacks -> nonce/timestamp/protocol replay protection

## Product scope removals
- Veyra Team: removed.
- Veyra Communities: removed.

## Design order
1. Features and behavior
2. Security
3. Navigation
4. Chat behavior
5. Account/settings
6. Final unified UI/UX redesign
