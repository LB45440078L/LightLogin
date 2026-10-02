package dev.lightlogin.paper.gui;

import dev.lightlogin.paper.bootstrap.PluginContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * A private password input built on an anvil's rename field.
 *
 * <p>This is how the plugin makes login and register anonymous: the player types the password into
 * a client-side text field, the server receives it as an item name, and it never becomes a command
 * argument or a chat message — so there is no line for the server to log, no command history entry
 * and no tab-completion leak. The command form still exists for players who prefer it, protected by
 * the log filter.</p>
 *
 * <p>The callback is bound to the inventory's holder rather than to a separate player-keyed map, and
 * is claimed <em>before</em> the inventory is closed. Two ordering hazards make that necessary, and
 * both produced a silent no-op in earlier revisions:</p>
 * <ul>
 *   <li>{@code closeInventory()} fires {@link InventoryCloseEvent} synchronously, so a handler that
 *       clears the pending callback on close would delete it before the confirm path could read
 *       it. The password was then never delivered: registration appeared to accept the input but
 *       stored nothing, and the same password was subsequently reported as "not registered" rather
 *       than wrong.</li>
 *   <li>The two-step register flow (enter, then confirm) opens a second inventory from inside the
 *       first one's callback. A close event belonging to the first inventory can therefore arrive
 *       after the second is already open; clearing "the player's callback" blindly would destroy
 *       the confirmation step. Only the close of the inventory that is still registered as open for
 *       that player is honoured.</li>
 * </ul>
 */
public final class PasswordInput implements Listener {

    /** Longest title the client will display usefully in an anvil. */
    private static final int MAX_TITLE_LENGTH = 40;

    /** Marks an inventory as a password input and carries its continuation. */
    private static final class Holder implements InventoryHolder {

        private final UUID owner;
        private final BiConsumer<Player, String> onConfirm;
        private Inventory inventory;
        private boolean claimed;

        Holder(UUID owner, BiConsumer<Player, String> onConfirm) {
            this.owner = owner;
            this.onConfirm = onConfirm;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        /** Claims the continuation; only the first claim wins. */
        synchronized BiConsumer<Player, String> claim() {
            if (claimed) {
                return null;
            }
            claimed = true;
            return onConfirm;
        }
    }

    private final PluginContext ctx;
    private final Map<UUID, Holder> open = new ConcurrentHashMap<>();

    public PasswordInput(PluginContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Opens the input for a player.
     *
     * @param player    the player
     * @param prompt    shown as the window title
     * @param onConfirm receives the typed value on the main thread; the player is still online
     */
    public void open(Player player, Component prompt, BiConsumer<Player, String> onConfirm) {
        Holder holder = new Holder(player.getUniqueId(), onConfirm);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL, title(prompt));
        holder.attach(inventory);

        // The anvil's rename field is seeded from the first input item's display name, so that item
        // is deliberately left unnamed: naming it would pre-fill the field with the prompt and the
        // player's own password would be appended to it instead of replacing it.
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.lore(java.util.List.of(Component.text("Type it, then click the result slot to confirm.")));
        paper.setItemMeta(meta);
        inventory.setItem(0, paper);

        open.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    /** Truncates an over-long label so the client does not clip it mid-word. */
    private static Component title(Component prompt) {
        String text = PlainTextComponentSerializer.plainText().serialize(prompt);
        if (text.length() <= MAX_TITLE_LENGTH) {
            return prompt;
        }
        return Component.text(text.substring(0, MAX_TITLE_LENGTH - 1) + "…");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        // Cancel every route out of our inventory before deciding anything.
        event.setCancelled(true);
        if (!holder.owner.equals(player.getUniqueId())) {
            return;
        }
        // Raw slot 2 is the anvil result slot; anything in the player's own inventory is ignored.
        if (event.getRawSlot() != 2) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        String value = result == null ? "" : nameOf(result);

        // Claim first, then close. closeInventory() raises InventoryCloseEvent synchronously, and
        // closing before claiming is what made this silently do nothing.
        BiConsumer<Player, String> callback = holder.claim();
        if (callback == null) {
            return;
        }
        open.remove(player.getUniqueId(), holder);
        player.closeInventory();
        if (!value.isBlank()) {
            callback.accept(player, value);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    /**
     * Drops the registration only when the closing inventory is the one currently open for this
     * player. A close event for a superseded inventory (the first step of the two-step register
     * flow) must not clear the continuation that replaced it.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder holder) {
            open.remove(event.getPlayer().getUniqueId(), holder);
        }
    }

    private static String nameOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return "";
        }
        Component name = meta.displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name).trim();
    }

    /** Whether a player currently has an input open. */
    public boolean isOpen(Player player) {
        return open.containsKey(player.getUniqueId());
    }
}