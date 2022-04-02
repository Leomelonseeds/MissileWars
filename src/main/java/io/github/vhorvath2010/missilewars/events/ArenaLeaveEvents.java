package io.github.vhorvath2010.missilewars.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.utilities.InventoryUtils;

/** Class for managing arena leaving. */
public class ArenaLeaveEvents implements Listener {

    /** Remove player from Arena if they DC. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Get Arena player is in and remove them
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
        	InventoryUtils.saveInventory(player);
            return;
        }
        playerArena.removePlayer(player.getUniqueId());
    }
    
    /** Handle inventory loading on join */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
    	Player player = event.getPlayer();
    	if (!player.getWorld().getName().equals("world")) {
    		return;
    	}
    	InventoryUtils.loadInventory(player);
    }

    /** Remove player from Arena if they leave the world.
     * Also handles inventory saving and clearing */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
    	
    	Player player = event.getPlayer();
    	
        if (event.getFrom().getName().contains("mwarena")) {       
	        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
	        Arena playerArena = manager.getArena(player.getUniqueId());
	        if (playerArena == null || player.getWorld().equals(playerArena.getWorld())) {
	            return;
	        }   
	        
	        playerArena.removePlayer(player.getUniqueId());
	        
	        if (player.getWorld().getName().equals("world")) {
	        	InventoryUtils.loadInventory(player);
	        }
	        
	        return;
        }

        
        if (event.getFrom().getName().equals("world") && 
    		player.getWorld().getName().contains("mwarena")) {
        	InventoryUtils.saveInventory(player);
        	InventoryUtils.clearInventory(player);
        	return;
        }
    }

}
