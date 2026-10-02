# Migrating from the original plugin

This rewrite keeps the original's feature set but changes the package, the configuration layout and
the database schema. This page lists what changed and what an upgrade requires.

## Compatibility

| Original | This rewrite |
|---|---|
| Package `top.cmarco.lightlogin` | `dev.lightlogin.*` (core / persistence / paper) |
| Single Maven module, Java 17, Spigot API 1.21.1 | Three modules, Java 25, Paper API 26.1 |
| Plugin name `LightLogin` | `LightLogin` (unchanged) |
| Commands `login`, `register`, `verify`, `changepassword`, `unregister`, `unlogin`, `email`, `resetpassword`, `temppassword`, `login-data`, `lightlogin` | Same names and aliases |
| Config in `config.yml` + `config_<language>.yml` | `config.yml`, `messages.yml`, `gui.yml` |
| Argon2 via BouncyCastle `bcpkix` | Argon2id via BouncyCastle `bcprov` |

**There is no automatic in-place database migration.** The schema changed (see below), and the
original stored the salt in a separate column while this rewrite stores the full PHC string. A
password hashed by the original cannot be verified by this rewrite, because the original's
`Argon2Utilities.encryptArgon2(password)` overload discarded the salt it generated — the data needed
to verify it does not exist.

You have two options:

### Option A — fresh start (recommended)

Delete the old plugin and its database, and let players register again. Cleanest, and it removes any
hashes produced by the original's weaker configuration (4 iterations, a `65336` KiB memory value
that looks like a typo for `65536`, and a hard-coded 16-byte salt).

### Option B — keep the player list

If you must keep registrations, the honest path is to force a reset:

1. Import the old `uuid` and `username` values into `ll_accounts` with `password_hash` set to
   `NULL`.
2. Start the server. Each player is treated as unregistered and must run `/register` once.
3. Alternatively, email a reset to every player with a recovery address using
   `RecoveryService` (a small script against the new `ll_accounts` table).

Do not copy the old `password_hash` / `password_salt` columns into `password_hash`: the values are
not in PHC format and every login would fail.

## Schema mapping

| Original (single table) | This rewrite |
|---|---|
| `uuid`, `username` | `ll_accounts.uuid`, `.username`, `.username_lower` |
| `password_hash`, `password_salt` | `ll_accounts.password_hash` (single PHC column) |
| `email` | `ll_accounts.email` |
| `last_login`, `last_ipv4` | `ll_accounts.last_login`, `.last_ip` |
| — | `ll_accounts.status`, `.failed_attempts`, `.locked_until`, `.created_at`, `.registration_ip` |
| — | `ll_sessions`, `ll_ip_bans`, `ll_audit`, `ll_admin_accounts`, `ll_counters` |

## Configuration mapping

| Original key | This rewrite |
|---|---|
| `database.type/username/password/address/port/db-name` | `database.type/username/password/host/port/name` |
| `crash-shutdown` | removed — the pool never fails fast; a broken database degrades instead of killing the server |
| `void-world.enabled/mode` | `void-world.enabled` / `void-world.mode` (`NORMAL`/`THE_END`) plus `name`, `spawn-y`, `return-to-original` |
| `login-blindness.enabled` | `login.blindness` |
| `login.command-delay` | `login.command-delay-millis` |
| `login.kick-after-seconds` | `login.timeout-millis` |
| `login.session-expire` | `security.session-ttl-millis` |
| `login.allowed-commands` | `login.allowed-commands` (without the leading `/`) |
| `login.bruteforce-punishment` (a command list) | removed — punishment is now an automatic temporary IP ban, recorded in the audit trail |
| `safety.players-same-ip` | `safety.max-players-per-ip` |
| `safety.register-same-ip` | `safety.max-registrations-per-ip` |
| `safe-passwords.force-safe.*` | `password-policy.*` |
| `email.email-smtp` | `email.smtp-host` |
| `email.email-port` | `email.smtp-port` |
| `email.email-account` | `email.account` |
| `email.email-password` | `email.password` (encrypted after the first run) |
| `email.email-sender-name` | `email.sender-name` |
| `email.email-subject` | `email.subject` |
| `email.email-text-content` | `email.body` |
| `email.recovery-password-min-length` | `email.recovery-password-length` |
| `email.recovery-min-delay` (minutes) | `email.recovery-cooldown-millis` |
| `captcha.message/wrong-answer/correct-answer/...` | `messages.yml` under `captcha.*` |
| `captcha.max-attempts`, `captcha.punishments` | `captcha.max-attempts`, `captcha.punish-on-failure` |
| `temp-password.secret-key` | removed — `/temppassword` is console-only, so a shared secret in a config file is no longer needed |
| `sounds.*` | removed — sounds are fixed and sensible; a wrong sound is not worth a config key |
| `login-animation.*`, `title.*` | collapsed into `login.titles.enabled` and `login.action-bar.enabled` |
| `messages.*` | moved to `messages.yml` |
| `teleport.login-teleport/post-login-teleport` | `login.teleport.login-location` / `login.teleport.return-location` (`world,x,y,z`) |

Two keys were deliberately **removed** rather than mapped:

* `login.bruteforce-punishment` ran arbitrary console commands (`clear {PLAYER}`,
  `tempban …`). Executing operator-configured command strings from an authentication path is a
  privilege-escalation surface — anyone who can edit the config can already run commands, but so can
  anything that can write the file. The plugin now applies an automatic temporary IP ban with a
  configured duration, recorded in the audit trail.
* `temp-password.secret-key` was a plaintext shared secret in the config file. The command is
  console-only now, which is a stronger control and needs no secret.

## Behaviour changes

* **CAPTCHA** is no longer a plain maths question only. `captcha.mode: PROOF_OF_WORK` is available,
  and challenges now expire, have a bounded attempt count and a re-issue cooldown.
* **Sessions** are remembered server-side and bound to the connecting address, instead of the
  original's `session-expire` timer with no binding.
* **Passwords typed in chat** are cancelled and never broadcast (the original warned but still let
  the message through in some paths).
* **The login and register commands** are hidden from the client's command list until authenticated.
* **The void world** is disabled by default and marked experimental.
* **Reload** no longer claims to apply everything: security, database, CAPTCHA and rate-limit
  settings require a restart, and the plugin says so.

## Steps

1. Back up the old database and configuration.
2. Stop the server and remove the old plugin jar.
3. Install this jar and start the server once to generate the new files.
4. Port your settings using the mapping above.
5. Set `LIGHTLOGIN_PEPPER` **before** any player registers. Setting it later makes existing hashes
   unverifiable.
6. Choose Option A or B above for the player data.
7. Restart and verify with `/lightlogin stats`.