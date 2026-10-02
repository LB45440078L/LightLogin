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
 * <p>The menu is identified by its {@link InventoryHolder}, not by a player-keyed map, so opening
 * one input while another is open cannot cross wires, and clicks are cancelled before anything is
 * decided.</p>
 */
public final class PasswordInput implements Listener {

    /** Marks an inventory as a password input. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final UUID owner;

        Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final PluginContext ctx;
    private final Map<UUID, BiConsumer<Player, String>> callbacks = new ConcurrentHashMap<>();

    public PasswordInput(PluginContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Opens the input for a player.
     *
     * @param player    the player
     * @param prompt    the label shown in the field
     * @param onConfirm receives the typed value; the player is still online and this runs on the
     *                  main thread
     */
    public void open(Player player, Component prompt, BiConsumer<Player, String> onConfirm) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL, Component.text(" "));
        holder.attach(inventory);

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(prompt);
        meta.lore(java.util.List.of(Component.text("Type and click the result slot to confirm.")));
        paper.setItemMeta(meta);
        inventory.setItem(0, paper);

        callbacks.put(player.getUniqueId(), onConfirm);
        player.openInventory(inventory);
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
        player.closeInventory();
        BiConsumer<Player, String> callback = callbacks.remove(player.getUniqueId());
        if (callback != null && !value.isBlank()) {
            callback.accept(player, value);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            callbacks.remove(event.getPlayer().getUniqueId());
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
        return callbacks.containsKey(player.getUniqueId());
    }
}