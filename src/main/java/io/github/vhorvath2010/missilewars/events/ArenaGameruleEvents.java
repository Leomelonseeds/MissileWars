package io.github.vhorvath2010.missilewars.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;

/** Class to listen for events relating to Arena game rules. */
public class ArenaGameruleEvents implements Listener {

    /** Event to ignore hunger. */
    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld().getName().contains("mwarena_")) {
            event.setCancelled(true);
        }
    }
    
    /** Handle void death. Works outside arenas too. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
    	//Check if entity is player
    	if (!(event.getEntity() instanceof Player)) {
            return;
        }
    	
        Player player = (Player) event.getEntity();

        // Cause instant death so player can respawn faster
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            player.setHealth(0);
        }
    }

    /** Handle player deaths. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Check if player was killed in an Arena
        Player player = event.getEntity();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }

        // Find killer and increment kills
        if (player.getKiller() != null) {
            MissileWarsPlayer killer = playerArena.getPlayerInArena(player.getKiller().getUniqueId());
            killer.incrementKills();
        }
        
        player.setBedSpawnLocation(playerArena.getPlayerSpawn(player), true);
    }
}
