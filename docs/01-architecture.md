# Architecture

## Modules

```
lightlogin-parent
├── lightlogin-core          domain + crypto + captcha + rate limiting + ports + web panel
├── lightlogin-persistence   JDBC adapters (SQLite / MariaDB / PostgreSQL), portable schema
└── lightlogin-spigot        the Bukkit plugin: commands, listeners, GUI, void world, wiring
```

`lightlogin-core` has **no dependency on any Minecraft server API**. That is the single most
important structural decision in the rewrite: it means the cryptography, the password policy, the
CAPTCHA engine, the rate limiters, the ban logic and the whole administration website are unit
testable on a plain JVM, with no server to boot and no mocking framework.

Dependency direction is strictly one-way:

```
paper ──▶ persistence ──▶ core
  │                         ▲
  └─────────────────────────┘
```

The core never depends on the adapters. The persistence module depends only on the core's
repository *interfaces*.

## Ports and adapters

The core declares the storage it needs as interfaces (`dev.lightlogin.core.port`):

| Port | Purpose |
|---|---|
| `AccountRepository` | accounts, password hashes, failure counters, locks, paging |
| `SessionRepository` | login sessions, stored as token digests |
| `IpBanRepository` | address and CIDR bans |
| `AuditRepository` | append-only audit trail |
| `AdminAccountRepository` | panel administrators |

`lightlogin-persistence` implements them over JDBC. A test implements them over a `HashMap`
(`dev.lightlogin.core.support.InMemoryAccountRepository`). Neither the core nor its tests mention
`java.sql`: `SQLException` is translated to the unchecked `StorageException` in exactly one place
(`JdbcSupport`), which is why the domain interfaces can stay clean.

## Application services

`dev.lightlogin.core.service` holds the use cases:

* **`AuthService`** — register, login, change password, unregister. Holds no per-player state, so it
  is shared freely across workers.
* **`SessionService`** — issues and validates sessions; binds a session to its address.
* **`IpBanService`** — decides whether an address may connect, combining whitelist, explicit bans
  and country rules, cheapest check first.
* **`AuditService`** — the single choke point where audit detail is redacted before it is stored.
* **`RecoveryService`** — email password recovery, conservative by design (uniform outcomes, per
  account cooldown, session invalidation on success).

These are synchronous and blocking by contract, and are only ever called from the async executor.

## The Bukkit adapter

`dev.lightlogin.paper` is organised by concern:

| Package | Responsibility |
|---|---|
| `bootstrap` | `LightLoginBootstrap` (the startup sequence) and `PluginContext` (the composition root) |
| `config` | `BukkitConfigSource` — the only bridge from Bukkit's YAML to the core's config model |
| `command` | `CommandRegistry` plus `AuthCommands`, `AccountCommands`, `AdminCommands` |
| `listener` | connection gating, pre-login restrictions, chat blocking |
| `gui` | the moderator menu and the anvil-based private password input |
| `auth` | `AuthGate` (pending-login state) and `LoginEffects` (apply/restore) |
| `world` | the void login world |
| `mail` | the SMTP adapter |
| `geo` | the MaxMind country resolver |
| `log` | the console banner and the credential-redacting log filter |
| `messages` | the message service |
| `task` | the single periodic maintenance task |

### Composition root

`PluginContext` is built once with a builder and is immutable thereafter. The one exception is the
configuration, which is `volatile` and swapped wholesale on reload — that is precisely how a reload
reaches every component that captured a reference. A component never reads the file; it reads
`ctx.config()`.

`LightLoginBootstrap` performs a fixed, visible startup sequence:

```
config → integrity check → secrets (key file, pepper) → database + migrations
      → services → void world → log filter → context → commands → listeners
      → admin panel → maintenance task
```

Each stage either succeeds, degrades to a documented fallback (no pepper, no mail, no panel, no
GeoIP), or fails the enable with a clear message. It never leaves a half-wired context behind.

## Concurrency model

```
main thread ──▶ command/listener ──▶ AsyncExecutor.submit ──▶ virtual thread
                                          │  (bounded by a Semaphore)
                                          ▼
                                    Argon2 / JDBC / SMTP
                                          │
main thread ◀── Bukkit scheduler ─────────┘  (result applied: teleport, message, inventory)
```

* `AsyncExecutor` wraps `Executors.newThreadPerTaskExecutor` with a fair `Semaphore` sized by
  `rate-limit.max-concurrent-auth`. Virtual threads are nearly free; the permit exists for *memory*
  backpressure, because a 64 MiB hash holds 64 MiB for its duration.
* Blocking work never touches the main thread; world mutations never happen off it.
* `AuthGate` is the only shared mutable per-player state, and it is backed by `ConcurrentHashMap`.

## Data model

Immutable records in `dev.lightlogin.core.model`:

* `Account` — password hash, status, failure counter, lock expiry, last/registration IP.
  Every mutation produces a new instance via `withX`, which removes aliasing bugs where a cached
  account is mutated behind a repository's back.
* `AuthResult` — a **sealed interface** whose cases carry exactly the data their handler needs
  (`Success`, `WrongPassword(attemptsRemaining)`, `Locked(untilMillis)`, `PolicyRejected(violations)`,
  …). The compiler enforces that every case is handled; there is no ambiguous boolean.
* `CaptchaResult` — likewise sealed.
* `Session`, `IpBan`, `AuditEntry`, `AdminAccount`.

## Persistence

One portable DDL runs unchanged on all three backends (`db/migrations/V1__init.sql`). It avoids
every construct that differs between them: application-generated `VARCHAR(36)` UUID keys (no
identity columns), `BIGINT` epoch-millis timestamps (no native `TIMESTAMP`), `INTEGER` 0/1 booleans
(SQLite has no `BOOLEAN`), `DOUBLE PRECISION` floats.

Migrations are listed in an explicit `index.txt` read from the classpath, because directory scanning
silently finds nothing inside a packaged jar. Each migration and its ledger row commit in one
transaction, so an interrupted upgrade leaves a consistent version.

The audit table's monotonic id comes from a counter row updated in the same transaction as the
insert — portable (no identity column), race-free (the `UPDATE` takes a row lock that serialises
writers) and monotonic across restarts. A test exercises this with eight concurrent writers.

HikariCP is configured with `initializationFailTimeout(-1)`: a briefly unreachable database does not
prevent startup, and the first real query surfaces the problem to a caller already prepared to
degrade. SQLite uses a small pool (≤ 4) with `PRAGMA busy_timeout` so a blocked writer waits instead
of failing.

## The administration website

`dev.lightlogin.core.web` serves the panel from the JDK's built-in `com.sun.net.httpserver`, so it
adds no dependency to the shaded jar. `AdminService` is the privileged surface: every mutating method
takes an `AccessToken` and verifies it before acting. The handlers, the session store and the
templates are all in the core, which is why the panel is covered by an integration test that starts
the real server on an ephemeral port and drives the full login → dashboard → action flow with a real
HTTP client.