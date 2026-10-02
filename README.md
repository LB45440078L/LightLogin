# LightLogin

**A login and registration plugin that treats your players' passwords like they actually matter.**

![Version](https://img.shields.io/badge/version-3.0.0-5b8cff?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-26.1%2B-1f6feb?style=for-the-badge)
![Tests](https://img.shields.io/badge/tests-149%20passing-3ddc97?style=for-the-badge)
![Jar](https://img.shields.io/badge/jar-1.7%20MB-ffcf5b?style=for-the-badge)
![Hashing](https://img.shields.io/badge/passwords-Argon2id-8957e5?style=for-the-badge)
![Modules](https://img.shields.io/badge/maven-3%20modules-c71a36?style=for-the-badge&logo=apachemaven&logoColor=white)
![Licence](https://img.shields.io/badge/licence-GPLv3-2f81f7?style=for-the-badge&logo=gnu&logoColor=white)

![Last commit](https://img.shields.io/github/last-commit/LB45440078L/LightLogin?style=flat-square)
![Stars](https://img.shields.io/github/stars/LB45440078L/LightLogin?style=flat-square)
![Issues](https://img.shields.io/github/issues/LB45440078L/LightLogin?style=flat-square)

LightLogin handles the login and registration flow for Paper servers on Minecraft 26.1 and newer.

This is a complete rewrite of the original LightLogin rather than a patch release. The storage layer,
the cryptography, the command surface and the admin panel were all rebuilt around a single idea:

> If someone steals your database, they should get nothing they can use. And a login should never
> cost you a tick.

## Passwords that are useless to a thief

Every account is stored with Argon2id at 64 MiB of memory, three passes and a fresh 16 byte salt.
Hashes are written in standard PHC format, so the salt and the work factors travel inside the hash
itself. A stored hash can no longer be separated from the salt that produced it, which is precisely
the bug class the original plugin had.

You can also set a server side pepper by environment variable. It is mixed into the hash with HMAC
SHA 512 and never touches the database. Someone who walks away with a full copy of your account
table, and who somehow also knows your hashing parameters, still cannot test one password offline
without that key.

Work factors below the current OWASP guidance are raised automatically rather than rejected, and each
account is rehashed at the better setting the next time its owner logs in.

## Nobody watches your players type

The login window is a private anvil input, so a password never becomes a command argument, a chat
line or a shell history entry. A Log4j2 filter **drops** any log line containing a credential bearing
command, so `/login`, `/register` and `/changepassword` are absent from your console and your log
files even when a player uses the command form. Completion for the authentication commands returns
nothing, so they are not advertised to a client that has not logged in. The chat listener blocks any
message shaped like a password.

Staff reading a log, or a support ticket pasted into Discord, learns nothing.

## Zero load on the main thread

Every hash, every database query and every SMTP round trip runs on a virtual thread behind a bounded
semaphore. The main thread never touches a login.

The bound is not about thread cost, since virtual threads are nearly free. It is about memory: an
Argon2id hash at 64 MiB allocates 64 MiB for its lifetime, so an unbounded flood of logins would
otherwise be a heap exhaustion waiting to happen. With it in place a burst becomes a short queue
instead of a crash. One task, once a second, handles everything time based.

## Built for the day you get attacked

Two CAPTCHA strategies ship in the box: an arithmetic question for casual bot noise, and a mined
proof of work whose per attempt cost makes automated flooding genuinely expensive. Around them sit
the protections that matter at three in the morning:

* Per IP login rate limiting with a token bucket
* Per account lockout after too many failures
* A join flood throttle that rejects connections before a player entity is ever created
* Configurable per IP registration limits
* IP and CIDR bans with a source trail
* Optional country blocking through a MaxMind GeoLite2 database

## An empty world to wait in

Players who have not logged in can be parked in a blank world generated in the End or in the
overworld, your choice from `config.yml`. Either way it has no terrain, no structures, no mob
spawning, a random tick speed of zero and no force loaded spawn chunks. It is a place to stand and
nothing else, and nobody unauthenticated is standing in your real map. Experimental, off by default.

## Full control from a local page

LightLogin serves its own administration panel from the JDK's built in HTTP server, so there is no
extra dependency and no separate service to run. Dashboard, player list, per player actions, password
resets, IP bans, audit log, with a square head icon for each account drawn locally rather than
fetched from a skin service, since asking a third party for every avatar would leak who you are
investigating.

It binds to loopback by default and refuses a public bind unless you explicitly allow it. Sessions
are token based with CSRF protection on every action, and two factor authentication is available
through any TOTP app.

In game, moderators get an inventory menu to browse players, reset passwords, ban addresses and
unregister accounts, with a confirmation step on anything destructive.

## Small on disk

The jar is 1.7 MB. It bundles only what it always needs: a trimmed BouncyCastle for Argon2, Angus
Mail for recovery emails, HikariCP and slf4j.

The JDBC drivers and the GeoIP reader are not bundled. They are found at startup on your server's own
classpath if it already provides them, or in `plugins/LightLogin/libs/`, or downloaded once and
verified against a pinned SHA 256. Every path checks the checksum, so a tampered or truncated file is
never loaded just because it has the right name. All of it is configurable, including turning
downloads off entirely for a server with no outbound network.

## Other plugins can hook in

Three events fire when it matters: `PlayerAuthenticatedEvent` (carrying the method used, whether
password, registration or a resumed session), `PlayerRegisteredEvent`, and `PlayerAuthFailedEvent`
with a stable reason code. A read only facade answers questions about authentication state and is
published both as a static accessor and through Bukkit's services manager. See
[docs/08-api.md](docs/08-api.md).

## Requirements

* Paper 26.1 or newer
* Java 25 or newer at runtime
* Optional: MySQL, MariaDB or PostgreSQL. SQLite is bundled and is the default
* Optional: an SMTP account for email based password recovery
* Optional: a MaxMind GeoLite2 country database for nation blocking

## Install

1. Drop `lightlogin-paper-3.0.0.jar` into your `plugins/` folder.
2. Start the server once. LightLogin writes `config.yml`, `messages.yml`, `gui.yml` and a
   `lightlogin.key` master key into `plugins/LightLogin/`.
3. Set the pepper environment variable (strongly recommended, and worth doing on day one):

   ```bash
   export LIGHTLOGIN_PEPPER="$(head -c 48 /dev/urandom | base64)"
   ```

4. Review `config.yml` and restart.

The first start downloads the SQLite driver into `plugins/LightLogin/libs/` unless your server
already ships one. The administration panel is disabled until you enable it in `config.yml`; on the
first start with it enabled, LightLogin creates an administrator and prints the generated password to
your console once.

## Build

Builds run on JDK 27 with `--release 25`, so the artifact targets Java 25 bytecode.

```bash
mvn clean verify                 # full build: 149 tests, including the shaded jar integration tests
mvn clean package                # build plus unit tests, stopping before the jar integration tests
mvn -pl lightlogin-core test     # one module
```

Output: `lightlogin-paper/target/lightlogin-paper-3.0.0.jar`

Use `verify` rather than `package` for the complete suite, because the integration tests inspect the
shaded jar and therefore run in the phase after packaging. The jar size, the bundled library set and
the startup wordmark are all asserted there.

## Testing

149 automated tests, and they do not all live in a happy path. The suite covers the real HTTP panel
driven over a socket, the real JDBC layer against a real SQLite file, an audit that every config key
is actually read and every message key actually exists, and six tests that load the packaged jar
itself and confirm Argon2id still hashes and verifies using only what shipped inside it.

The build fails if the jar grows past 4 MB, if the startup wordmark stops being rectangular, or if a
relocated library would break at runtime. Those packaging mistakes are invisible to an ordinary unit
test, which is exactly why they are checked here.

## Documentation

* [Security analysis and design rationale](docs/00-security-analysis.md), the threat model and the algorithms chosen, with sources
* [Architecture](docs/01-architecture.md), modules, ports and adapters, concurrency model
* [Security model](docs/02-security.md), what is protected, how, and what is not possible
* [Configuration reference](docs/03-configuration.md), every key
* [Commands and permissions](docs/04-commands-and-permissions.md)
* [Administration panel](docs/05-admin-panel.md)
* [Migrating from the original plugin](docs/06-migration-from-original.md)
* [Development and testing](docs/07-development.md)
* [API for other plugins](docs/08-api.md), login and registration events plus the read only facade

## Licence

GPLv3. Original plugin (C) 2024 CMarco. This rewrite keeps the same licence.
