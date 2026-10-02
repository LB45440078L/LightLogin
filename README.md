# LightLogin

Modern, secure and high-performance authentication for Paper servers (Minecraft 26.1+).

LightLogin is a complete rewrite of the original `top.cmarco.LightLogin` plugin. It keeps the
feature set operators rely on — register/login, CAPTCHA, blindness, void login world, email
recovery, IP limits — and rebuilds it on a clean, tested, security-first architecture.

```
Java 25 · Paper API 26.1 · 3 Maven modules · 125 unit/integration tests · Argon2id + AES-256-GCM
```

## Highlights

| Area | What changed |
|---|---|
| Password storage | Argon2id in PHC format (`$argon2id$v=19$m=65536,t=3,p=1$salt$hash`). The salt and work factors travel **with** the hash, so a stored hash can never lose its salt again. Optional server-side pepper (HMAC-SHA-512) so a database leak alone cannot be cracked offline. |
| Secrets at rest | The SMTP password and database password are encrypted with AES-256-GCM under a 256-bit key file (`0600`) created on first run. |
| Anonymous login | Passwords can be typed into a private anvil window so they never become a command argument or a chat line. A Log4j2 filter **drops** any log line containing a credential-bearing command, and the authentication commands are hidden from the client's command list until you are authenticated. |
| Anti-bot | Two CAPTCHA strategies: an arithmetic question, and a hashcash proof-of-work whose cost per attempt makes automated flooding expensive. Per-IP rate limiting, per-account lockout, join-flood throttling. |
| Async | Every hashing and database operation runs on a virtual thread behind a bounded semaphore, so the main thread never touches a login and a burst cannot exhaust the heap. |
| Admin website | A full administration panel served from the JDK's built-in HTTP server (no extra dependency): dashboard, player list, per-player actions, IP bans, audit log. Loopback by default; refuses a public bind unless explicitly enabled. |
| Moderator GUI | An inventory menu to browse players and reset passwords, ban/unban addresses and unregister accounts, with a confirmation step. |
| Nations | Country blocking via an optional MaxMind GeoLite2 database; inert (never blocks) when absent. |
| Tests | 130 tests: 125 unit tests plus 5 that load the **shaded jar** itself and open a real SQLite connection — the check that catches packaging bugs no unit test can see. Includes the real HTTP panel over a socket, the real JDBC layer against SQLite, and an audit that every config key is read and every message key exists. |

## Requirements

* **Paper 26.1 or newer** (also works on 26.2/26.3 lines)
* **Java 25 or newer** at runtime
* Optional: MySQL/MariaDB or PostgreSQL (SQLite is bundled and is the default)
* Optional: an SMTP account for email recovery
* Optional: a MaxMind GeoLite2 Country database for nation blocking

## Install

1. Build (see below) or take `lightlogin-paper/target/lightlogin-paper-3.0.0.jar`.
2. Drop it into your server's `plugins/` directory.
3. Start the server once. LightLogin writes `config.yml`, `messages.yml`, `gui.yml` and a
   `lightlogin.key` master key into `plugins/LightLogin/`.
4. Set the pepper environment variable (strongly recommended) — see
   [docs/02-security.md](docs/02-security.md):
   ```bash
   export LIGHTLOGIN_PEPPER="$(head -c 48 /dev/urandom | base64)"
   ```
5. Review `config.yml` and restart.

## Build

Builds run on JDK 27 with `--release 25`; the artifact targets Java 25 bytecode.

```bash
mvn clean verify                 # full build: 125 unit tests + 5 integration tests
mvn clean package                # build + unit tests (stops before the jar integration tests)
mvn -Pslim clean package         # smaller jar, excludes server drivers and GeoIP
mvn -pl lightlogin-core test     # one module
```

Output: `lightlogin-paper/target/lightlogin-paper-3.0.0.jar`

Use `verify`, not `package`, for the complete suite: the integration tests inspect the shaded jar, so
they run in the phase after packaging.

The shaded jar is about **24 MB**, dominated by the SQLite native libraries (~12 MB, needed for
portability) and BouncyCastle's Argon2 implementation (~5.5 MB). The `slim` profile drops the
PostgreSQL/MariaDB drivers and the GeoIP reader, producing about **20 MB**. See
[docs/07-development.md](docs/07-development.md) for the full breakdown.

## Documentation

* [Security analysis and design rationale](docs/00-security-analysis.md) — the threat model and the
  algorithms chosen, with sources.
* [Architecture](docs/01-architecture.md) — modules, ports and adapters, concurrency model.
* [Security model](docs/02-security.md) — what is protected, how, and what is *not* possible.
* [Configuration reference](docs/03-configuration.md) — every key.
* [Commands and permissions](docs/04-commands-and-permissions.md)
* [Administration panel](docs/05-admin-panel.md)
* [Migrating from the original plugin](docs/06-migration-from-original.md)
* [Development and testing](docs/07-development.md)

## Licence

GNU General Public License v3.0 — see [LICENSE](LICENSE). Original plugin © 2024 CMarco; this
rewrite retains the same licence.