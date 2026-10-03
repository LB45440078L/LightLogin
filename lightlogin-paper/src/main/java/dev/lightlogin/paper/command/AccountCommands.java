package dev.lightlogin.paper.command;

import dev.lightlogin.core.crypto.ConstantTime;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AuthResult;
import dev.lightlogin.core.service.RecoveryService;
import dev.lightlogin.paper.bootstrap.PluginContext;
import dev.lightlogin.paper.gui.PasswordInput;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** {@code /changepassword}, {@code /unlogin}, {@code /email}, {@code /resetpassword}, {@code /unregister}. */
public final class AccountCommands extends CommandSupport {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s.]{1,190}(\\.[^@\\s.]{2,63})+$");

    private final PasswordInput passwordInput;

    public AccountCommands(PluginContext ctx, PasswordInput passwordInput) {
        super(ctx);
        this.passwordInput = passwordInput;
    }

    // --------------------------------------------------------- /changepassword

    public void changePassword(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender).orElse(null);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            passwordInput.open(player, "&7Type your current password", (target, oldPassword) ->
                    passwordInput.open(target, "&7Type your new password", (confirmed, newPassword) ->
                            attemptChange(confirmed, oldPassword.toCharArray(), newPassword.toCharArray())));
            return;
        }
        attemptChange(player, args[0].toCharArray(), args[1].toCharArray());
    }

    private void attemptChange(Player player, char[] oldPassword, char[] newPassword) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        String ip = ipOf(player);
        // Both passwords are claimed on this thread before the work is submitted.
        dev.lightlogin.core.crypto.OwnedPassword ownedNew =
                dev.lightlogin.core.crypto.OwnedPassword.claim(newPassword);
        asyncAuth(oldPassword, ownedOld -> ownedNew.use(ownedNewValue -> {
            AuthResult result = ctx.authService().changePassword(uuid, name, ownedOld, ownedNewValue, ip);
            if (result instanceof AuthResult.Success) {
                // Database work stays on the worker thread, never the server thread.
                ctx.sessionService().invalidateAll(uuid);
            }
            return result;
        }), result -> handleChangeResult(player, result));
    }

    private void handleChangeResult(Player player, AuthResult result) {
        switch (result) {
            case AuthResult.Success ignored -> send(player, "password.change-success");
            case AuthResult.WrongPassword ignored -> send(player, "password.change-wrong-old");
            case AuthResult.NotRegistered ignored -> send(player, "login.not-registered");
            case AuthResult.PolicyRejected rejected -> {
                send(player, "password.unsafe");
                rejected.violations().forEach(v -> send(player, "password.unsafe-line", of("REASON", v)));
            }
            default -> send(player, "password.change-wrong-old");
        }
    }

    // ------------------------------------------------------------- /unlogin

    public void unlogin(CommandSender sender, String label, String[] args) {
        if (args.length >= 1) {
            if (!hasPermission(sender, "lightlogin.admin")) {
                send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                send(sender, "unlogin.not-online");
                return;
            }
            String targetUuid = target.getUniqueId().toString();
            String targetName = target.getName();
            // The session store is a database: invalidate it off the server thread.
            async(() -> {
                ctx.sessionService().invalidateAll(targetUuid);
                return null;
            }, ignored -> send(sender, "unlogin.player-success", of("PLAYER", targetName)));
            return;
        }
        Player player = requirePlayer(sender).orElse(null);
        if (player == null) {
            return;
        }
        String uuid = player.getUniqueId().toString();
        async(() -> {
            ctx.sessionService().invalidateAll(uuid);
            return null;
        }, ignored -> send(player, "unlogin.success"));
    }

    // ---------------------------------------------------------------- /email

    public void email(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender).orElse(null);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            send(player, "email.usage");
            return;
        }
        String address = args[0];
        if (!EMAIL.matcher(address).matches()) {
            send(player, "email.invalid");
            return;
        }
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        String ip = ipOf(player);
        asyncRun(() -> ctx.authService().setEmail(uuid, name, address, ip));
        send(player, "email.updated", of("EMAIL", address));
    }

    // -------------------------------------------------------- /resetpassword

    public void resetPassword(CommandSender sender, String label, String[] args) {
        String targetName;
        String actor;
        String ip;
        if (args.length >= 1) {
            if (!hasPermission(sender, "lightlogin.admin")) {
                send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
                return;
            }
            targetName = args[0];
            actor = sender instanceof Player p ? p.getName() : "console";
            ip = sender instanceof Player p ? ipOf(p) : "";
        } else {
            Player player = requirePlayer(sender).orElse(null);
            if (player == null) {
                return;
            }
            targetName = player.getName();
            actor = player.getName();
            ip = ipOf(player);
        }
        String finalActor = actor;
        String finalIp = ip;
        async(() -> ctx.recoveryService().requestReset(targetName, finalActor, finalIp), outcome -> {
            switch (outcome) {
                case DISABLED -> send(sender, "recovery.disabled");
                case COOLDOWN -> send(sender, "recovery.cooldown");
                case NO_EMAIL -> send(sender, "recovery.no-email");
                case FAILED -> send(sender, "recovery.failed");
                case DISPATCHED -> send(sender, "recovery.sent");
            }
        });
    }

    // ------------------------------------------------------------ /unregister

    public void unregister(CommandSender sender, String label, String[] args) {
        if (!hasPermission(sender, "lightlogin.admin")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
            return;
        }
        if (args.length < 1) {
            send(sender, "unregister.usage");
            return;
        }
        String targetName = args[0];
        String actor = sender instanceof Player p ? p.getName() : "console";
        String ip = sender instanceof Player p ? ipOf(p) : "";
        // All storage work happens on the worker thread; only messaging runs on the server thread.
        async(() -> {
            Optional<Account> found = ctx.accounts().findByUsername(targetName);
            found.ifPresent(target -> {
                ctx.authService().unregister(target.uuid(), target.username(), actor, ip);
                ctx.sessionService().invalidateAll(target.uuid());
            });
            return found;
        }, account -> {
            if (account.isEmpty()) {
                send(sender, "unregister.not-found");
                return;
            }
            Account target = account.get();
            send(sender, "unregister.success");
            Player online = Bukkit.getPlayer(java.util.UUID.fromString(target.uuid()));
            if (online != null) {
                send(online, "unregister.notify");
            }
        });
    }

    /** Tab completion: online player names for the commands that take one. */
    public List<String> playerNames(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    /** Exposed for the admin command's tab completion. */
    public static Optional<String> normalise(String input) {
        return input == null || input.isBlank() ? Optional.empty() : Optional.of(input.trim());
    }

    /** Kept for symmetry with other command classes. */
    public RecoveryService recovery() {
        return ctx.recoveryService();
    }
}