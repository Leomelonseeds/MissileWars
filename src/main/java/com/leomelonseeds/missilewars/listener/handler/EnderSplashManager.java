package com.leomelonseeds.missilewars.listener.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class EnderSplashManager {
    
    private static EnderSplashManager instance;
    
    private Map<Player, List<Entity>> ender_splash;
    
    public static EnderSplashManager getInstance() {
        if (instance == null) {
            instance = new EnderSplashManager();
        }
        
        return instance;
    }
    
    private EnderSplashManager() {
        ender_splash = new HashMap<>();
    }
    
    /**
     * Register a player with a splash
     * 
     * @param player
     * @param entity
     */
    public void addPlayer(Player player, Entity entity) {
        if (!ender_splash.containsKey(player)) {
            ender_splash.put(player, new ArrayList<>());
        }
        
        ender_splash.get(player).add(entity);
    }
    
    /**
     * Remove a splash for a player from the list.
     * 
     * @param player
     * @param entity
     * @return if the list contained the player/entity
     */
    public boolean removeSplash(Player player, Entity entity) {
        if (!ender_splash.containsKey(player)) {
            return false;
        }
        
        return ender_splash.get(player).remove(entity);
    }
    
    /**
     * Remove a player from the list, preventing any splashes from teleporting them
     * 
     * @param player
     */
    public void removePlayer(Player player) {
        ender_splash.remove(player);
    }

}
