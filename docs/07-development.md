# Development and testing

## Toolchain

* **Build JDK 27** (the compiler runs on it) with `maven.compiler.release = 25`, so the artifact
  targets Java 25 bytecode — the minimum for the Paper 26.1 line.
* **Maven 3.9+**
* The build was verified with `maven-compiler-plugin` 3.16.0, `maven-surefire-plugin` 3.6.0 and
  `maven-shade-plugin` 3.6.2.

```bash
mvn clean package                     # build + all 125 tests
mvn -Pslim clean package              # smaller jar (excludes server drivers + GeoIP)
mvn -pl lightlogin-core test          # one module
mvn -pl lightlogin-core -am -Dtest=AuthServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Note the `-Dsurefire.failIfNoSpecifiedTests=false`: running a single named test across a reactor with
`-am` fails in every sibling module where that test does not exist without it.

## Module layout

```
lightlogin-core/src/main/java/dev/lightlogin/core/
  crypto/       Argon2id, PHC format, pepper, AES-GCM, key file, tokens, constant time
  captcha/      challenge types and the issue/verify engine
  ratelimit/    token bucket and the per-key registry
  policy/       password policy and the common-password blocklist
  port/         repository interfaces and StorageException
  model/        immutable records and sealed result types
  config/       ConfigSource and the immutable configuration tree + loader
  service/      AuthService, SessionService, IpBanService, AuditService, RecoveryService
  security/     access tokens, integrity guard, secret redactor, TOTP, audit actions
  concurrent/   AsyncExecutor (virtual threads + semaphore)
  web/          the administration panel (HttpServer), session store, templates
  util/         IP parsing/CIDR, hashing
  geo/          CountryResolver port
  mail/         MailSender port

lightlogin-persistence/src/main/java/dev/lightlogin/persistence/
  DataSourceFactory, SchemaMigrator, JdbcSupport, Jdbc*Repository, PersistenceBootstrap
  resources/db/migrations/index.txt, V1__init.sql

lightlogin-paper/src/main/java/dev/lightlogin/paper/
  LightLoginPlugin, bootstrap/, config/, command/, listener/, gui/, auth/, world/,
  mail/, geo/, log/, messages/, task/
```

## Where to make a change

| Task | Files |
|---|---|
| Add a config key | `ConfigLoader` + the relevant config record + `config.yml` + `docs/03-configuration.md`. The audit test fails until the key is in both. |
| Add a message | `messages.yml`. The audit test fails if the code references a missing key. |
| Add a repository method | the port interface + the JDBC implementation + the in-memory test double. |
| Add a schema change | a new `V2__*.sql` **plus** an entry in `index.txt`. Never edit `V1`. |
| Add a command | declare it in `plugin.yml`, add a handler method, register it in `LightLoginBootstrap#registerCommands`. A command declared but not registered is logged at startup. |

## Tests

125 tests across the three modules. Highlights:

| Suite | What it proves |
|---|---|
| `Argon2idPasswordHasherTest` | round-trip, wrong-password rejection, the salt is embedded and never lost, pepper changes the output, `needsRehash` semantics, malformed input is rejected, unsafe parameters are refused |
| `SecretBoxTest` | AES-GCM round-trip, fresh nonce per encryption, tamper detection, wrong key fails |
| `CaptchaEngineTest` | arithmetic verification, attempt budget, expiry, re-issue cooldown, a real mined proof-of-work nonce |
| `PasswordPolicyTest` | every rule, all violations reported together, the blocklist, unicode |
| `AuthServiceTest` | register/login, lockout, rate limiting, per-IP registration limits, IP bans, transparent re-hash, and that **no plaintext password ever reaches the audit trail** |
| `RecoveryServiceTest` | the emailed password actually works, cooldown, no account enumeration, session invalidation |
| `AdminPanelTest` | the real HTTP server over a socket: redirect, security headers, login, CSRF rejection, ban/unban, password reset, logout, external-bind refusal |
| `JdbcRepositoriesTest` | the real JDBC layer against real SQLite: migrations idempotent, upsert, nullable columns, CIDR ban matching, paging, and 8 concurrent audit writers with unique ids |
| `ResourceAuditTest` | every config key the loader reads exists in `config.yml`; every message key the code uses exists in `messages.yml`; no inert keys |
| `ConsoleBannerAndFilterTest` | the banner stays plain ASCII; credential commands are dropped from logs and ordinary lines are not |

## Jar size

The shaded jar is about **25 MB**:

| Component | Size | Why |
|---|---|---|
| SQLite JDBC | ~12 MB | Native libraries for every OS/architecture; this is what makes SQLite work out of the box everywhere |
| BouncyCastle (`bcprov`) | ~5.5 MB | Argon2. `bcpkix` would add ~1.5 MB more and is not needed. |
| Other | ~2.8 MB | Jackson (via GeoIP), Netty-free misc, resources |
| PostgreSQL driver | ~1.1 MB | |
| MariaDB driver | ~0.7 MB | |
| Angus Mail + Jakarta Mail | ~0.7 MB | Email recovery |
| HikariCP | ~0.15 MB | |
| GeoIP reader | ~0.12 MB | |
| LightLogin itself | ~0.3 MB | |

To slim it, use `mvn -Pslim clean package` (about **21 MB**): the profile excludes the PostgreSQL and
MariaDB drivers and the GeoIP reader. A server using the `slim` jar and a server database must place
the driver jar in `plugins/LightLogin/libs/` and reference it from the server's classpath, or use
the default build. SQLite and everything else keep working unchanged.

Stripping the unused SQLite natives would save roughly another 8 MB but would break portability, so
the build does not do it.

## Conventions

* The core must never import `org.bukkit.*`. That is the invariant that keeps the whole domain
  testable; if a change needs a Bukkit type in the core, the abstraction is wrong.
* Repository and mail calls block and are only called from `AsyncExecutor`.
* Mutable per-player state lives in `AuthGate` (backed by `ConcurrentHashMap`) or nowhere.
* A menu is identified by its `InventoryHolder`, never by a player-keyed map.
* Comments explain *why*, not *what*.

## Verifying an artifact

```bash
# own classes must be Java 25 (class-file major 69)
python3 - <<'PY'
import zipfile, collections
z = zipfile.ZipFile('lightlogin-paper/target/lightlogin-paper-3.0.0.jar')
own = [n for n in z.namelist() if n.endswith('.class') and n.startswith('dev/lightlogin/')
       and not n.startswith('dev/lightlogin/libs/')]
majors = collections.Counter(((z.read(n)[6]<<8)|z.read(n)[7]) for n in own)
print(len(own), "own classes, majors:", dict(majors))   # expect {69: N}
PY
```

## Known limitations

* The void login world is **experimental** and off by default.
* Country blocking needs a MaxMind GeoLite2 database and is inert without one.
* Reload applies messages, the GUI and the login flow; security, database, CAPTCHA and rate-limit
  settings need a restart (the plugin says so when you reload).
* A plugin cannot sandbox another plugin in the same JVM; see
  [00-security-analysis.md](00-security-analysis.md) §7.