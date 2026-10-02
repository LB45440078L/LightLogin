# Commands and permissions

## Player commands

| Command | Aliases | Arguments | Notes |
|---|---|---|---|
| `/login` | `l`, `auth`, `authenticate` | `[password]` | With no argument, opens a **private input window** so the password never becomes a command argument. |
| `/register` | `reg` | `[password] [password]` | With no argument, opens the private input twice (enter, then confirm). Requires a solved CAPTCHA when enabled. |
| `/verify` | — | `<answer>` | Answers the CAPTCHA. |
| `/changepassword` | `changepsw` | `[old] [new]` | With no arguments, opens the private input twice. |
| `/unlogin` | `unlog`, `unl`, `ul` | `[player]` | Clears your session; the argument form needs `lightlogin.admin`. |
| `/email` | — | `<address>` | Sets your recovery address. |
| `/resetpassword` | `resetpsw` | `[player]` | Requests a recovery email; the argument form needs `lightlogin.admin`. |

## Administrative commands

| Command | Aliases | Arguments | Permission |
|---|---|---|---|
| `/lightlogin reload` | `ll` | | `lightlogin.admin` |
| `/lightlogin gui` | | | `lightlogin.moderator` |
| `/lightlogin stats` | | | `lightlogin.moderator` |
| `/lightlogin ban <ip\|cidr> [reason]` | | | `lightlogin.admin` |
| `/lightlogin unban <ip\|cidr>` | | | `lightlogin.admin` |
| `/lightlogin reset <player>` | | Prints a new temporary password | `lightlogin.admin` |
| `/lightlogin unregister <player>` | | | `lightlogin.admin` |
| `/unregister <player>` | `unreg` | | `lightlogin.admin` |
| `/login-data <player>` | `logindata`, `userdata` | | `lightlogin.moderator` |
| `/temppassword <player>` | `temppsw` | **Console only** | — |

`/temppassword` is console-only by design: a temporary password is a credential, so it is never
handed to a player session and never passes through chat. The result is printed to the console and
the player is notified that it happened.

`/login-data` deliberately never displays a password or a hash. It shows the UUID, registration
state, recovery email, last IP, last login and failed-attempt count.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `lightlogin.admin` | `op` | Everything: reload, bans, password resets, unregistration, session clearing. |
| `lightlogin.moderator` | `op` | The moderation GUI, statistics and `/login-data`. |

`lightlogin.moderator` can reset passwords and ban addresses through the GUI. Grant it only to
people who should be able to do that.

## The private password input

Typing a password after a command sends it through the command pipeline, where the server may log
it. The private input avoids that entirely: the password is entered in a client-side anvil text
field and arrives at the server as an item name, so it never becomes a command argument, never
enters command history and is never tab-completable.

Run `/login` or `/register` with **no arguments** to use it. It is the recommended flow.

The command-argument form still works for players who prefer it, and is protected by two layers:

1. A Log4j2 filter **drops** any log line containing a credential-bearing command, so it never
   reaches the console or the log file.
2. The authentication commands are hidden from the client's command list until the player is
   authenticated, so they are not advertised.

## Tab completion

Tab completion deliberately offers **no arguments** for `/login`, `/register`, `/verify` and
`/changepassword` — there is nothing safe to suggest, and offering suggestions would risk hinting at
a password. Player-name completion is available only on the administrative commands where the
sender already holds the permission.

## Messages

All player-facing text lives in `messages.yml`. Colour codes use `&`. Placeholders are written
`{LIKE_THIS}`. A multi-line message is a YAML list; the prefix is applied to the first line only.

Every key referenced by the code is verified to exist by a test, so a message can never print a
literal `{REASON}` because someone renamed a key.