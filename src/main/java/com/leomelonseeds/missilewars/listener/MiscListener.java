package com.leomelonseeds.missilewars.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.listener.packets.PositionListener;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;

public class MiscListener implements Listener {
    
    // WORLD CREATION LISTENER
    
    @EventHandler
    public void worldInit(WorldInitEvent e) {
        e.getWorld().setKeepSpawnInMemory(false);
    }
    
    // TRACKER LISTENER
    
    @EventHandler
    public void tntPrimed(TNTPrimeEvent e) {
        // Get arena
        World world = e.getBlock().getWorld();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(world);
        if (arena == null) {
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), 
                () -> arena.getTracker().assignPrimeSource(e));
    }
    
    @EventHandler
    public void pistonExtend(BlockPistonExtendEvent e) {
        // Add to b36 flame arrow listener
        e.getBlocks().forEach(b -> addToList(b, e));
        
        // Get arena
        World world = e.getBlock().getWorld();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(world);
        if (arena == null) {
            return;
        }
        
        arena.getTracker().registerPistonEvent(e);
    }
    
    /** Remove player from Arena if they DC. */
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {

        // Save player deck configuration
        Player player = event.getPlayer();
        MissileWarsPlugin.getPlugin().getJSON().savePlayer(player.getUniqueId());
        
        // Get Arena player is in and remove them
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            InventoryUtils.saveInventory(player, true);
            return;
        }

        playerArena.removePlayer(player.getUniqueId(), false);
        PositionListener.clientPosition.remove(player.getUniqueId());
        player.teleport(ConfigUtils.getSpawnLocation());
    }

    /** Handle inventory loading on join */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.teleport(ConfigUtils.getSpawnLocation());
        if (player.isInvulnerable()) {
            player.setInvulnerable(false);
        }
        
        // Make player undrunk
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "brew " + player.getName() + " 0 10");
        
        // Remove potion effects
        for (PotionEffect effect : player.getActivePotionEffects()){
            if (effect.getType() == PotionEffectType.DAMAGE_RESISTANCE) {
                continue;
            }
            player.removePotionEffect(effect.getType());
        }

        // Load player data, making sure for new players that it happens after an entry for them is created.
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        plugin.getSQL().createPlayer(player.getUniqueId(), result -> {
            MissileWarsPlugin.getPlugin().getJSON().loadPlayer(player.getUniqueId());
            InventoryUtils.loadInventory(player);
            RankUtils.setPlayerExpBar(player);
        });
        
        // Teleport new players to join arena
        if (player.hasPlayedBefore()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Arena arena = plugin.getArenaManager().getArena("tutorial");
            if (arena.isResetting()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> arena.joinPlayer(player), 20);
            } else {
                arena.joinPlayer(player);
            }
        }, 20);
    }
    
    // JOIN/LEAVE LISTENERS

    /** Remove player from Arena if they leave the world. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        
        // Idk why this check is here but whatever
        if (from.equals(to)) {
            return;
        }

        if (from.getName().contains("mwarena")) {
            Arena fromArena = manager.getArena(from);
            if (fromArena == null) {
                return;
            }
            
            // Arena to arena transfers
            if (to.getName().contains("mwarena")) {
                Arena toArena = manager.getArena(player.getUniqueId());
                if (toArena == null) {
                    return;
                }
                
                if (fromArena.getPlayers().contains(player)) {
                    fromArena.removePlayer(player.getUniqueId(), false);
                }
                
                if (!toArena.getPlayers().contains(player)) {
                    toArena.joinPlayer(player);
                }
                return;
            }
            
            // Arena to world transfer
            if (to.getName().equals("world")) {
                if (fromArena.getPlayers().contains(player)) {
                    fromArena.removePlayer(player.getUniqueId(), true);
                }
                return;
            }
            return;
        }
        
        // World to arena transfers
        if (from.getName().equals("world") && to.getName().contains("mwarena")) {
            Arena toArena = manager.getArena(player.getUniqueId());
            if (toArena == null) {
                return;
            }
            
            if (!toArena.getPlayers().contains(player)) {
                toArena.joinPlayer(player);
            }
        }
    }

    
    // ---------------------------------------------------------
    // This section ignites tnt if b36 hit with flame arrow
    // Extend TNT event already handled above
    // ---------------------------------------------------------
    
    public static Map<Location, Location> tnt = new HashMap<>();
    
    @EventHandler
    public void retractTNT(BlockPistonRetractEvent e) {
        e.getBlocks().forEach(b -> addToList(b, e));
    }
    
    private void addToList(Block b, BlockPistonEvent e) {
        if (b.getType() != Material.TNT) {
            return;
        }
        
        // Add location of where the block WILL BE to a list
        Location loc = b.getLocation().toCenterLocation();
        Vector direction = e.getDirection().getDirection().normalize();
        final Location finalLoc = loc.clone().add(direction);
        tnt.put(finalLoc, loc);
        
        // Update the spawning location of the tnt such that it spawns smoothly
        for (int i = 1; i <= 3; i++) {
            Vector toAdd = direction.clone().multiply(0.33 * i);
            int index = i;
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                if (!tnt.containsKey(finalLoc)) {
                    return;
                }
                if (index < 3) {
                    tnt.get(finalLoc).add(toAdd);
                }
                else {
                    tnt.remove(finalLoc);
                }
            }, i);
        }
    }
    
    @EventHandler
    public void igniteTNT(ProjectileHitEvent e) {
        if (e.getEntityType() != EntityType.ARROW) {
            return;
        }
        
        Arrow arrow = (Arrow) e.getEntity();
        if (arrow.getFireTicks() <= 0) {
            return;
        }
        
        if (e.getHitBlock() == null) {
            return;
        }
        
        Block b = e.getHitBlock();
        if (b.getType() != Material.MOVING_PISTON) {
            return;
        }
        
        Location loc = b.getLocation().toCenterLocation();
        if (!tnt.containsKey(loc)) {
            return;
        }
        
        b.setType(Material.AIR);
        Location spawnLoc = tnt.remove(loc).subtract(0, 0.5, 0);
        TNTPrimed primed = (TNTPrimed) b.getWorld().spawnEntity(spawnLoc, EntityType.PRIMED_TNT);
        primed.setFuseTicks(80);
        
        // Get source
        if (arrow.getShooter() instanceof Player) {
            primed.setSource((Player) arrow.getShooter());
        }
    }
    
    // ------------------------------------------------
    // This section handles jump pads (code stolen from HubBasics)
    // ------------------------------------------------
    
    @EventHandler
    public void onPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        
        if (event.getClickedBlock().getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals("world")) {
            return;
        }

        double radians = Math.toRadians(player.getLocation().getYaw());
        double x = -Math.sin(radians) * 1.5;
        double y = 1.0;
        double z = Math.cos(radians) * 1.5;
        player.setVelocity(new Vector(x, y, z));
        ConfigUtils.sendConfigSound("lobby-plate", player);
        event.setCancelled(true);
    }
    
    // --------------------------------------------------
    // This section helps high-ping users with fireball deflections
    // event is high priority to execute after player drop/interact
    // --------------------------------------------------
    
    public static Set<UUID> notLeftClick = new HashSet<>();
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        // Cancel if a left click was recently detected
        Player player = event.getPlayer();
        if (notLeftClick.contains(player.getUniqueId())) {
            return;
        }

        Entity target = player.getTargetEntity(2); // 2 is enough for 3 block reach (for some reason)
        if (target == null) {
            return;
        }
        
        // Handle dragon fireballs by registering an EDBEE for the handler
        if (target instanceof Slime) {
            @SuppressWarnings("deprecation")
            EntityDamageByEntityEvent extraEvent = new EntityDamageByEntityEvent(player, target, DamageCause.ENTITY_ATTACK, 0.001);
            Bukkit.getPluginManager().callEvent(extraEvent);
            return;
        }
        
        // Thank you so much mister CAG2 for suggesting Player#attack, absolute genius if you ask me
        if (target instanceof Fireball) {
            player.attack(target);
        }
    }
    
    // Debug world saving
    @EventHandler
    public void onWorldSave(WorldSaveEvent e) {
        MissileWarsPlugin.getPlugin().log("World save detected... world: " + e.getWorld().getName());
    }
}
