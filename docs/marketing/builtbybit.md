# LightLogin

**A login and registration plugin that treats your players' passwords like they actually matter.**

![Version](https://img.shields.io/badge/version-3.0.0-5b8cff?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-26.1%2B-1f6feb?style=for-the-badge)
![Tests](https://img.shields.io/badge/tests-149%20passing-3ddc97?style=for-the-badge)
![Jar](https://img.shields.io/badge/jar-1.7%20MB-ffcf5b?style=for-the-badge)
![Hashing](https://img.shields.io/badge/passwords-Argon2id-8957e5?style=for-the-badge)
![Licence](https://img.shields.io/badge/licence-GPLv3-2f81f7?style=for-the-badge&logo=gnu&logoColor=white)

LightLogin handles the login and registration flow for Paper servers on Minecraft 26.1 and newer.
It is a complete rewrite of the original LightLogin rather than a patch release: the storage layer,
the cryptography, the command surface and the admin panel were all rebuilt around a single idea.

**If someone steals your database, they should get nothing they can use. And a login should never
cost you a tick.**

## Passwords that are useless to a thief

Every account is stored with Argon2id at 64 MiB of memory, three passes and a fresh 16 byte salt.
Hashes are written in standard PHC format, so the salt and the work factors travel inside the hash
itself. It is no longer possible for a stored hash to be separated from the salt that produced it.

On top of that you can set a server side pepper by environment variable. It is mixed into the hash
with HMAC SHA 512 and it never touches the database. Someone who walks away with a full copy of your
account table, and who somehow also knows your hashing parameters, still cannot test a single
password offline without that key.

If your work factors ever drop below the current OWASP guidance they are raised automatically rather
than rejected, and every account is quietly rehashed at the better setting the next time its owner
logs in.

## Nobody watches your players type

The login window is a private anvil input. Your player types into a client side text field, the
server receives it as an item name, and the password never becomes a command argument, a chat line
or a shell history entry.

A Log4j2 filter drops any log line containing a credential bearing command, so `/login`, `/register`
and `/changepassword` are absent from your console and your log files even when a player uses the
command form. Completion for the authentication commands returns nothing, so they are not advertised
to a client that has not logged in. Staff reading a log, or a support ticket pasted into Discord,
learns nothing.

The chat listener blocks any message shaped like a password, so a player cannot accidentally type it
into public chat while trying to log in.

## Built for the day you get attacked

Two CAPTCHA strategies ship in the box. A simple arithmetic question for casual bot noise, and a
mined proof of work whose per attempt cost makes automated flooding genuinely expensive. You choose
which one is required.

Around that sit the boring protections that matter at three in the morning: per IP login rate
limiting with a token bucket, per account lockout after too many failures, a join flood throttle that
rejects connections before a player entity is ever created, configurable per IP registration limits,
IP and CIDR bans with a source trail, and optional country blocking through a MaxMind GeoLite2
database.

## Zero load on the main thread

Every hash, every database query and every SMTP round trip runs on a virtual thread behind a bounded
semaphore. The main thread never touches a login.

The semaphore is not about thread cost, virtual threads are nearly free. It is about memory: an
Argon2id hash at 64 MiB allocates 64 MiB for its lifetime, so an unbounded flood of logins would
otherwise be a heap exhaustion waiting to happen. With the bound in place a burst becomes a short
queue instead of a crash.

One periodic task, once a second, handles everything time based. There is no per player scheduler
noise to profile later.

## An empty world for your empty world (experimental)

Players waiting to log in can be parked in a blank world generated in the End or in the overworld,
your choice. Either way it has no terrain, no structures, no mob spawning, a random tick speed of
zero and no force loaded spawn chunks. It is a place to stand and nothing else, and nobody who has
not authenticated is standing in your real map.

## Full control from a local page

LightLogin serves its own administration panel from the JDK's built in HTTP server, so there is no
extra dependency and no separate service to run. Dashboard, player list, individual player actions,
password resets, IP bans, audit log. It binds to loopback by default and refuses a public bind unless
you explicitly allow it. Sessions are token based with CSRF protection on every action, and two
factor authentication is available through any TOTP app.

In game, moderators get an inventory menu to browse players, reset passwords, ban addresses and
unregister accounts, with a confirmation step on anything destructive.

## Small on disk

The jar is 1.7 MB. It bundles only what it always needs: a trimmed BouncyCastle for Argon2, Angus
Mail for recovery emails, HikariCP and slf4j.

The JDBC drivers and the GeoIP reader are not bundled. They are found at startup on your server's own
classpath if it already provides them, or in `plugins/LightLogin/libs/`, or downloaded once and
verified against a pinned SHA 256. Every path checks the checksum, so a tampered or truncated file is
never loaded just because it has the right name. You control it all from `config.yml`, including
turning downloads off entirely for an air gapped server.

## Other plugins can hook in

Three events fire when it matters: `PlayerAuthenticatedEvent` (with the method used, password,
registration or resumed session), `PlayerRegisteredEvent`, and `PlayerAuthFailedEvent` with a stable
reason code. A read only facade answers questions about authentication state, published both as a
static accessor and through Bukkit's services manager.

## Requirements

* Paper 26.1 or newer
* Java 25 or newer
* Optional, MySQL, MariaDB or PostgreSQL, SQLite is bundled and is the default
* Optional, an SMTP account if you want email based password recovery
* Optional, a MaxMind GeoLite2 country database for nation blocking

## Install

1. Drop `lightlogin-paper-3.0.0.jar` into your `plugins/` folder.
2. Start the server once. LightLogin writes `config.yml`, `messages.yml`, `gui.yml` and a `lightlogin.key` master key.
3. Set the pepper environment variable. Strongly recommended, and worth doing on day one.
4. Review `config.yml` and restart.

The first start downloads the SQLite driver into `plugins/LightLogin/libs/` unless your server
already ships one.

## Testing

149 automated tests, and they do not all live in a happy path. The suite includes the real HTTP panel
driven over a socket, the real JDBC layer against a real SQLite file, an audit that every config key
is actually read and every message key actually exists, and six tests that load the packaged jar
itself and check that Argon2id still hashes and verifies using only what shipped inside it.

The build fails if the jar grows past 4 MB, if the startup wordmark stops being rectangular, or if a
relocated library would break at runtime. The packaging mistakes that are invisible to a normal unit
test are the ones this catches.

## Documentation

Nine documents ship in the repo, covering the threat model and algorithm choices with sources, the
architecture, the security model, every configuration key, the command and permission reference, the
admin panel, migration from the original plugin, development and testing, and the plugin API.

## Licence

GPLv3. Original plugin (C) 2024 CMarco. This rewrite keeps the same licence.
