package io.github.vhorvath2010.missilewars.events;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerRespawnEvent;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;

import java.util.ArrayList;

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
        ArenaLeaveEvents.beingRespawned.add(player);
        if (player.getKiller() != null) {
            MissileWarsPlayer killer = playerArena.getPlayerInArena(player.getKiller().getUniqueId());
            killer.incrementKills();
        }
        
        player.setBedSpawnLocation(playerArena.getPlayerSpawn(player), true);
    }

    /** Handle friendly fire. */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Ensure we are handling a player in an arena
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }
        Player player = (Player) event.getEntity();
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }

        // Check if player is damaged by a player
        Player damager = null;
        if (event.getDamager().getType() == EntityType.PLAYER) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                damager = (Player) projectile.getShooter();
            }
        }
        if (damager == null) {
            return;
        }

        // Stop event if damager and damaged are on same team
        if (arena.getTeam(player.getUniqueId()).equalsIgnoreCase(arena.getTeam(damager.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    /** Handle portal breaking in Arenas. */
    @EventHandler
    public void onPortalBreak(BlockPhysicsEvent event) {
        // See if portal was broken
        if (event.getChangedType() != Material.NETHER_PORTAL) {
            return;
        }
        // Ensure it was in an arena world
        String possibleArenaName = event.getBlock().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        // Register portal breaking at the location
        possibleArena.registerPortalBreak(event.getBlock().getLocation());
    }

    /** Handle fireball explosions. */
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        // Ensure it was in an arena world
        String possibleArenaName = event.getEntity().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        // Remove all portals from block list
        event.blockList().removeIf(block -> block.getType() == Material.NETHER_PORTAL);
    }

}
