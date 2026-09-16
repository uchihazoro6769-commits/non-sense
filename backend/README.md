# Veyra Go backend — phone/SMS authentication

Veyra v10 no longer uses a username/password login. The app requests a one-time 6-digit SMS verification code and verifies it through this backend.

## Production requirements

- Use HTTPS in front of the Go service (Caddy/Nginx/load balancer).
- Set `DATABASE_URL` to PostgreSQL.
- Set `SMS_PROVIDER_URL` to your HTTPS SMS gateway endpoint and `SMS_PROVIDER_TOKEN` if required.
- The gateway receives JSON `{ "to": "+998...", "message": "..." }`.
- Replace the provider adapter with your chosen provider (Twilio, Infobip, Eskiz, etc.) if needed.
- Add Redis-backed rate limiting and signed access/refresh tokens before production deployment.

## Run

```bash
go mod tidy
go run .
```

Example environment:

```text
DATABASE_URL=postgres://user:password@localhost:5432/veyra
LISTEN_ADDR=:8080
SMS_PROVIDER_URL=https://your-sms-gateway.example/send
SMS_PROVIDER_TOKEN=...
```

For a physical Android phone, the app's `API_BASE_URL` must point to the PC/server's LAN HTTPS address. `10.0.2.2` is only for the Android emulator.
