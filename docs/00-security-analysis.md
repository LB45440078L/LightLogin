# Security analysis and design rationale

This document records the threat model and the algorithm choices behind the rewrite, so the
reasoning can be reviewed rather than taken on faith.

## 1. What is being defended

A Minecraft authentication plugin is unusual: it is the only component between an unauthenticated
attacker and a fully privileged game session, and it runs inside a JVM shared with arbitrary
third-party code.

| Asset | Consequence of compromise |
|---|---|
| Player passwords | Credential stuffing across other services (players reuse passwords) |
| The password database | Offline cracking, account takeover |
| SMTP credentials | Mailbox takeover, phishing from the server's address |
| The database credentials | Full read/write of player data |
| Admin panel session | Total control of the plugin and its data |
| The authentication decision itself | Impersonation of any player |

### Threat actors

1. **A remote attacker with no account** — floods joins, brute-forces logins, attempts account
   enumeration.
2. **A legitimate player** — guesses another player's password, abuses recovery, tries to bypass
   the gate.
3. **A malicious plugin on the same server** — reads memory, reflects into the auth internals,
   hooks events.
4. **An attacker with a database dump** — attempts offline cracking.
5. **An attacker with filesystem read access** — attempts to recover secrets from configuration.

## 2. Password hashing

**Choice: Argon2id.** It is the Password Hashing Competition winner and OWASP's first
recommendation. It is memory-hard, which is the property that actually defeats GPU and ASIC
cracking, and its hybrid data-dependent/data-independent addressing resists both side-channel and
time-memory-tradeoff attacks.

**Parameters.** OWASP's guidance is a minimum of 19 MiB with 2 iterations and 1 degree of
parallelism, with the note that memory should be raised first because memory costs decrease slower
than compute over time. This plugin ships a **balanced** default of 64 MiB / 3 iterations / 1 lane
and a **hardened** profile of 128 MiB / 4 iterations, and it *clamps any configuration below the
OWASP floor back up to it* rather than trusting the file. A 64 MiB profile costs roughly 250–400 ms
on modern hardware; that latency is absorbed off the main thread (see §6).

**Encoding.** The full PHC string is stored, e.g.

```
$argon2id$v=19$m=65536,t=3,p=1$c2FsdHNhbHRzYWx0$ZGVyaXZlZGtleWRlcml2ZWRrZXk
```

This is a direct fix for a real defect in the original plugin: its public `encryptArgon2(password)`
overload generated a random salt and then discarded it, returning only the digest. Any caller using
that overload produced an unverifiable hash. Carrying the parameters and salt in the string means a
stored hash is always self-contained and can be verified years later, and it lets parameters be
raised over time — a successful login transparently re-hashes with the current profile when
`needsRehash` reports the stored one is weaker.

**Pepper.** An optional server-side secret is mixed in as `HMAC-SHA-512(pepper, password)` before
Argon2. If the database is exfiltrated but the environment is not, the attacker cannot even begin
offline cracking: they lack the key. HMAC is used rather than concatenation to avoid
length-extension concerns and to keep the input length constant. The pepper is read from an
environment variable, never from the configuration file.

**Comparison.** All digest comparisons go through `MessageDigest.isEqual` (constant-time for equal
lengths). Comparing with `Arrays.equals` leaks the common-prefix length through timing.

**Account enumeration.** The login path is uniform: a missing account still performs a hash
verification against a fixed dummy value, and the same rate-limit token is consumed whether the
account exists or not. Recovery requests return the same outcome for an unknown account as for a
known one.

## 3. Secrets at rest

Values that must be *recoverable* (SMTP password, database password) are encrypted with
**AES-256-GCM** under a 256-bit master key stored in a separate file created with owner-only
permissions (`0600`). GCM is an AEAD construction: confidentiality **and** integrity, so a tampered
ciphertext fails to decrypt rather than yielding attacker-chosen plaintext. Each encryption uses a
fresh 96-bit nonce (the GCM-recommended size) prepended to the ciphertext.

This is defence in depth: a leaked `config.yml` alone yields ciphertext, and a leaked key file alone
yields nothing.

**What is not stored.** Session tokens are stored only as SHA-256 digests, so a database leak cannot
be replayed against the server. Passwords are never stored, logged, or written to the audit trail —
the audit record for a failed login records the *event*, never the attempted password. Plaintext
passwords exist only as `char[]` arrays that are wiped with `Arrays.fill` immediately after use; the
original plugin's `PlaintextPasswordManager`, which held plaintext passwords in a `ConcurrentHashMap`
keyed by UUID and never removed them, is gone.

## 4. Anti-bot and flood resistance

A maths CAPTCHA stops a human-level nuisance but not a script: a bot answers "7 + 5" as fast as a
human. The plugin therefore offers a **hashcash-style proof of work** as an alternative: the client
must find a nonce such that `sha256(seed + ':' + nonce)` has N leading zero bits. At 16 bits that is
~65 000 hashes per attempt — invisible to a human, but it turns a flood of one-packet attempts into a
flood of CPU-bound work. At 20+ bits it becomes a genuine denial-of-service cost for the attacker.

Layered with it:

* **Token buckets per IP** for connections, login attempts and commands. A bucket models a sustained
  rate with a bounded burst — exactly the shape of legitimate traffic (a player retrying a mistyped
  password) versus an attack (thousands per second).
* **Per-account lockout** after N failed attempts.
* **Bounded concurrency**: an Argon2id hash at 64 MiB allocates 64 MiB for its duration, so an
  unbounded flood would exhaust the heap. A semaphore turns "the server OOMs" into "the excess
  logins queue".
* **Automatic temporary bans** on sustained brute force or CAPTCHA failure, with the source recorded
  in the audit trail.
* **Per-IP limits** on simultaneous players and on registrations, which is what stops one address
  from registering a thousand accounts.
* **Country blocking** via GeoIP, which degrades to "never blocks" when no database is present,
  rather than failing closed on a misconfiguration.

## 5. Log and console hygiene

Cancelling a command event does **not** stop the server from logging the raw command line — that
happens in the packet handler before the event fires. Redaction at the logging layer is therefore
the only place a typed password can actually be kept out of the console and the log files.

The plugin installs a Log4j2 filter that returns `DENY` for any line matching a credential-bearing
command. Log4j filters cannot modify an event, but denying is exactly the required behaviour: the
line never appears anywhere. The pattern is deliberately tight so ordinary lines are not swallowed,
and the number of denied lines is exposed as a metric so a silently broken filter is detectable.

In addition, the plugin's own output paths run through a `SecretRedactor` that masks any registered
secret (the access token, the pepper) and any password-shaped command argument, so a line the plugin
itself logs cannot leak either.

## 6. Concurrency and the main thread

Every blocking operation — Argon2, JDBC, SMTP — runs on a **virtual thread** (stable since Java 21).
Virtual threads are cheap enough to create one per login, and blocking on a socket parks the virtual
thread rather than a carrier thread, so the server's tick loop is never touched by authentication
work. Results are delivered back on the main thread via the scheduler, where it is safe to teleport,
message and mutate inventory.

The `AsyncExecutor` is the only place this contract is expressed, so no repository method is
reachable from the main thread by accident.

## 7. The honest limits

**A plugin cannot sandbox another plugin.** All Bukkit plugins share one JVM, and reflection
bypasses access modifiers. Any claim of true isolation would be false. What LightLogin does instead
is make privileged operations *unreachable by accident* and *detectable by design*:

* Privileged internal APIs (the ones the admin panel uses) take an `AccessToken` that must match a
  token minted at boot from a CSPRNG. A malicious plugin that finds the method by reflection still
  cannot call it without reading the token out of the running JVM, and every rejected call
  increments a counter and can be audited.
* All mutable state is private and exposed only through narrow, explicit accessors; there is no
  public mutable field to poke.
* `IntegrityGuard` records at startup whether the JVM was launched with a `-javaagent` — the usual
  vector for bytecode-rewriting attacks against a login plugin — and prints a loud warning naming
  the agents. It can also fingerprint a class's bytecode for on-disk tamper checks.

**The only complete isolation is a separate JVM or an OS sandbox.** Run untrusted plugins on a
different server, and treat any `-javaagent` on an authentication server as a compromise until
proven otherwise.

**The void login world is experimental** and disabled by default. It has a real cost: a second world
keeps its own chunk cache, and a player moved there loses their original position unless
`return-to-original` is enabled.

**Country blocking is only as good as the GeoIP database**, which must be updated regularly; an
out-of-date database misclassifies addresses, and blocking a whole country also blocks VPNs that
exit there.

## 8. Sources

* OWASP Password Storage Cheat Sheet — Argon2id parameters (19 MiB / t=2 / p=1 minimum).
* RFC 9106 — Argon2 specification, and the PHC string format.
* RFC 6238 — TOTP (used for optional admin two-factor authentication).
* NIST SP 800-38D — GCM mode and nonce guidance.
* Java 25 (JEP 506) — Scoped Values finalised; virtual threads stable since Java 21.