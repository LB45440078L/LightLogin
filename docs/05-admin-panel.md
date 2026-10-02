# Administration panel

LightLogin serves a full administration website from inside the plugin, using the JDK's built-in
`com.sun.net.httpserver`. No extra dependency is added to the shaded jar, and no external web server
is required.

## Enabling it

```yaml
web-panel:
  enabled: true
  bind-address: 127.0.0.1   # loopback only, by default
  port: 8099
  allow-external-access: false
```

Start the server. On the first start with the panel enabled, a bootstrap administrator is created
and its password is printed **once** to the console:

```
[LightLogin] » A bootstrap administrator was created.
[LightLogin] » Username: admin    Password: <generated>
[LightLogin] » Sign in and change this password immediately.
```

Open `http://127.0.0.1:8099/` and sign in. The password is stored as an Argon2id hash; it cannot be
recovered, only reset.

## Pages

| Path | What it does |
|---|---|
| `/` | Dashboard: account count, active bans, live sessions, audit entries, and the 25 most recent audit events. |
| `/login` | Sign-in form (with a CSRF token and an optional 2FA field). |
| `/players` | Paginated player list, searchable by username. |
| `/player?uuid=…` | Per-player detail with actions: reset password, ban last IP, unregister. Shows that player's audit history. |
| `/bans` | Ban list, an add-ban form (address or CIDR, reason, duration), and per-row unban. |
| `/logs` | Paginated audit log. |
| `/logout` | Destroys the session. |

## Roles

| Role | Can do |
|---|---|
| `VIEWER` | Read statistics, players and logs. |
| `MODERATOR` | Everything a viewer can, plus reset passwords, ban/unban addresses and unregister accounts. |
| `ADMIN` | Everything, including managing the panel and configuration. |

Roles are stored per administrator account. The bootstrap account is an `ADMIN`.

## Security

The panel is treated as a privileged surface, not a convenience:

* **Credentials are separate** from player accounts and hashed with the same Argon2id hasher.
* **Loopback by default.** `start()` throws if `bind-address` is not loopback and
  `allow-external-access` is false, so exposure is always a deliberate act.
* **Per-IP login rate limiting**, plus an optional **TOTP** second factor (RFC 6238, implemented on
  the JDK's HMAC; set `require-totp: true` to make it mandatory).
* **CSRF** on every mutating request, plus a separate login-form CSRF token compared against a
  cookie. A mismatch is recorded as a security violation.
* **Session cookies** are `HttpOnly`, `SameSite=Strict`, and `Secure` when bound externally.
* **Strict response headers**: `Content-Security-Policy: default-src 'none'; …`,
  `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `Cache-Control: no-store`.
* **All interpolated values are HTML-escaped.**
* **Sessions are in-memory**: a restart invalidates every one of them, which is the safe default for
  an administrative surface.
* **The privileged API is token-gated.** `AdminService` methods take an `AccessToken` that must match
  the token minted at boot, so another plugin cannot drive the panel's destructive operations by
  simply calling a public method.

## Exposing it externally

If you must reach the panel from outside the machine:

1. Put it behind a **TLS-terminating reverse proxy** (nginx, Caddy, Traefik) on the same host.
2. Set `bind-address` to the proxy's upstream interface (or keep loopback and proxy to it) and set
   `allow-external-access: true`.
3. Set `trust-proxy-headers: true` **only** if the proxy is the sole route in — otherwise a client
   can forge `X-Forwarded-For` and defeat the per-IP rate limiting.
4. Set `require-totp: true`.
5. Restrict the proxy route by IP allowlist if possible.

Never expose the panel directly on a public interface without TLS: session cookies and credentials
would travel in clear text.

## External front end

If you would rather host the UI yourself, set `external-redirect-url` to your URL. A request to `/`
is then redirected there. The panel's own routes still exist for programmatic use.

## Resetting an administrator

There is no self-service reset. To recover access:

1. Stop the server.
2. Delete the `ll_admin_accounts` table (or the whole SQLite file, if you have no players to keep).
3. Start the server; a new bootstrap administrator and password are generated.

## Testing

The panel is covered by an integration test that starts the real HTTP server on an ephemeral port
and drives the whole flow with a real HTTP client: unauthenticated redirect, the login page and its
security headers, successful and failed sign-in, CSRF rejection, ban/unban, password reset, the
players page, and logout. It also asserts that a non-loopback bind is refused unless external access
is enabled.