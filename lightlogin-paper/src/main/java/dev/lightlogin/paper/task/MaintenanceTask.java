package dev.lightlogin.paper.task;

import dev.lightlogin.core.config.LoginConfig;
import dev.lightlogin.paper.auth.AuthGate;
import dev.lightlogin.paper.bootstrap.PluginContext;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * The single periodic task behind reminders, the login countdown and periodic clean-up.
 *
 * <p>One task at a fixed interval replaces several: fewer scheduler entries means less main-thread
 * work, and all the per-player work happens in a single pass over the (small) pending set rather
 * than in a task per player.</p>
 */
public final class MaintenanceTask {

    private final PluginContext ctx;
    private BukkitTask task;
    private int tickCounter;

    public MaintenanceTask(PluginContext ctx) {
        this.ctx = ctx;
    }

    /** Starts the task. The interval is one second. */
    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::tick, 20L, 20L);
    }

    /** Stops the task. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCounter++;
        LoginConfig login = ctx.config().login();
        long now = System.currentTimeMillis();

        for (AuthGate.Pending pending : ctx.authGate().all()) {
            Player player = Bukkit.getPlayer(pending.uuid());
            if (player == null || !player.isOnline()) {
                continue;
            }
            long remaining = login.timeoutMillis() - (now - pending.joinedAt());
            if (remaining <= 0) {
                player.kick(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(ctx.messages().raw("login.timed-out")));
                ctx.authGate().forget(pending.uuid());
                continue;
            }
            int secondsLeft = (int) (remaining / 1000);
            if (login.titlesEnabled()) {
                sendTitle(player, pending);
            }
            if (login.actionBarEnabled()) {
                sendActionBar(player, secondsLeft);
            }
            if (tickCounter % Math.max(1, login.reminderSeconds()) == 0) {
                ctx.messages().send(player, pending.registered() ? "login.prompt" : "register.prompt");
                player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.4f, 1.6f);
            }
        }

        // Housekeeping once a minute: expire sessions, bans, challenges and old audit rows.
        if (tickCounter % 60 == 0) {
            housekeeping(now);
        }
        if (tickCounter % 300 == 0) {
            ctx.connectionLimiter().sweep(System.nanoTime());
            ctx.loginLimiter().sweep(System.nanoTime());
            ctx.commandLimiter().sweep(System.nanoTime());
            ctx.captcha().sweep(now);
        }
    }

    private void sendTitle(Player player, AuthGate.Pending pending) {
        String key = pending.registered() ? "login.prompt" : "register.prompt";
        java.util.List<net.kyori.adventure.text.Component> lines = ctx.messages().render(key, java.util.Map.of());
        if (lines.isEmpty()) {
            return;
        }
        player.showTitle(net.kyori.adventure.title.Title.title(
                lines.get(0), net.kyori.adventure.text.Component.empty(),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(250),
                        java.time.Duration.ofSeconds(2),
                        java.time.Duration.ofMillis(250))));
    }

    private void sendActionBar(Player player, int secondsLeft) {
        String text = ctx.messages().raw("login.action-bar");
        if (text.isBlank()) {
            text = "&7Time left: &c{TIME}s";
        }
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text.replace("{TIME}", String.valueOf(secondsLeft))));
    }

    private void housekeeping(long now) {
        int retentionDays = ctx.config().security().auditRetentionDays();
        long cutoff = retentionDays > 0 ? now - retentionDays * 24L * 60 * 60 * 1000 : 0L;
        // This runs on the server thread (a scheduler task), so every database call has to be
        // pushed onto the async executor. Doing it inline froze the tick loop whenever the
        // database was slow or locked.
        ctx.async().run(() -> {
            try {
                ctx.sessionService().purgeExpired();
                ctx.ipBanService().purgeExpired();
                if (cutoff > 0) {
                    ctx.auditRepository().purgeOlderThan(cutoff);
                }
            } catch (RuntimeException e) {
                ctx.plugin().getLogger().warning("Housekeeping failed: " + e.getMessage());
            }
        });
    }

    /** Whether the task is running. */
    public boolean isRunning() {
        return task != null;
    }
}