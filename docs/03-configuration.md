# Configuration reference

Every key in `config.yml` is read by the plugin; a key that nothing reads is a test failure
(`ResourceAuditTest`), so this page and the shipped file cannot drift apart silently.

Values below the OWASP floor are **raised automatically** rather than rejected.

## `config-version`

The schema version this file was written for. The plugin understands version `2`. A newer file
should be treated as written by a newer plugin.

## `language`

Reserved for localisation of console output; `en`.

## `database`

| Key | Default | Notes |
|---|---|---|
| `type` | `SQLITE` | `SQLITE`, `MARIADB`/`MYSQL`, `POSTGRESQL`. An unknown value falls back to SQLite. |
| `host` | `127.0.0.1` | Server databases only. |
| `port` | `3306` | Server databases only. |
| `name` | `lightlogin` | Database/schema name. |
| `username` | `root` | |
| `password` | `''` | Stored encrypted after the first run. |
| `sqlite-file` | `lightlogin.db` | Relative to `plugins/LightLogin/`. |
| `pool-size` | `6` | SQLite is capped at 4 internally. |
| `connection-timeout-millis` | `10000` | |

The pool never fails fast: a briefly unreachable database does not prevent startup. A database that
is *permanently* unreachable, or a driver that cannot load, fails startup immediately with a message
naming the real cause (see
[07-development.md § Startup database probe](07-development.md#startup-database-probe)). For SQLite,
that first connection is also what creates the `lightlogin.db` file.

## `security`

| Key | Default | Notes |
|---|---|---|
| `argon2.memory-kib` | `65536` | 64 MiB. The most valuable knob; raise it first. |
| `argon2.iterations` | `3` | |
| `argon2.parallelism` | `1` | Keep at 1: raising it speeds up evaluation and reduces memory-hardness. |
| `argon2.salt-bytes` | `16` | |
| `argon2.hash-bytes` | `32` | |
| `pepper-env` | `LIGHTLOGIN_PEPPER` | Name of the environment variable holding the pepper. |
| `key-file` | `lightlogin.key` | Master key for encrypted secrets; created `0600`. |
| `session-ttl-millis` | `43200000` | 12 hours. |
| `max-failed-attempts` | `5` | Before a lockout. |
| `lockout-millis` | `900000` | 15 minutes. `0` disables locking. |
| `mask-commands-in-logs` | `true` | Mask password-shaped command arguments in plugin output. |
| `audit-retention-days` | `90` | Older audit rows are purged hourly. |

## `password-policy`

| Key | Default |
|---|---|
| `min-length` / `max-length` | `8` / `64` |
| `min-uppercase` / `min-lowercase` | `1` / `1` |
| `min-digits` / `min-special` | `1` / `1` |
| `allowed-special` | `! @ # $ % ^ & * - _ ? + =` |
| `deny-username` | `true` |
| `deny-common` | `true` — enforced against a bundled blocklist of common passwords |
| `banned-substrings` | `[]` — e.g. your server name |

Every violation is reported at once, not one at a time.

## `captcha`

| Key | Default | Notes |
|---|---|---|
| `enabled` | `true` | |
| `mode` | `ARITHMETIC` | or `PROOF_OF_WORK` |
| `max-attempts` | `3` | |
| `ttl-millis` | `120000` | Challenge lifetime. |
| `cooldown-millis` | `5000` | Minimum interval between re-issues; stops farming easy questions. |
| `difficulty-bits` | `16` | Proof-of-work only. 16 ≈ 65 000 hashes; 20+ is a real cost to a bot. |
| `require-for-register` | `true` | |
| `require-for-login` | `false` | |
| `punish-on-failure` | `true` | Triggers an automatic ban on exhaustion. |

## `rate-limit`

Token buckets per IP (`*-burst` is the allowance, `*-rate` the sustained per-second rate).

| Key | Default |
|---|---|
| `connection-burst` / `connection-rate` | `8` / `1.5` |
| `login-burst` / `login-rate` | `6` / `0.5` |
| `command-burst` / `command-rate` | `6` / `2.0` |
| `max-concurrent-auth` | `8` — the Argon2 memory backpressure ceiling |
| `join-throttle-millis` | `0` |
| `max-joins-per-second` | `20` |
| `auto-ban-on-flood` | `true` |

## `safety`

| Key | Default | Notes |
|---|---|---|
| `max-players-per-ip` | `3` | `0` disables. |
| `max-registrations-per-ip` | `2` | `0` disables. |
| `ip-bans-enabled` | `true` | |
| `auto-ban-on-bruteforce` | `true` | |
| `bruteforce-ban-millis` | `3600000` | |
| `country-blocking.enabled` | `false` | Requires a GeoIP database. |
| `country-blocking.blocked` | `[]` | ISO-3166 alpha-2, upper-cased automatically. |
| `country-blocking.allowed` | `[]` | When non-empty, only these are permitted. |
| `country-blocking.geoip-database` | `''` | Path to a MaxMind GeoLite2 Country `.mmdb`. |
| `whitelisted-ips` | `[]` | Addresses or CIDR blocks exempt from every IP check. |

Without a database, country blocking is **inert**: it never blocks, rather than failing closed.

## `void-world` (experimental)

| Key | Default |
|---|---|
| `enabled` | `false` |
| `name` | `lightlogin_void` |
| `mode` | `NORMAL` or `THE_END` |
| `spawn-y` | `100.0` |
| `return-to-original` | `true` — restores the join location after login |

A second world has a real cost (its own chunk cache). Leave it off unless unauthenticated entities
in the main world are a problem for you.

## `email`

| Key | Default |
|---|---|
| `enabled` | `false` |
| `smtp-host` / `smtp-port` | `smtp.example.com` / `587` |
| `use-tls` | `true` — STARTTLS is *required*, not merely offered |
| `account` / `password` | Sender address and credential (encrypted after the first run) |
| `sender-name` | `LightLogin` |
| `subject` | Recovery-mail subject |
| `body` | Body lines; `{PLAYER}` and `{PASSWORD}` placeholders |
| `recovery-password-length` | `12` |
| `recovery-cooldown-millis` | `5400000` (90 min) |
| `connection-timeout-millis` | `15000` |

## `web-panel`

| Key | Default | Notes |
|---|---|---|
| `enabled` | `false` | |
| `bind-address` | `127.0.0.1` | A non-loopback bind requires `allow-external-access`. |
| `port` | `8099` | `0` requests an ephemeral port. |
| `allow-external-access` | `false` | |
| `trust-proxy-headers` | `false` | Only enable behind a known reverse proxy. |
| `session-ttl-millis` | `1800000` | |
| `max-login-attempts` | `5` | |
| `login-lockout-millis` | `600000` | |
| `require-totp` | `false` | |
| `page-size` | `25` | |
| `external-redirect-url` | `''` | Redirect `/` to an externally hosted front end. |

## `login`

| Key | Default | Notes |
|---|---|---|
| `timeout-millis` | `120000` | Kick after this long unauthenticated. |
| `blindness` | `true` | Blindness effect while pending. |
| `titles.enabled` | `true` | Title prompt. |
| `action-bar.enabled` | `true` | Countdown in the action bar. |
| `reminder-seconds` | `5` | Reminder interval. |
| `allowed-commands` | `login, register, verify, email, resetpassword` | Runnable while pending. |
| `command-delay-millis` | `1000` | Anti-autoclick delay before a login is accepted. |
| `teleport.enabled` | `false` | |
| `teleport.login-location` | `''` | `world,x,y,z` |
| `teleport.return-location` | `''` | `world,x,y,z`, or `latest` |
| `auto-login-after-register` | `true` | |

## Reload semantics

`/lightlogin reload` re-reads `config.yml`, `messages.yml` and `gui.yml`, and applies the settings
that are read live (login flow, allowed commands, messages, GUI layout). **Security, database,
CAPTCHA and rate-limit settings are captured when the services are built and need a full restart.**
The reload prints a warning saying exactly that, rather than pretending otherwise.