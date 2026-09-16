# Veyra API production deployment

This is the server side of Vayra. The Android app talks to `https://api.veyra.com`; users never configure a server URL.

## DNS
Create an A/AAAA record for `api.veyra.com` pointing to the VPS. No website is required. Caddy only terminates HTTPS and proxies the Veyra API.

## VPS
Install Docker and Docker Compose, copy `.env.example` to `.env`, set a strong `POSTGRES_PASSWORD`, then run:

```bash
docker compose up -d --build
```

Caddy obtains and renews the TLS certificate automatically once DNS points to the server and ports 80/443 are reachable.

## Verify

```bash
curl https://api.veyra.com/health
```

Expected response:

```json
{"ok":true,"service":"vayra-api"}
```

The backend does not serve HTML pages. `api.veyra.com` is an API endpoint used by the Veyra Android app.
