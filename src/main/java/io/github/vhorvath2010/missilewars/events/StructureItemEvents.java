package io.github.vhorvath2010.missilewars.events;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Class to handle events for structure items. */
public class StructureItemEvents implements Listener {

    @EventHandler
    public void useStructureItem(PlayerInteractEvent event) {
        // Check if player is trying to place a structure item
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getItemMeta() == null) {
            return;
        }
        if (!hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"),
                PersistentDataType.STRING)) {
            return;
        }
        String structureName = hand.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"), PersistentDataType.STRING);
        event.setCancelled(true);

        // Stop if not left-click
        if (!event.getAction().toString().contains("RIGHT")) {
            return;
        }

        // Find player's team (Default to blue)
        boolean redTeam = false;
        ArenaManager manager = plugin.getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena != null) {
            redTeam = arena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.RED +
                    "red" + ChatColor.RESET);
        }

        // Place structure
        SchematicManager.spawnNBTStructure(structureName, player.getLocation(), redTeam);
        hand.setAmount(hand.getAmount() - 1);
    }

}
