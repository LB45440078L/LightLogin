# API for other plugins

LightLogin fires Bukkit events when a player authenticates or registers, and exposes a small
read-only facade for asking questions about authentication state. Both live in
`dev.lightlogin.paper.api`.

## Declaring the dependency

Events are delivered to any registered listener, so reacting needs no dependency at all. The facade
does — declare LightLogin as a soft dependency so it is loaded first:

```yaml
# your plugin.yml
softdepend: [LightLogin]
```

Use `depend` instead if your plugin cannot function without LightLogin.

## Getting the API

```java
import dev.lightlogin.paper.api.LightLoginApi;

LightLoginApi api = LightLoginApi.get();          // null when LightLogin is not loaded
LightLoginApi api = LightLoginApi.require();      // throws IllegalStateException naming the problem
```

Or through Bukkit's services manager, which avoids holding a static reference:

```java
RegisteredServiceProvider<LightLoginApi> provider =
        Bukkit.getServicesManager().getRegistration(LightLoginApi.class);
if (provider != null) {
    boolean authenticated = provider.getProvider().isAuthenticated(player);
}
```

`install` and `uninstall` are called by LightLogin itself during enable and disable. Other plugins
should not call them.

## Events

All three fire on the server thread, synchronously. A listener that throws is caught and logged:
one misbehaving plugin cannot fail a player's login.

### `PlayerAuthenticatedEvent`

Fired after a player has successfully authenticated. The player is online, the authentication gate
has been released, and any login-world teleport has already been reversed — so this is the place for
"let me give them their kit now".

```java
@EventHandler
public void onAuthenticated(PlayerAuthenticatedEvent event) {
    Player player = event.getPlayer();
    if (event.getMethod() == AuthMethod.PASSWORD) {
        player.sendMessage(Component.text("Welcome back."));
    }
}
```

`getMethod()` returns an `AuthMethod`:

| Value | Meaning |
|---|---|
| `PASSWORD` | The player typed their password |
| `REGISTRATION` | The player registered, and registration logs them straight in |
| `SESSION` | A recent session from the same address skipped the password prompt |

**Not cancellable, deliberately.** By the time it fires the session is remembered, the login is
recorded and the player has been restored. A cancellable event would let one listener leave the
server in a state where the player is authenticated but the plugin believes they are not.

### `PlayerRegisteredEvent`

Fired after a player has successfully registered. The account row is already written, but the player
has **not** been let through the gate yet, so this is the right place for first-join housekeeping:
starter kits, group assignment, notifying staff.

Not cancellable: the password is hashed and the account persisted, so there is no state to roll back
to.

### `PlayerAuthFailedEvent`

Fired after a failed authentication attempt, with a stable reason code rather than a localised
sentence, so a listener can branch on it without parsing prose:

| `Reason` | Meaning |
|---|---|
| `WRONG_PASSWORD` | The password did not match |
| `NOT_REGISTERED` | No account exists for this player |
| `LOCKED` | The account is temporarily locked after too many failures |
| `RATE_LIMITED` | The attempt was refused by rate limiting |

`getAttemptsRemaining()` carries the remaining attempt budget where that is meaningful, and `-1`
where it is not.

Not cancellable: the attempt has already been counted against the player's rate limit and failure
budget, and a veto would silently disable lockout.

## Facade methods

| Method | Blocks? | Notes |
|---|---|---|
| `isAuthenticated(Player)` | no | In-memory; safe on the server thread |
| `isAuthenticated(UUID)` | no | In-memory; safe on the server thread |
| `isPending(UUID)` | no | Still waiting to authenticate |
| `pendingCount()` | no | How many players are waiting |
| `sessionExpiryMillis(UUID)` | no | `0` when there is no remembered session |
| `hasSession(UUID)` | no | Whether a remembered session is live |
| `isRegistered(UUID)` | **yes** | Reads storage. Call it from an asynchronous thread, never from the server thread — the same rule LightLogin holds itself to. |

## Example: a starter kit on first registration

```java
public final class StarterKit implements Listener {

    @EventHandler
    public void onRegistered(PlayerRegisteredEvent event) {
        // The account exists but the player has not been let through the gate yet.
        rewardLater(event.getPlayer());
    }

    @EventHandler
    public void onAuthenticated(PlayerAuthenticatedEvent event) {
        if (event.getMethod() == AuthMethod.REGISTRATION) {
            event.getPlayer().sendMessage(Component.text("Account created."));
        }
    }
}
```

Register the listener in your own `onEnable` as usual.
