package com.leomelonseeds.missilewars.listener.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.Trail;
import org.bukkit.World;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.event.player.PlayerReadyArrowEvent;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class AstralTurretManager implements Listener {
    
    private static AstralTurretManager instance;
    
    private Map<Player, Location> locs;
    private Map<Player, List<BukkitTask>> tasks;
    private Set<Player> drawingBow;
    
    private AstralTurretManager() {
        instance = this;
        this.locs = new HashMap<>();
        this.tasks = new HashMap<>();
        this.drawingBow = new HashSet<>();
        Bukkit.getPluginManager().registerEvents(this, MissileWarsPlugin.getPlugin());
    }
    
    /**
     * Register a player as using an astral turret at location
     * 
     * @param player
     * @param location
     */
    public void registerPlayer(Player player, Location location, boolean isRed) {
        unregisterPlayer(player, false);
        Location center = location.toCenterLocation();
        locs.put(player, center);
        
        // Particle task
        Color color = isRed ? Color.RED : Color.BLUE;
        Trail trailOptions = new Trail(center, color.setAlpha(128), 20);
        List<BukkitTask> tasksToAdd = new ArrayList<>();
        tasksToAdd.add(Bukkit.getScheduler().runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            player.getWorld().spawnParticle(
                    Particle.TRAIL, 
                    player.getLocation(), 
                    5, 0.2, 0.2, 0.2, 0, 
                    trailOptions, true);
        }, 0, 2));
        
        // Respawn anchor and dispenser task
        tasksToAdd.add(Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () -> {
            if (!locs.containsKey(player)) {
                return;
            }
            
            // Check if player no longer has offhand item
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.getType() != Material.ARROW) {
                unregisterPlayer(player);
                return;
            }

            // Check if player too low
            Location loc = player.getLocation();
            if (loc.getY() < -64) {
                unregisterPlayer(player);
                return;
            }

            // Check for respawn anchor display change
            int curCharges = 1;
            ItemStack mainhand = player.getInventory().getItemInMainHand();
            if (drawingBow.contains(player)) {
                curCharges = 4;
            } else if (isReadyThrowable(player, mainhand) || isReadyThrowable(player, offhand)) {
                curCharges = 3;
            }
            
            setRespawnAnchors(locs.get(player), curCharges);
        }, 0, 1));
        
        tasks.put(player, tasksToAdd);
        
        
        // Change shield appearance
        location.getBlock().setType(Material.END_GATEWAY);
        setRespawnAnchors(location, 1);
        
        // Play sound
        ConfigUtils.sendConfigSound("astralturret-activate", player.getLocation());
    }
    
    // Forcefully unregister a player if registered
    private void unregisterPlayer(Player player) {
        unregisterPlayer(player, true);
    }
    
    // Forcefully unregister a player if registered
    private void unregisterPlayer(Player player, boolean soundAtPlayer) {
        if (locs.containsKey(player)) {
            unregisterPlayer(player, locs.get(player), true, soundAtPlayer);
        }
    }
    
    /**
     * Make player stop using astral turret at a location. Used for
     * obsidian shield despawns
     * 
     * @param player
     * @param location
     * @return true if a player was successfully unregistered
     */
    public void unregisterPlayer(Player player, Location location) {
        unregisterPlayer(player, location, false, true);
    }
    
    /**
     * Make player stop using astral turret at a location
     * 
     * @param player
     * @param location
     * @param soundAtLocation
     * @param soundAtPlayer
     * @return true if a player was successfully unregistered
     */
    private void unregisterPlayer(Player player, Location location, boolean soundAtLocation, boolean soundAtPlayer) {
        Location toCheck = location.toCenterLocation();
        if (!toCheck.equals(locs.get(player))) {
            return;
        }
        
        locs.remove(player);
        tasks.get(player).forEach(t -> t.cancel());
        tasks.remove(player);
        drawingBow.remove(player);
        
        if (soundAtPlayer) {
            ConfigUtils.sendConfigSound("astralturret-deactivate", player.getLocation());
        }
        
        if (soundAtLocation) {
            setRespawnAnchors(location, 0);
            ConfigUtils.sendConfigSound("astralturret-deactivate", location);
        }
    }
    
    private void setRespawnAnchors(Location loc, int charges) {
        Location[] locs = new Location[4];
        locs[0] = loc.clone().add( 0,  1, 0);
        locs[1] = loc.clone().add( 0, -1, 0);
        locs[2] = loc.clone().add( 1,  0, 0);
        locs[3] = loc.clone().add(-1,  0, 0);
        World world = loc.getWorld();
        RespawnAnchor anchor = (RespawnAnchor) Material.RESPAWN_ANCHOR.createBlockData();
        anchor.setCharges(charges);
        for (Location blockLoc : locs) {
            world.setBlockData(blockLoc, anchor);
        }
    }
    
    public static AstralTurretManager getInstance() {
        if (instance == null) {
            return new AstralTurretManager();
        }
        
        return instance;
    }
    
    private boolean isReadyThrowable(Player player, ItemStack item) {
        Material type = item.getType();
        if (player.hasCooldown(type)) {
            return false;
        }
        
        return type == Material.EGG || type == Material.ENDER_PEARL || type == Material.SNOWBALL;
    }
    
    // Teleports launched projectiles to the astral turret
    @EventHandler
    private void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        ProjectileSource source = proj.getShooter();
        if (!locs.containsKey(source)) {
            return;
        }
        
        Player shooter = (Player) source;
        Location loc = locs.get(source).clone();
        float yaw = shooter.getLocation().getYaw();
        if (yaw > -90 && yaw < 90) {
            loc.add(0, 0, 1);
        } else {
            loc.add(0, 0, -1);
        }
        
        Location currentProjLoc = proj.getLocation();
        loc.setYaw(currentProjLoc.getYaw());
        loc.setPitch(currentProjLoc.getPitch());
        proj.teleport(loc);
        
        // SFX
        if (proj.getType() == EntityType.ARROW) {
            ConfigUtils.sendConfigSound("shoot-bow", loc);
        } else {
            ConfigUtils.sendConfigSound("throw-projectile", loc);
        }
    }
    
    // Unregister on player death
    @EventHandler(priority = EventPriority.HIGH)
    private void onDeath(PlayerDeathEvent event) {
        unregisterPlayer(event.getPlayer());
    }
    
    // Bow draw detection. Ready arrow fires when bow is drawn, and again when it is shot/released.
    // Therefore, this event can toggle the state of the player.
    @EventHandler
    private void onArrowReady(PlayerReadyArrowEvent event) {
        Player player = event.getPlayer();
        if (!locs.containsKey(player)) {
            return;
        }
        
        if (!drawingBow.contains(player)) {
            drawingBow.add(player);
        } else {
            drawingBow.remove(player);
        }
    }

    @EventHandler
    private void onSlotChange(PlayerItemHeldEvent event) {
        drawingBow.remove(event.getPlayer());
    }
}
