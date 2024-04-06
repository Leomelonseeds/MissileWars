package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.SchematicManager;

public class CanopyManager implements Listener {
    
    private static CanopyManager instance;
    
    private Set<Player> canopy_freeze;
    private Map<Player, ItemStack> canopy_cooldown;
    private Map<Location, Integer> canopy_extensions;
    
    public static CanopyManager getInstance() {
        if (instance == null) {
            instance = new CanopyManager();
        }
        
        return instance;
    }
    
    private CanopyManager() {
        canopy_freeze = new HashSet<>();
        canopy_cooldown = new HashMap<>();
        canopy_extensions = new HashMap<>();
    }
    
    /** Stop players from moving a second after canopy spawn */
    @EventHandler
    public void canopyFreeze(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!canopy_freeze.contains(player)) {
            return;
        }

        if (e.getFrom().distance(e.getTo()) > 0.1) {
            e.setCancelled(true);
        }
    }
    
    /**
     * Register canopy extension (from splash most likely)
     * 
     * @param key
     * @param extraduration
     */
    public void registerExtension(Location key, int extraduration) {
        if (canopy_extensions.containsKey(key)) {
            canopy_extensions.put(key, canopy_extensions.get(key) + extraduration);
        } else {
            canopy_extensions.put(key, extraduration);
        }
    }

    
    /**
     * Unregister player from canopy manager, and return
     * the player's itemstack
     * 
     * @param player
     * @return
     */
    public ItemStack removePlayer(Player player) {
        canopy_freeze.remove(player);
        return canopy_cooldown.remove(player);
    }
    
    /**
     * Check if player frozen
     * 
     * @param player
     * @return
     */
    public boolean isFrozen(Player player) {
        return canopy_freeze.contains(player);
    }
    
    
    /**
     * Set after player initially throws canopy
     * 
     * @param player
     */
    public void initPlayer(Player player, ItemStack hand, Arena playerArena, int canopy_distance) {
        if (canopy_cooldown.containsKey(player)) {
            return;
        }
        
        // Spawn ender eye
        Location eyeLoc = player.getEyeLocation();
        EnderSignal signal = (EnderSignal) playerArena.getWorld().spawnEntity(eyeLoc, EntityType.ENDER_SIGNAL);
        
        // Set target location
        Vector distance = eyeLoc.getDirection().multiply(canopy_distance);
        Location target = eyeLoc.clone().add(distance).toCenterLocation();
        ConfigUtils.sendConfigSound("launch-canopy", player.getLocation());
        signal.setDropItem(false);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (signal.isDead()) {
                    this.cancel();
                    return;
                }
                
                // Updates canopy so it travels to the correct location
                // No clue why I need to do this but oh well
                signal.setTargetLocation(target);
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 0, 5);
        
        // Add player to canopy cooldown list to give item back on death
        InventoryUtils.consumeItem(player, playerArena, hand, -1);
        canopy_cooldown.put(player, hand);
        
        // Send sound 2 seconds later, tp 3 sec later
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            if (!canopy_cooldown.containsKey(player)) {
                return;
            }
            
            ConfigUtils.sendConfigSound("canopy-activate", player);
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> 
                spawnCanopy(player, playerArena, signal), 20L);
        }, 40L);
    }
    
    private void spawnCanopy(Player player, Arena playerArena, EnderSignal signal) {
        // Ignore offline players. Obviously
        if (!player.isOnline()) {
            return;
        }
        
        ItemStack hand = canopy_cooldown.remove(player);
        if (hand == null) {
            return;
        }
    
        String mapName = "default-map";
        if (playerArena.getMapName() != null) {
            mapName = playerArena.getMapName();
        }
        
        Location spawnLoc = signal.getLocation().toCenterLocation();
        if (spawnLoc.getBlock().getType() != Material.AIR ||
            spawnLoc.clone().add(0, 1, 0).getBlock().getType() != Material.AIR) {
            ConfigUtils.sendConfigMessage("messages.canopy-blocked", player, null, null);
            InventoryUtils.regiveItem(player, hand);
            return;
        }
        
        boolean isRed = playerArena.getTeam(player.getUniqueId()) == TeamName.RED;
        if (!SchematicManager.spawnNBTStructure(player, "canopy-1", spawnLoc, isRed, mapName, false, true)) {
            InventoryUtils.regiveItem(player, hand);
            return;
        }
            
        // Teleport and give slowness
        int freezeTime = 30;
        canopy_freeze.add(player);
        Location loc = spawnLoc.add(0, -0.5, 0);
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, freezeTime, 6, true, false));
        player.teleport(loc);
        signal.remove();

        // Freeze player for a bit
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> 
            canopy_freeze.remove(player), freezeTime);
        
        ConfigUtils.sendConfigSound("spawn-canopy", spawnLoc);
        playerArena.getPlayerInArena(player.getUniqueId()).incrementUtility();
        despawnCanopy(spawnLoc, 5);
    }
    
    private void despawnCanopy(Location loc, int duration) {
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            Location wood = loc.clone().add(0, -1, 0).getBlock().getLocation();
            if (wood.getBlock().getType() == Material.OAK_WOOD) {
                if (canopy_extensions.containsKey(wood)) {
                    despawnCanopy(loc, canopy_extensions.get(wood));
                    canopy_extensions.remove(wood);
                    return;
                }
                wood.getBlock().setType(Material.AIR);
            }
        }, duration * 20L);
    }
}
