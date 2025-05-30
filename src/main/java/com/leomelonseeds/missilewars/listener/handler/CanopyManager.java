package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer.Stat;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.SchematicManager;

public class CanopyManager {
    
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
        EnderSignal signal = (EnderSignal) playerArena.getWorld().spawnEntity(eyeLoc, EntityType.EYE_OF_ENDER);
        
        // Set target location
        Vector distance = eyeLoc.getDirection().multiply(canopy_distance);
        Location target = eyeLoc.clone().add(distance).toCenterLocation();
        ConfigUtils.sendConfigSound("launch-canopy", player.getLocation());
        signal.setDropItem(false);
        
        // Add player to canopy cooldown list to give item back on death
        InventoryUtils.consumeItem(player, playerArena, hand, -1);
        canopy_cooldown.put(player, hand);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (signal.isDead()) {
                    this.cancel();
                    return;
                }
                
                // Updates canopy so it travels to the correct location
                // No clue why I need to do this but oh well
                signal.setTargetLocation(target, false);
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 0, 5);
        
        // Send sound 2 seconds later, tp 3 sec later
        ConfigUtils.schedule(40, () -> {
            if (!canopy_cooldown.containsKey(player)) {
                return;
            }
            
            ConfigUtils.sendConfigSound("canopy-activate", player);
            ConfigUtils.schedule(20, () -> spawnCanopy(player, playerArena, signal));
        });
    }
    
    private void spawnCanopy(Player player, Arena playerArena, EnderSignal signal) {
        // Ignore offline players, or if signal is dead
        if (!player.isOnline() || signal.isDead()) {
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
        
        // Check if canopy destination is blocked
        Location spawnLoc = signal.getLocation().toCenterLocation();
        if (spawnLoc.getBlock().getType() != Material.AIR ||
            spawnLoc.clone().add(0, 1, 0).getBlock().getType() != Material.AIR) {
            ConfigUtils.sendConfigMessage("canopy-blocked", player);
            InventoryUtils.regiveItem(player, hand);
            return;
        }
        
        // Check if player would take too much fall damage.
        // Raw fall damage is ceil of player fall distance - 3
        // Each level of jump boost further reduces fall damage by 1
        // Each feather falling level reduces fall damage by 12%
        double rawDmg = Math.ceil(player.getFallDistance()) - 3;
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jump != null) {
            rawDmg -= jump.getAmplifier() + 1;
        }
        
        int ff = player.getInventory().getItem(EquipmentSlot.FEET).getEnchantmentLevel(Enchantment.FEATHER_FALLING);
        if (ff > 0) {
            rawDmg *= 1 - 0.12 * ff;
        }
        
        if (rawDmg > player.getHealth()) {
            ConfigUtils.sendConfigMessage("canopy-fall", player);
            InventoryUtils.regiveItem(player, hand);
            return;
        }
        
        // Try to teleport player finally (but regive if unbreakable blocks)
        boolean isRed = playerArena.getTeam(player.getUniqueId()) == TeamName.RED;
        if (!SchematicManager.spawnNBTStructure(player, "canopy-1", spawnLoc, isRed, mapName, false, true)) {
            InventoryUtils.regiveItem(player, hand);
            return;
        }
            
        // Teleport and remove ender eye
        Location loc = spawnLoc.add(0, -0.5, 0);
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        player.teleport(loc);
        player.damage(rawDmg);
        signal.remove();

        // Stop players from placing missiles or using utility for a second
        loc.getBlock().setType(Material.COBWEB);
        canopy_freeze.add(player);
        ConfigUtils.schedule(10, () -> canopy_freeze.remove(player));
        
        // Do final checks
        ConfigUtils.sendConfigSound("spawn-canopy", spawnLoc);
        playerArena.getPlayerInArena(player.getUniqueId()).incrementStat(Stat.UTILITY);
        despawnCanopy(spawnLoc, 5);
    }
    
    private void despawnCanopy(Location loc, int duration) {
        ConfigUtils.schedule(duration * 20, () -> {
            Location wood = loc.clone().add(0, -1, 0).getBlock().getLocation();
            if (wood.getBlock().getType() != Material.OAK_WOOD) {
                return;
            }
            
            if (canopy_extensions.containsKey(wood)) {
                despawnCanopy(loc, canopy_extensions.get(wood));
                canopy_extensions.remove(wood);
                return;
            }
            
            wood.getBlock().setType(Material.AIR);
            
            // There may be a cobweb - set that to air too
            Location cobweb = wood.clone().add(0, 1, 0);
            if (cobweb.getBlock().getType() == Material.COBWEB) {
                cobweb.getBlock().setType(Material.AIR);
            }
        });
    }
}
