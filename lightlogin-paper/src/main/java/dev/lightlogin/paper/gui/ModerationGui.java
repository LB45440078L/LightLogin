package dev.lightlogin.paper.gui;

import dev.lightlogin.core.crypto.ConstantTime;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.IpBan;
import dev.lightlogin.paper.bootstrap.PluginContext;
import dev.lightlogin.paper.messages.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The moderator menu: a paginated player list, a per-player action page and a confirmation step.
 *
 * <p>Every menu is identified by its {@link InventoryHolder}, never by a player-keyed map, so
 * opening one menu from another cannot lose the state of the new one (the classic bug where clicks
 * silently stop working). Clicks are cancelled before anything is decided, drags are cancelled
 * separately, and only slots inside the top inventory resolve to an action. Permissions are
 * re-checked on click, not only when the item was drawn.</p>
 *
 * <p>Actions are the ones the plugin can actually perform. Where a player is offline, the action
 * still works because it operates on stored data.</p>
 */
public final class ModerationGui implements Listener {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private enum Action {
        RESET_PASSWORD, BAN_LAST_IP, UNBAN_LAST_IP, UNREGISTER
    }

    private final PluginContext ctx;
    private YamlConfiguration layout;
    private int rows;
    private int pageSize;
    private String title;

    // ------------------------------------------------------------------ holders

    private final class PlayerListHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        PlayerListHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final class ActionsHolder implements InventoryHolder {
        private final Account account;
        private final int page;
        private Inventory inventory;

        ActionsHolder(Account account, int page) {
            this.account = account;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final class ConfirmHolder implements InventoryHolder {
        private final Action action;
        private final Account account;
        private final int page;
        private Inventory inventory;

        ConfirmHolder(Action action, Account account, int page) {
            this.action = action;
            this.account = account;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public ModerationGui(PluginContext ctx) {
        this.ctx = ctx;
        reload();
    }

    /** Re-reads gui.yml; safe to call on a plugin reload. */
    public void reload() {
        File file = new File(ctx.plugin().getDataFolder(), "gui.yml");
        if (!file.exists()) {
            ctx.plugin().saveResource("gui.yml", false);
        }
        this.layout = YamlConfiguration.loadConfiguration(file);
        this.rows = Math.clamp(layout.getInt("rows", 6), 3, 6);
        this.pageSize = Math.clamp(layout.getInt("page-size", 45), 9, rows * 9 - 9);
        this.title = layout.getString("title", "&8LightLogin &7» &fModeration");
    }

    // ------------------------------------------------------------------ opening

    /** Opens the paginated player list. */
    public void openPlayerList(Player viewer, int page) {
        int size = rows * 9;
        PlayerListHolder holder = new PlayerListHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, size, MessageService.colour(title));
        holder.attach(inventory);

        async(() -> ctx.accounts().page(page * pageSize, pageSize), accounts -> {
            if (!viewer.isOnline()) {
                return;
            }
            int slot = 0;
            for (Account account : accounts) {
                if (slot >= pageSize) {
                    break;
                }
                inventory.setItem(slot++, playerHead(account));
            }
            if (page > 0) {
                inventory.setItem(size - 9, item("previous"));
            }
            if (accounts.size() >= pageSize) {
                inventory.setItem(size - 3, item("next"));
            }
            inventory.setItem(size - 5, item("stats"));
            inventory.setItem(size - 1, item("close"));
            viewer.openInventory(inventory);
        });
    }

    private void openActions(Player viewer, Account account, int page) {
        ActionsHolder holder = new ActionsHolder(account, page);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                MessageService.colour("&8Actions &7» &f" + account.username()));
        holder.attach(inventory);
        inventory.setItem(10, item("action-reset-password"));
        inventory.setItem(12, item("action-ban-ip"));
        inventory.setItem(14, item("action-unban-ip"));
        inventory.setItem(16, item("action-unregister"));
        inventory.setItem(22, item("back"));
        viewer.openInventory(inventory);
    }

    private void openConfirm(Player viewer, Action action, Account account, int page) {
        ConfirmHolder holder = new ConfirmHolder(action, account, page);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                MessageService.colour("&8Confirm &7» &f" + account.username()));
        holder.attach(inventory);
        inventory.setItem(11, item("confirm"));
        inventory.setItem(15, item("cancel"));
        viewer.openInventory(inventory);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        Object holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof PlayerListHolder) && !(holder instanceof ActionsHolder)
                && !(holder instanceof ConfirmHolder)) {
            return;
        }
        // Cancel before deciding: nothing may leave or enter our menus.
        event.setCancelled(true);

        // Permission is re-checked on the click, not merely when the item was drawn.
        if (!viewer.hasPermission("lightlogin.moderator")) {
            viewer.closeInventory();
            ctx.messages().send(viewer, "gui.no-permission");
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            return; // a click in the viewer's own inventory
        }

        if (holder instanceof PlayerListHolder listHolder) {
            handleListClick(viewer, listHolder, rawSlot);
        } else if (holder instanceof ActionsHolder actionsHolder) {
            handleActionsClick(viewer, actionsHolder, rawSlot);
        } else if (holder instanceof ConfirmHolder confirmHolder) {
            handleConfirmClick(viewer, confirmHolder, rawSlot);
        }
    }

    private void handleListClick(Player viewer, PlayerListHolder holder, int rawSlot) {
        int size = rows * 9;
        if (rawSlot == size - 9) {
            openPlayerList(viewer, Math.max(0, holder.page - 1));
            return;
        }
        if (rawSlot == size - 3) {
            openPlayerList(viewer, holder.page + 1);
            return;
        }
        if (rawSlot == size - 1) {
            viewer.closeInventory();
            return;
        }
        if (rawSlot == size - 5) {
            // Statistics are shown in chat; a menu cannot render live values cheaply.
            viewer.closeInventory();
            sendStats(viewer);
            return;
        }
        if (rawSlot >= pageSize) {
            return;
        }
        ItemStack clicked = holder.getInventory().getItem(rawSlot);
        String uuid = uuidOf(clicked);
        if (uuid == null) {
            return;
        }
        async(() -> ctx.accounts().findByUuid(uuid), account ->
                account.ifPresentOrElse(
                        value -> openActions(viewer, value, holder.page),
                        () -> ctx.messages().send(viewer, "admin.player-not-found")));
    }

    private void handleActionsClick(Player viewer, ActionsHolder holder, int rawSlot) {
        switch (rawSlot) {
            case 10 -> openConfirm(viewer, Action.RESET_PASSWORD, holder.account, holder.page);
            case 12 -> openConfirm(viewer, Action.BAN_LAST_IP, holder.account, holder.page);
            case 14 -> openConfirm(viewer, Action.UNBAN_LAST_IP, holder.account, holder.page);
            case 16 -> openConfirm(viewer, Action.UNREGISTER, holder.account, holder.page);
            case 22 -> openPlayerList(viewer, holder.page);
            default -> {
                // Nothing to do; the click is already cancelled.
            }
        }
    }

    private void handleConfirmClick(Player viewer, ConfirmHolder holder, int rawSlot) {
        if (rawSlot == 15) {
            openActions(viewer, holder.account, holder.page);
            return;
        }
        if (rawSlot != 11) {
            return;
        }
        viewer.closeInventory();
        perform(viewer, holder.action, holder.account);
    }

    private void perform(Player viewer, Action action, Account account) {
        String actor = viewer.getName();
        switch (action) {
            case RESET_PASSWORD -> async(() -> {
                String temporary = ctx.tokens().temporaryPassword(12);
                char[] chars = temporary.toCharArray();
                try {
                    ctx.accounts().updatePassword(account.uuid(), ctx.hasher().hash(chars));
                } finally {
                    ConstantTime.wipe(chars);
                }
                ctx.sessionService().invalidateAll(account.uuid());
                ctx.auditService().record(actor,
                        dev.lightlogin.core.security.AuditAction.PASSWORD_RESET, account.uuid(),
                        "moderation menu", "");
                return temporary;
            }, temporary -> ctx.messages().send(viewer, "admin.reset",
                    Map.of("PLAYER", account.username(), "PASSWORD", temporary)));
            case BAN_LAST_IP -> {
                if (account.lastIp() == null) {
                    ctx.messages().send(viewer, "admin.player-not-found");
                    return;
                }
                asyncRun(() -> ctx.ipBanService().ban(IpBan.permanent(account.lastIp(),
                        "Banned from the moderation menu", actor, System.currentTimeMillis(),
                        IpBan.BanSource.MANUAL)));
                ctx.messages().send(viewer, "admin.banned", Map.of("TARGET", account.lastIp()));
            }
            case UNBAN_LAST_IP -> {
                if (account.lastIp() == null) {
                    ctx.messages().send(viewer, "admin.player-not-found");
                    return;
                }
                async(() -> ctx.ipBanService().unban(account.lastIp()), removed ->
                        ctx.messages().send(viewer, removed ? "admin.unbanned" : "admin.not-banned",
                                Map.of("TARGET", account.lastIp())));
            }
            case UNREGISTER -> asyncRun(() -> {
                ctx.authService().unregister(account.uuid(), account.username(), actor, "");
                ctx.sessionService().invalidateAll(account.uuid());
            });
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof PlayerListHolder || holder instanceof ActionsHolder
                || holder instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
    }

    private void sendStats(Player viewer) {
        async(() -> {
            var stats = ctx.adminService().stats(ctx.internalToken());
            return new long[]{stats.accounts(), stats.activeBans(), stats.sessions(), stats.auditEntries()};
        }, values -> ctx.messages().send(viewer, "admin.stats", Map.of(
                "ACCOUNTS", String.valueOf(values[0]),
                "BANS", String.valueOf(values[1]),
                "SESSIONS", String.valueOf(values[2]),
                "AUDIT", String.valueOf(values[3]),
                "WORKERS", String.valueOf(ctx.async().availablePermits()))));
    }

    // ------------------------------------------------------------------ helpers

    private ItemStack playerHead(Account account) {
        Material material = material("player-entry", Material.PLAYER_HEAD);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(render(itemString("player-entry.name", "&e{PLAYER}"), account));
            meta.setLore(renderLines("player-entry.lore", account));
            if (meta instanceof SkullMeta skull) {
                // Resolved by the stored UUID rather than by name: Bukkit's name lookup is
                // deprecated and can block on a profile fetch, and this account already knows its
                // own identifier.
                try {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(java.util.UUID.fromString(account.uuid())));
                } catch (IllegalArgumentException e) {
                    ctx.plugin().getLogger().warning("Account " + account.uuid()
                            + " does not hold a valid UUID; the head will show the default skin.");
                }
            }
            stack.setItemMeta(meta);
        }
        // The account is identified by a persistent-data key so a click never depends on the name.
        var key = new org.bukkit.NamespacedKey(ctx.plugin(), "account_uuid");
        ItemMeta finalMeta = stack.getItemMeta();
        if (finalMeta != null) {
            finalMeta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING,
                    account.uuid());
            stack.setItemMeta(finalMeta);
        }
        return stack;
    }

    private String uuidOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        var key = new org.bukkit.NamespacedKey(ctx.plugin(), "account_uuid");
        return item.getItemMeta().getPersistentDataContainer()
                .get(key, org.bukkit.persistence.PersistentDataType.STRING);
    }

    private ItemStack item(String key) {
        Material material = material(key, Material.STONE);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(render(layout.getString("items." + key + ".name", key), null));
            List<String> lore = layout.getStringList("items." + key + ".lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(MessageService::colour).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material material(String key, Material fallback) {
        String name = layout.getString("items." + key + ".material");
        if (name == null) {
            return fallback;
        }
        Material resolved = Material.matchMaterial(name);
        return resolved == null ? fallback : resolved;
    }

    private String itemString(String path, String fallback) {
        return layout.getString(path, fallback);
    }

    private String render(String template, Account account) {
        String text = template;
        if (account != null) {
            text = text.replace("{PLAYER}", account.username())
                    .replace("{UUID}", account.uuid())
                    .replace("{IP}", account.lastIp() == null ? "-" : account.lastIp())
                    .replace("{LAST_LOGIN}", account.lastLoginMillis() == 0 ? "never"
                            : TIMESTAMP.format(Instant.ofEpochMilli(account.lastLoginMillis())))
                    .replace("{REGISTERED}", account.isRegistered() ? "yes" : "no");
        }
        return MessageService.colour(text);
    }

    private List<String> renderLines(String path, Account account) {
        List<String> lines = layout.getStringList(path);
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(render(line, account));
        }
        return out;
    }

    private <T> void async(java.util.concurrent.Callable<T> work, java.util.function.Consumer<T> onMain) {
        ctx.async().submit(work).whenComplete((result, error) -> {
            if (error != null) {
                ctx.plugin().getLogger().warning("Menu task failed: " + error.getMessage());
                return;
            }
            Bukkit.getScheduler().runTask(ctx.plugin(), () -> onMain.accept(result));
        });
    }

    private void asyncRun(Runnable work) {
        ctx.async().run(work);
    }
}