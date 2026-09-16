# Veyra v14 — Email identity

Phone/SMS authentication has been removed from the Android login flow and Go auth server.

## Identity
- Veyra ID: `3–32` characters, `A-Z`, `a-z`, `0-9`, `_`
- Email: `username@veyra.com`
- Password: 10–128 characters
- The Android client is configured for `https://api.veyra.com`

## Backend endpoints
- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `GET /v1/auth/me`
- `POST /v1/auth/logout`
- `GET /health`

## Security
- PostgreSQL stores users.
- Passwords use PBKDF2-HMAC-SHA256 with a random 128-bit salt and 210,000 iterations.
- Session tokens are 256-bit random values; only SHA-256 hashes are stored in PostgreSQL.
- Sessions expire after 30 days.
- Registration/login attempts are rate-limited.
- Request bodies are size-limited and reject unknown JSON fields.
- Security headers are enabled.

## Important
`@veyra.com` is an identity namespace in the app. For real internet email delivery/receiving, the `veyra.com` domain must actually be owned and configured with DNS/MX records and a mail service. This version does not pretend that a domain is available when it has not been registered.
