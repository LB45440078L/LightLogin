# Development and testing

## Toolchain

* **Build JDK 27** (the compiler runs on it) with `maven.compiler.release = 25`, so the artifact
  targets Java 25 bytecode — the minimum for the Paper 26.1 line.
* **Maven 3.9+**
* The build was verified with `maven-compiler-plugin` 3.16.0, `maven-surefire-plugin` 3.6.0 and
  `maven-shade-plugin` 3.6.2.

```bash
mvn clean verify                      # build + 125 unit tests + 5 shaded-jar integration tests
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

130 tests across the three modules: 125 unit tests plus 5 integration tests that run against the
shaded jar itself. Highlights:

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
| `ShadedJarIT` | loads the **built jar** in an isolated classloader and opens a real SQLite database; asserts no multi-release library was relocated, the driver sits at its own coordinates alongside its native libraries, every `META-INF/services` entry names a class that exists, and no bundled library leaked into the shared namespace |

## Shading rules

One rule matters more than any other here, and breaking it produces a plugin that builds cleanly and
then dies on the server:

> **Never relocate a library that ships `META-INF/versions/**` (multi-release) content.**

Shade rewrites the class files it copies and the resources it merges, but it does **not** rewrite the
`META-INF/versions/N/...` copies. A relocated multi-release library therefore ends up split in two: the
classes your code links against are at the relocated package, while the version-specific half stays at
the original one. On first use the driver or library cannot find the class that its versioned copy was
meant to supply.

This is not hypothetical. `org.sqlite` was originally relocated, and the plugin failed at startup with:

```
java.lang.UnsatisfiedLinkError: 'void dev.lightlogin.libs.org.sqlite.core.NativeDB._open_utf8(byte[], int)'
```

because the driver could no longer find its own native library. The multi-release libraries are
therefore left at their own coordinates, and `ShadedJarIT` fails the build if anyone relocates one
again.

| Library | Multi-release content | Relocated? |
|---|---|---|
| `org.bouncycastle` | versions 11/15/17/25 | no |
| `com.fasterxml.jackson` | versions 11/17/21 | no |
| `org.mariadb` | versions 11/15 | no |
| `org.postgresql` | versions 11 | no |
| `org.sqlite` | versions 9 | no |
| `com.zaxxer.hikari` | — | yes |
| `org.eclipse.angus`, `jakarta.mail`, `jakarta.activation` | — | yes |
| `com.maxmind` | — | yes |
| `org.slf4j` | — | yes |

Leaving the first five un-relocated is safe on Paper, which gives every plugin its own child-first
classloader, so they cannot clash with another plugin's copy.

Two supporting details:

* **`ServicesResourceTransformer` is required.** Without it shade copies `META-INF/services/*` files
  by overwriting, so only one provider survives — `META-INF/services/java.sql.Driver` listed one of
  the three drivers before this was added. With it, the files are merged and their contents rewritten
  so they agree with the relocations.
* **`slf4j-nop` is bundled.** HikariCP needs slf4j at runtime and slf4j 2.x prints a warning when no
  provider is found. Bundling the NOP provider (relocated along with `org.slf4j`) keeps the plugin
  self-contained, silent and clear of the server's own slf4j.

## Startup database probe

`PersistenceBootstrap` loads the driver and opens one real connection *before* creating the pool,
and connects through the `Driver` instance rather than `DriverManager` (inside a plugin classloader
the two do not always agree about which drivers are visible).

The reason is diagnostic. The pool is configured with `initializationFailTimeout(-1)` so a briefly
unreachable database does not block startup — but that also means a *permanently* broken database
would only surface ten seconds later as an opaque `Connection is not available, request timed out`.
The probe turns that into an immediate message naming the real cause, e.g.:

```
The SQLITE driver could not load a required class or its native library:
  UnsatisfiedLinkError: 'void org.sqlite.core.NativeDB._open_utf8(byte[], int)'
  The bundled SQLite driver loads a native library from its own package path, so it must not be
  relocated when shading; check the build's relocation configuration.
```

## Jar size

The shaded jar is about **24 MB**:

| Component | Size | Why |
|---|---|---|
| SQLite JDBC | ~12 MB | Native libraries for every OS/architecture; this is what makes SQLite work out of the box everywhere |
| BouncyCastle (`bcprov`) | ~5.5 MB | Argon2. `bcpkix` would add ~1.5 MB more and is not needed. |
| Jackson (via GeoIP) | ~2.5 MB | |
| PostgreSQL driver | ~1.1 MB | |
| MariaDB driver | ~0.7 MB | |
| Angus Mail + Jakarta Mail | ~0.7 MB | Email recovery |
| HikariCP, GeoIP reader, slf4j | ~0.4 MB | |
| LightLogin itself | ~0.3 MB | |

To slim it, use `mvn -Pslim clean package` (about **20 MB**): the profile excludes the PostgreSQL and
MariaDB drivers and the GeoIP reader (Jackson with them). A server using the `slim` jar and a server
database must place the driver jar where the server can load it; SQLite and everything else keep
working unchanged. The `slim` jar is exercised by the same SQLite integration test.

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