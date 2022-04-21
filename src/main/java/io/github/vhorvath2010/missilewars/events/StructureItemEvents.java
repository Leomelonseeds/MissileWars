package io.github.vhorvath2010.missilewars.events;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

/** Class to handle events for structure items. */
public class StructureItemEvents implements Listener {

    /**
     * Get a structure from a structure item.
     *
     * @param item the structure item
     * @return the name of the structure, or null if the item has none
     */
    private String getStructureFromItem(ItemStack item) {
        if (item.getItemMeta() == null) {
            return null;
        }
        if (!item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"),
                PersistentDataType.STRING)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get( new NamespacedKey(MissileWarsPlugin.getPlugin(),
                "item-structure"), PersistentDataType.STRING);
    }

    /**
     * Check if a given player is on the red team.
     *
     * @param player the player
     * @return true if they are on the red team, false otherwise (including if they are not on any team at all)
     */
    private boolean isRedTeam(Player player) {
        // Find player's team (Default to blue)
        boolean redTeam = false;
        Arena arena = getPlayerArena(player);
        if (arena != null) {
            redTeam = arena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.RED +
                    "red" + ChatColor.RESET);
        }
        return redTeam;
    }

    /**
     * Get the Arena the current player is in.
     *
     * @param player the player
     * @return the Arena the player is in
     */
    private Arena getPlayerArena(Player player) {
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        return arena;
    }

    /** Handle missile and other structure item spawning. */
    @EventHandler
    public void useStructureItem(PlayerInteractEvent event) {
        // Check if player is trying to place a structure item
    	MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        
        Arena playerArena = getPlayerArena(player);
        if (playerArena == null) {
            return;
        }
        
        ItemStack hand = player.getInventory().getItemInMainHand();
        Block clicked = event.getClickedBlock();
        String structureName = getStructureFromItem(hand);
        if (structureName == null) {
            return;
        }
      
        // Switch to throwing logic if using a throwable
        if (structureName.contains("shield_") || structureName.contains("platform_") || structureName.contains("torpedo_")
                || structureName.contains("obsidian_")) {
            return;
        }

        // Stop if not left-click on block
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

        // Place structure
        String mapName = "default-map";
        if (playerArena.getMapName() != null) {
            mapName = playerArena.getMapName();
        }
        if (SchematicManager.spawnNBTStructure(structureName, clicked.getLocation(), isRedTeam(player), mapName)) {
            hand.setAmount(hand.getAmount() - 1);
            playerArena.getPlayerInArena(player.getUniqueId()).incrementMissiles();
        } else {
        	ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
        }
    }

    /** Handle utilities utilization. */
    @EventHandler
    public void useUtility(PlayerInteractEvent event) {
        // Check if player is trying to place a utility item
        Player player = event.getPlayer();
        
        Arena playerArena = getPlayerArena(player);
        if (playerArena == null) {
            return;
        }
        
        ItemStack hand = player.getInventory().getItemInMainHand();
        // Handle splash potion through other methods
        if (hand.getType() == Material.SPLASH_POTION) {
            return;
        }
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
        // Stop if not left-click
        if (!event.getAction().toString().contains("RIGHT")) {
            return;
        }
        
        event.setCancelled(true);
        playerArena.getPlayerInArena(player.getUniqueId()).incrementUtility();

        // Do proper action based on utility type
        if (utility.equalsIgnoreCase("fireball") && event.getAction().toString().contains("RIGHT_CLICK")) {
            Fireball fireball = (Fireball) player.getWorld().spawnEntity(player.getEyeLocation().clone().add(player
                    .getEyeLocation().getDirection()), EntityType.FIREBALL);
            fireball.setYield(1);
            fireball.setIsIncendiary(false);
            fireball.setDirection(player.getEyeLocation().getDirection());
            hand.setAmount(hand.getAmount() - 1);
            for (Player players : player.getWorld().getPlayers()) {
            	 ConfigUtils.sendConfigSound("spawn-fireball", players, player.getLocation());
            }
        }
    }

    /** Handle projectile items structure creation */
    @EventHandler
    public void useProjectile(ProjectileLaunchEvent event) {
        // Ensure we are tracking a utility thrown by a player
        if (!(event.getEntity().getType() == EntityType.SNOWBALL ||
              event.getEntity().getType() == EntityType.EGG ||
              event.getEntity().getType() == EntityType.ENDER_PEARL)) {
            return;
        }
        Projectile thrown = event.getEntity();
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        Player thrower = (Player) thrown.getShooter();
        Arena playerArena = getPlayerArena(thrower);
        if (playerArena == null) {
            return;
        }

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
                    spawnUtility(structureName, thrown.getLocation(), thrower, thrown, playerArena);
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 20);
    }
    
    /** Utility should go through players */
    @EventHandler
    public void handleUtilityCollisions(ProjectileHitEvent event) {
        // Ensure we are tracking a utility thrown by a player
        if (!(event.getEntity().getType() == EntityType.SNOWBALL ||
              event.getEntity().getType() == EntityType.EGG ||
              event.getEntity().getType() == EntityType.ENDER_PEARL)) {
            return;
        }
        
        Projectile thrown = event.getEntity();
        
        // Make sure a player threw this projectile
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        
        // If it has a custom name, it definitely is a missile wars item
        if (thrown.getCustomName() == null) {
            return;
        }
        
        // Make sure it's in an arena
        Player thrower = (Player) thrown.getShooter();
        Arena playerArena = getPlayerArena(thrower);
        if (playerArena == null) {
            return;
        }
        
        // Make them go through entities
        if (event.getHitEntity() != null) {
            event.setCancelled(true);
            return;
        }
    }
    
    /** Handle spawning of utility structures */
    public void spawnUtility(String structureName, Location spawnLoc, Player thrower, Projectile thrown, Arena playerArena) {
        String mapName = "default-map";
        if (playerArena.getMapName() != null) {
            mapName = playerArena.getMapName();
        }
        if (SchematicManager.spawnNBTStructure(structureName, spawnLoc, isRedTeam(thrower), mapName)) {
            playerArena.getPlayerInArena(thrower.getUniqueId()).incrementUtility();
            String sound = "none";
            if (structureName.contains("shield_") || structureName.contains("platform")) {
                sound = "spawn-shield";
            } else if (structureName.contains("torpedo")) {
                sound = "spawn-torpedo";
            } else if (structureName.contains("obsidian_")) {
                sound = "spawn-obsidian-shield";
                clearObsidianShield(thrower, spawnLoc, isRedTeam(thrower), mapName, playerArena);
            }
            for (Player players : thrower.getWorld().getPlayers()) {
                ConfigUtils.sendConfigSound(sound, players, spawnLoc);
            }
        } else {
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", thrower, null, null);
        }
        if (!thrown.isDead()) {
            thrown.remove();
        }
    }
    
    /** Handle projectile item utility creation */
    @EventHandler
    public void throwSplash(ProjectileLaunchEvent event) {
        
        if (event.getEntity().getType() != EntityType.SPLASH_POTION) {
            return;
        }
        
        ThrownPotion thrown = (ThrownPotion) event.getEntity();
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        Player thrower = (Player) thrown.getShooter();
        Arena playerArena = getPlayerArena(thrower);
        if (playerArena == null) {
            return;
        }
        
        // Check if player is holding a utility item
        ItemStack hand = thrower.getInventory().getItemInMainHand();
        
        if (hand.getItemMeta() == null) {
            return;
        }
        if (!hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-utility"),
                PersistentDataType.STRING)) {
            return;
        }
        
        // Make sure it's splash potion of water
        if (!thrown.getEffects().isEmpty()) {
            return;
        }
        
        // Check the duration here
        thrown.setCustomName("splash:" + "1");
        playerArena.getPlayerInArena(thrower.getUniqueId()).incrementUtility();
    }
    
    /** Handle spawning of splash waters */
    @EventHandler
    public void handleSplash(ProjectileHitEvent event) {
        
        // Make sure we're getting the right potion here
        if (event.getEntityType() != EntityType.SPLASH_POTION) {
            return;
        }
        
        if (event.getEntity().getCustomName() == null) {
            return;
        }
        
        if (!event.getEntity().getCustomName().contains("splash:")) {
            return;
        } 
        
        // Extinguish entities
        if (event.getHitEntity() != null) {
            event.getHitEntity().setFireTicks(0);
        }
        
        // Spawn some water
        if (event.getHitBlock() == null) {
            return;
        }
        
        BlockFace face = event.getHitBlockFace();
        Location location = event.getHitBlock().getRelative(face).getLocation();
        
        if (location.getBlock().getType() != Material.AIR) {
            return;
        }
        
        location.getBlock().setType(Material.WATER);
        
        // Remove the water after a while
        String[] args = event.getEntity().getCustomName().split(":");
        double duration = Double.parseDouble(args[1]);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (location.getBlock().getType() == Material.WATER) {
                    location.getBlock().setType(Material.AIR);
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), (long) (duration * 20));
        
    }
    
    /** Clears obsidian shield after a certain amount of time */
    public void clearObsidianShield(Player thrower, Location location, Boolean red, String mapName, Arena playerArena) {
        
        // Insert code for detecting obsidian shield duration here
        int duration = 10 * 2;
        for (int i = duration; i > duration - 10; i--) {
            int finalDuration = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (playerArena.isRunning()) {
                        if (finalDuration == duration) {
                            SchematicManager.spawnNBTStructure("obsidian_shield_clear", location, red, mapName);
                            for (Player player : thrower.getWorld().getPlayers()) {
                                ConfigUtils.sendConfigSound("break-obsidian-shield", player, location); 
                            }
                        } else if (finalDuration % 2 == 0) {
                            SchematicManager.spawnNBTStructure("obsidian_shield_deplete", location, red, mapName);
                        } else {
                            SchematicManager.spawnNBTStructure("obsidian_shield", location, red, mapName);
                        }
                    }
                }          
            }.runTaskLater(MissileWarsPlugin.getPlugin(), i * 10L);
        }
    }
}
