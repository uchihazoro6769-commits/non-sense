# Vayra V14 server architecture

Veyra is an app-first service. There is no required public website and no server/IP field in the Android UI.

- Android: `https://api.veyra.com`
- Go API: authentication and future chat/realtime APIs
- PostgreSQL: persistent account/session data
- Caddy: HTTPS reverse proxy only; it does not host a website
- Docker restart policies keep services running after reboot

## Account flow

1. User enters a Vayra ID, `username@veyra.com`, and password.
2. Android POSTs to `/v1/auth/register` or `/v1/auth/login`.
3. Go validates input, hashes the password with PBKDF2-HMAC-SHA256, and creates a random session token.
4. Only the SHA-256 hash of the session token is stored in PostgreSQL.
5. Android stores the session token locally and sends it as `Authorization: Bearer ...`.

`@veyra.com` is an application namespace in V14. Real internet email delivery requires control of the `veyra.com` domain and a mail service/MX records; it is separate from Veyra account authentication.
