package io.github.vhorvath2010.missilewars.events;

import java.util.List;

import org.bukkit.ChatColor;
<<<<<<< Updated upstream
=======
import org.bukkit.Location;
import org.bukkit.Material;
>>>>>>> Stashed changes
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
<<<<<<< Updated upstream
=======
import org.bukkit.event.entity.ProjectileLaunchEvent;
>>>>>>> Stashed changes
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

/** Class to handle events for structure items. */
public class StructureItemEvents implements Listener {

    @EventHandler
    public void useStructureItem(PlayerInteractEvent event) {
        // Check if player is trying to place a structure item
    	MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
;        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Block clicked = event.getClickedBlock();
        if (hand.getItemMeta() == null) {
            return;
        }
<<<<<<< Updated upstream
        if (!hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"),
                PersistentDataType.STRING)) {
=======

        // Switch to throwing logic if using shield
        if (structureName.contains("shield") && !structureName.contains("buster")) {
>>>>>>> Stashed changes
            return;
        }

        // Stop if not right-click on block
        if (!event.getAction().toString().contains("RIGHT") || clicked == null) {
            return;
        }
        
        event.setCancelled(true);
        
        List<String> cancel = plugin.getConfig().getStringList("cancel-schematic");
        
        for (String s : cancel) {
        	if (clicked.getType() == Material.getMaterial(s)) {
            	ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
        		return;
        	}
        }

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
<<<<<<< Updated upstream
        SchematicManager.spawnNBTStructure(structureName, clicked.getLocation(), redTeam);
        hand.setAmount(hand.getAmount() - 1);
=======
        if (SchematicManager.spawnNBTStructure(structureName, clicked.getLocation(), isRedTeam(player))) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
        	ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
        }
>>>>>>> Stashed changes
    }

    /** Handle utilities utilization. */
    @EventHandler
    public void useFireball(PlayerInteractEvent event) {
        // Check if player is trying to place a utility item
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
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
<<<<<<< Updated upstream
       
=======
        // Stop if not left-click
        if (!event.getAction().toString().contains("RIGHT")) {
            return;
        }
        event.setCancelled(true);
>>>>>>> Stashed changes

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

<<<<<<< Updated upstream
=======
    /** Handle shield snowball creation */
    @EventHandler
    public void useShield(ProjectileLaunchEvent event) {
        // Ensure we are tracking a snowball thrown by a player
        if (event.getEntity().getType() != EntityType.SNOWBALL) {
            return;
        }
        Snowball thrown = (Snowball) event.getEntity();
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        Player thrower = (Player) thrown.getShooter();

        // Check if player is holding a structure item
        ItemStack hand = thrower.getInventory().getItemInMainHand();
        String structureName = getStructureFromItem(hand);
        if (structureName == null) {
            return;
        }

        // Add meta for structure identification
        thrown.setCustomName(structureName);

        // Schedule structure spawn after 1 second if snowball is still alive
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!thrown.isDead()) {
                    // Spawn shield at current location and remove snowball
                    Location spawnLoc = thrown.getLocation();
                    SchematicManager.spawnNBTStructure(structureName, spawnLoc, isRedTeam(thrower));
                    thrown.remove();
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 20);
    }
>>>>>>> Stashed changes
}
