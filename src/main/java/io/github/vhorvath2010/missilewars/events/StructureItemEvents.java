package io.github.vhorvath2010.missilewars.events;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
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
        Block clicked = event.getClickedBlock();
        if (hand.getItemMeta() == null) {
            return;
        }
        if (!hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"),
                PersistentDataType.STRING)) {
            return;
        }

        // Stop if not right-click on block
        if (!event.getAction().toString().contains("RIGHT") || clicked == null) {
            return;
        }
        
        event.setCancelled(true);

        // Find player's team (Default to blue)
        boolean redTeam = false;
        ArenaManager manager = plugin.getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena != null) {
            redTeam = arena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.RED +
                    "red" + ChatColor.RESET);
        }
        
        String structureName = hand.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"), PersistentDataType.STRING);

        // Place structure
        SchematicManager.spawnNBTStructure(structureName, clicked.getLocation(), redTeam);
        hand.setAmount(hand.getAmount() - 1);
    }

    /** Handle utilities utilization. */
    @EventHandler
    public void useFireball(PlayerInteractEvent event) {
        // Check if player is trying to place a utility item
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Block clicked = event.getClickedBlock();
        if (hand.getItemMeta() == null) {
            return;
        }
        if (!hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-utility"),
                PersistentDataType.STRING)) {
            return;
        }
        
        String utility = hand.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-utility"), PersistentDataType.STRING);
        assert utility != null;
        
        // Allow event if using bow
        if (utility.equalsIgnoreCase("sentinel_bow")) {
            return;
        }
       

        // Do proper action based on utility type
        if (utility.equalsIgnoreCase("fireball") && event.getAction().toString().contains("RIGHT_CLICK")) {
        	event.setCancelled(true);
            Fireball fireball = (Fireball) player.getWorld().spawnEntity(player.getEyeLocation().clone().add(player
                    .getEyeLocation().getDirection()), EntityType.FIREBALL);
            fireball.setYield(1);
            fireball.setIsIncendiary(false);
            fireball.setDirection(player.getEyeLocation().getDirection());
            hand.setAmount(hand.getAmount() - 1);
        }
    }

}
