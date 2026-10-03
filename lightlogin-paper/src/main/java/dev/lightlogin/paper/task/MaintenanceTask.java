package dev.lightlogin.paper.task;

import dev.lightlogin.core.config.LoginConfig;
import dev.lightlogin.paper.auth.AuthGate;
import dev.lightlogin.paper.bootstrap.PluginContext;
import dev.lightlogin.paper.messages.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;

/**
 * The single periodic task behind reminders, the login countdown and periodic clean-up.
 *
 * <p>One task at a fixed interval replaces several: fewer scheduler entries means less main-thread
 * work, and all the per-player work happens in a single pass over the (small) pending set rather
 * than in a task per player.</p>
 *
 * <p>The countdown is drawn with {@link Player#sendTitle(String, String, int, int, int)} rather than
 * an action bar. Bukkit's own API has no action bar call: the Spigot way is
 * {@code Player.Spigot#sendMessage(ChatMessageType, BaseComponent...)}, which needs Bungee's chat
 * library on the classpath, and Paper's component overload does not exist on Spigot at all. Using
 * the title and subtitle pair keeps this on the plain Bukkit surface, so the same jar runs on either
 * server and needs no extra dependency. Titles and the countdown remain separately switchable: with
 * only the countdown enabled the title is blank, which renders as a subtitle on its own.</p>
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
                player.kickPlayer(MessageService.colour(ctx.messages().raw("login.timed-out")));
                ctx.authGate().forget(pending.uuid());
                continue;
            }
            int secondsLeft = (int) (remaining / 1000);
            sendPrompt(player, pending, secondsLeft);
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

    /**
     * Shows the login prompt and the remaining time.
     *
     * <p>Refreshed every second; the stay time is slightly longer than the interval so the display
     * does not blink between ticks.</p>
     */
    private void sendPrompt(Player player, AuthGate.Pending pending, int secondsLeft) {
        LoginConfig login = ctx.config().login();
        if (!login.titlesEnabled() && !login.actionBarEnabled()) {
            return;
        }
        String title = "";
        if (login.titlesEnabled()) {
            List<String> lines = ctx.messages().render(
                    pending.registered() ? "login.prompt" : "register.prompt", Map.of());
            title = lines.isEmpty() ? "" : lines.get(0);
        }
        String countdown = "";
        if (login.actionBarEnabled()) {
            String template = ctx.messages().raw("login.action-bar");
            if (template.isBlank()) {
                template = "&7Time left: &c{TIME}s";
            }
            countdown = MessageService.colour(template.replace("{TIME}", String.valueOf(secondsLeft)));
        }
        player.sendTitle(title, countdown, 0, 30, 0);
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