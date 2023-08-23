package com.leomelonseeds.missilewars.listener;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

}
