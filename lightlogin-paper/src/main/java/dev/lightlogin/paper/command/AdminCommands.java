package dev.lightlogin.paper.command;

import dev.lightlogin.core.crypto.ConstantTime;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.core.security.AccessToken;
import dev.lightlogin.core.service.AuthService;
import dev.lightlogin.paper.bootstrap.PluginContext;
import dev.lightlogin.paper.gui.ModerationGui;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /lightlogin}, {@code /temppassword} and {@code /login-data}. */
public final class AdminCommands extends CommandSupport {

    private final ModerationGui gui;
    private final Runnable reloadAction;

    public AdminCommands(PluginContext ctx, ModerationGui gui, Runnable reloadAction) {
        super(ctx);
        this.gui = gui;
        this.reloadAction = reloadAction;
    }

    // ----------------------------------------------------------- /lightlogin

    public void admin(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "admin.usage");
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "gui" -> openGui(sender);
            case "stats" -> stats(sender);
            case "ban" -> ban(sender, args);
            case "unban" -> unban(sender, args);
            case "reset" -> reset(sender, args);
            case "unregister" -> unregister(sender, args);
            case "info" -> info(sender);
            default -> send(sender, "admin.usage");
        }
    }

    private void reload(CommandSender sender) {
        if (!hasPermission(sender, "lightlogin.admin")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
            return;
        }
        reloadAction.run();
        send(sender, "admin.reloaded");
    }

    private void openGui(CommandSender sender) {
        Player player = requirePlayer(sender).orElse(null);
        if (player == null) {
            return;
        }
        if (!hasPermission(player, "lightlogin.moderator")) {
            send(player, "gui.no-permission");
            return;
        }
        gui.openPlayerList(player, 0);
    }

    private void stats(CommandSender sender) {
        if (!hasPermission(sender, "lightlogin.moderator")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.moderator"));
            return;
        }
        AccessToken token = ctx.internalToken();
        async(() -> {
            long accounts = ctx.adminService().stats(token).accounts();
            long bans = ctx.adminService().stats(token).activeBans();
            long sessions = ctx.adminService().stats(token).sessions();
            long audit = ctx.adminService().stats(token).auditEntries();
            return new long[]{accounts, bans, sessions, audit};
        }, values -> send(sender, "admin.stats", java.util.Map.of(
                "ACCOUNTS", String.valueOf(values[0]),
                "BANS", String.valueOf(values[1]),
                "SESSIONS", String.valueOf(values[2]),
                "AUDIT", String.valueOf(values[3]),
                "WORKERS", String.valueOf(ctx.async().availablePermits()))));
    }

    private void ban(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "lightlogin.admin")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
            return;
        }
        if (args.length < 2) {
            send(sender, "admin.usage");
            return;
        }
        String target = args[1];
        String reason = args.length > 2 ? String.join(" ", List.of(args).subList(2, args.length)) : "";
        String actor = sender instanceof Player p ? p.getName() : "console";
        long now = System.currentTimeMillis();
        asyncRun(() -> ctx.ipBanService().ban(IpBan.permanent(target, reason, actor, now,
                IpBan.BanSource.MANUAL)));
        send(sender, "admin.banned", of("TARGET", target));
    }

    private void unban(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "lightlogin.admin")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
            return;
        }
        if (args.length < 2) {
            send(sender, "admin.usage");
            return;
        }
        String target = args[1];
        async(() -> ctx.ipBanService().unban(target), removed ->
                send(sender, removed ? "admin.unbanned" : "admin.not-banned", of("TARGET", target)));
    }

    private void reset(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "lightlogin.admin")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
            return;
        }
        if (args.length < 2) {
            send(sender, "admin.usage");
            return;
        }
        String name = args[1];
        async(() -> {
            var found = ctx.accounts().findByUsername(name);
            if (found.isEmpty()) {
                return "";
            }
            Account account = found.get();
            String temporary = ctx.tokens().temporaryPassword(12);
            char[] chars = temporary.toCharArray();
            try {
                ctx.accounts().updatePassword(account.uuid(), ctx.hasher().hash(chars));
            } finally {
                ConstantTime.wipe(chars);
            }
            ctx.sessionService().invalidateAll(account.uuid());
            ctx.auditService().record(sender instanceof Player p ? p.getName() : "console",
                    dev.lightlogin.core.security.AuditAction.PASSWORD_RESET, account.uuid(),
                    "admin reset", "");
            return temporary;
        }, temporary -> {
            if (temporary.isEmpty()) {
                send(sender, "admin.player-not-found");
            } else {
                send(sender, "admin.reset", of("PLAYER", name, "PASSWORD", temporary));
            }
        });
    }

    private void unregister(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin.usage");
            return;
        }
        unregister(sender, args[1]);
    }

    private void unregister(CommandSender sender, String name) {
        if (!hasPermission(sender, "lightlogin.admin")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.admin"));
            return;
        }
        String actor = sender instanceof Player p ? p.getName() : "console";
        async(() -> ctx.accounts().findByUsername(name), account -> {
            if (account.isEmpty()) {
                send(sender, "admin.player-not-found");
                return;
            }
            ctx.authService().unregister(account.get().uuid(), account.get().username(), actor, "");
            ctx.sessionService().invalidateAll(account.get().uuid());
            send(sender, "unregister.success");
        });
    }

    private void info(CommandSender sender) {
        send(sender, "admin.usage");
    }

    // -------------------------------------------------------- /temppassword

    public void tempPassword(CommandSender sender, String label, String[] args) {
        // Console-only: a temporary password is a credential, so it is never handed to a player
        // session and never passes through chat.
        if (sender instanceof Player) {
            send(sender, "player-only");
            return;
        }
        if (args.length < 1) {
            send(sender, "temppassword.usage");
            return;
        }
        String name = args[0];
        async(() -> {
            var found = ctx.accounts().findByUsername(name);
            if (found.isEmpty()) {
                return "";
            }
            String temporary = ctx.tokens().temporaryPassword(12);
            char[] chars = temporary.toCharArray();
            try {
                ctx.accounts().updatePassword(found.get().uuid(), ctx.hasher().hash(chars));
            } finally {
                ConstantTime.wipe(chars);
            }
            ctx.sessionService().invalidateAll(found.get().uuid());
            ctx.auditService().record("console",
                    dev.lightlogin.core.security.AuditAction.PASSWORD_RESET, found.get().uuid(),
                    "temporary password issued", "");
            return temporary;
        }, temporary -> {
            if (temporary.isEmpty()) {
                send(sender, "admin.player-not-found");
            } else {
                send(sender, "admin.reset", of("PLAYER", name, "PASSWORD", temporary));
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    send(online, "temppassword.player-message");
                }
            }
        });
    }

    // ---------------------------------------------------------- /login-data

    public void loginData(CommandSender sender, String label, String[] args) {
        if (!hasPermission(sender, "lightlogin.moderator")) {
            send(sender, "no-permission", of("PERMISSION", "lightlogin.moderator"));
            return;
        }
        if (args.length < 1) {
            send(sender, "admin.usage");
            return;
        }
        String name = args[0];
        async(() -> ctx.accounts().findByUsername(name), account -> {
            if (account.isEmpty()) {
                send(sender, "admin.player-not-found");
                return;
            }
            Account value = account.get();
            // Passwords and hashes are never displayed.
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(ctx.messages().prefix() + "&eLogin data for &f"
                            + value.username()));
            send(sender, "login-data-line", of("LABEL", "UUID", "VALUE", value.uuid()));
            send(sender, "login-data-line", of("LABEL", "Registered", "VALUE", value.isRegistered() ? "yes" : "no"));
            send(sender, "login-data-line", of("LABEL", "Email", "VALUE",
                    value.email() == null ? "-" : value.email()));
            send(sender, "login-data-line", of("LABEL", "Last IP", "VALUE",
                    value.lastIp() == null ? "-" : value.lastIp()));
            send(sender, "login-data-line", of("LABEL", "Last login", "VALUE",
                    value.lastLoginMillis() == 0 ? "never" : String.valueOf(value.lastLoginMillis())));
            send(sender, "login-data-line", of("LABEL", "Failed attempts", "VALUE",
                    String.valueOf(value.failedAttempts())));
        });
    }

    /** Tab completion for {@code /lightlogin}. */
    public List<String> adminCompletion(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "gui", "stats", "ban", "unban", "reset", "unregister", "info");
        }
        if (args.length == 2 && List.of("ban", "unban", "reset", "unregister").contains(
                args[0].toLowerCase(java.util.Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names;
        }
        return List.of();
    }

    /** The auth service, exposed for symmetry with the other command classes. */
    public AuthService auth() {
        return ctx.authService();
    }
}