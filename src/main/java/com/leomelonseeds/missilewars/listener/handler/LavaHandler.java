package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

/**
 * Class for associating lava placements to killers
 * I hate creating singletons bruh but I can't be bothered
 * to figure out a better way and this is good enough
 */
public class LavaHandler implements Listener {
    
    private static LavaHandler instance;
    
    private Map<Location, Player> lavaSources;
    private Map<Player, Pair<Player, BukkitTask>> lastDamageSource;
    
    private LavaHandler() {
        this.lavaSources = new HashMap<>();
        this.lastDamageSource = new HashMap<>();
        Bukkit.getPluginManager().registerEvents(this, MissileWarsPlugin.getPlugin());
    }
    
    public void addLavaSource(Location location, Player source) {
        lavaSources.put(location, source);
    }
    
    public void removeLavaSource(Location location) {
        lavaSources.remove(location);
    }
    
    public Player getLastDamageSource(Player player) {
        if (lastDamageSource.containsKey(player)) {
            return lastDamageSource.get(player).getLeft();
        }
        
        return null;
    }
    
    @EventHandler
    private void onLavaDamage(EntityDamageByBlockEvent event) {
        if (event.getCause() != DamageCause.LAVA) {
            return;
        }
        
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Location loc = event.getDamager().getLocation();
        if (!lavaSources.containsKey(loc)) {
            return;
        }
        
        // Lava sets players on fire for up to 300 ticks. But this timer should
        // reset every time the player takes another tick of lava damage.
        BukkitTask removalTask = ConfigUtils.schedule(310, () -> {
            lastDamageSource.remove(player);
        });
        
        if (lastDamageSource.containsKey(player)) {
            lastDamageSource.get(player).getRight().cancel();
        }
        
        lastDamageSource.put(player, Pair.of(lavaSources.get(loc), removalTask));
    }
    
    public static LavaHandler getInstance() {
        if (instance == null) {
            instance = new LavaHandler();
        }
        
        return instance;
    }
}
