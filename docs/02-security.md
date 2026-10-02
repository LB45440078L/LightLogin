# Security model

Read [00-security-analysis.md](00-security-analysis.md) first for the threat model and the reasoning
behind each choice. This page is the operational summary: what is protected, how, and what the
plugin cannot do.

## What is protected

### Passwords

* **Argon2id** with a 64 MiB / 3 iteration / 1 lane default, clamped up to the OWASP floor
  (19 MiB / 2 / 1) if configured weaker.
* Stored in **PHC format**, so the salt and parameters travel with the digest and a hash is always
  verifiable.
* Optional **pepper** (`LIGHTLOGIN_PEPPER`, HMAC-SHA-512) so a database dump alone cannot be cracked.
* **Constant-time** verification (`MessageDigest.isEqual`).
* Plaintext exists only as `char[]`, wiped immediately after use.
* Transparent **re-hash** on login when the stored parameters are weaker than the current profile.

### Secrets in configuration

* SMTP password and database password are **AES-256-GCM** encrypted under a 256-bit key file created
  with `0600` permissions.
* Plaintext values are encrypted and written back on first run, so the file converges to ciphertext.
* GCM provides integrity: a tampered value fails to decrypt instead of yielding chosen plaintext.

### Sessions

* Tokens are 256-bit, URL-safe Base64, from a `SecureRandom`.
* Only the **SHA-256 digest** is stored; a database leak cannot be replayed.
* A session is bound to the address it was created from; use from another address invalidates it.
* Invalidated by unlogin, password change, reset and unregister.

### Logs and console

* A Log4j2 filter **denies** any log line containing a credential-bearing command.
* The plugin's own output passes through a `SecretRedactor` that masks registered secrets (access
  token, pepper) and password-shaped arguments.
* The audit trail never records a password, a hash or a token; it records events.
* The authentication commands are hidden from the client's command list while unauthenticated.

### The administration panel

* Separate credentials from player accounts, hashed with the same Argon2id hasher.
* **Loopback bind by default**; a non-loopback bind throws unless
  `web-panel.allow-external-access: true`.
* Per-IP login rate limiting and optional **TOTP** two-factor authentication (RFC 6238, implemented
  on the JDK's HMAC — no extra dependency).
* `HttpOnly`, `SameSite=Strict` session cookies, `Secure` when bound externally.
* A **per-session CSRF token** required on every mutating request, plus a separate login-form CSRF
  token compared against a cookie.
* Strict `Content-Security-Policy` (`default-src 'none'`), `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.
* All interpolated values HTML-escaped.
* Sessions are in-memory only: a restart invalidates them.

### Internal API

* Privileged operations take an `AccessToken` minted from a CSPRNG at boot; a wrong token throws
  `SecurityViolationException` and increments a counter.
* `IntegrityGuard` reports `-javaagent` instrumentation at startup with a loud warning.

## What the plugin cannot do

**Isolate itself from another plugin.** All Bukkit plugins share one JVM and reflection bypasses
access modifiers. The token gate raises the cost and the visibility of an attack; it does not make
one impossible. The complete answers are a separate JVM or an OS sandbox.

**Protect a compromised host.** If an attacker has root, the master key file, the environment and
the process memory are all readable. Encrypting secrets at rest protects against a leaked *backup*
or a leaked *database*, not against root.

**Guarantee GeoIP accuracy.** Country blocking depends on a regularly updated database and will
misclassify addresses; it also blocks VPNs that exit in a blocked country.

**Stop a determined distributed flood.** Per-IP limits do nothing against a botnet of thousands of
addresses. The proof-of-work CAPTCHA and the bounded concurrency limit are what make such a flood
expensive rather than free; a real mitigation belongs at the network edge (a proxy, a firewall, a
scrubbing service).

## Operational checklist

- [ ] Set `LIGHTLOGIN_PEPPER` to 32+ random characters **before** the first registration, and back it
      up. Losing it makes every stored hash unverifiable.
- [ ] Back up `plugins/LightLogin/lightlogin.key` separately from the database.
- [ ] Keep `lightlogin.key` at `0600` and outside world-readable backups.
- [ ] Prefer SQLite or a database on a private network; never expose the database port publicly.
- [ ] Leave the panel on `127.0.0.1`. If you must expose it, put it behind a TLS-terminating reverse
      proxy, set `trust-proxy-headers: true` only then, and enable `require-totp`.
- [ ] Change the bootstrap administrator password immediately after the first start.
- [ ] Check the startup log for `IntegrityGuard` warnings; treat any unexpected `-javaagent` as a
      compromise.
- [ ] Raise `security.argon2.memory-kib` as your hardware allows; memory is the knob that ages best.
- [ ] Enable `captcha.mode: PROOF_OF_WORK` if you are under automated attack.

## Reporting

This is a rewrite of an open-source plugin. Report a vulnerability through the repository's issue
tracker, or privately if the issue is exploitable in production.