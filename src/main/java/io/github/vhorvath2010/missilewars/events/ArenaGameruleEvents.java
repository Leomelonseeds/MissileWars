package io.github.vhorvath2010.missilewars.events;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import net.kyori.adventure.text.Component;

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

        // Cause instant death so player can respawn faster
        if (event.getCause() == DamageCause.VOID) {
            event.setDamage(40.0);
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
        	player.setBedSpawnLocation(ConfigUtils.getSpawnLocation(), true);
            return;
        }

        // Find killer and increment kills
        if (player.getKiller() != null) {
            MissileWarsPlayer killer = playerArena.getPlayerInArena(player.getKiller().getUniqueId());
            killer.incrementKills();
            ConfigUtils.sendConfigSound("player-kill", killer.getMCPlayer());
        }
        
        Component deathMessage = event.deathMessage();
        event.setDeathMessage("");

        // Count death if player is on a team
        if (!playerArena.getTeam(player.getUniqueId()).equals("no team")) {
            MissileWarsPlayer missileWarsPlayer = playerArena.getPlayerInArena(player.getUniqueId());
            missileWarsPlayer.incrementDeaths();
            for (Player p : player.getWorld().getPlayers()) {
                p.sendMessage(deathMessage);
            }
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
        
        if (!arena.isRunning()) {
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
        if (arena.getTeam(player.getUniqueId()).equalsIgnoreCase(arena.getTeam(damager.getUniqueId())) &&
           !arena.getTeam(player.getUniqueId()).equalsIgnoreCase("no team")) {
            event.setCancelled(true);
        }
    }
    
    /** Make sure players can't create portals */
    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        
        // Ensure it was in an arena world
        String possibleArenaName = event.getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        event.setCancelled(true);
    }
    
    /** Make sure players can't teleport with ender pearls */
    @EventHandler
    public void onPearl(PlayerTeleportEvent event) {
        
        // Ensure it's an ender pearl
        if (event.getCause() != TeleportCause.ENDER_PEARL) {
            return;
        }
        
        // Ensure it was in an arena world
        String possibleArenaName = event.getPlayer().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        event.setCancelled(true);
    }

    /** Handle fireball and TNT explosions. */
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        // Ensure it was in an arena world
        String possibleArenaName = event.getEntity().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        // Check for shield breaks
        event.blockList().forEach(block -> {
            // Register shield breaking
            possibleArena.registerShieldBlockEdit(block.getLocation(), false);
        });

        // Ensure its actually a fireball
        if (event.getEntityType() == EntityType.FIREBALL) {
            // Remove all portals from block list
            event.blockList().removeIf(block -> block.getType() == Material.NETHER_PORTAL);
        }
        // Check for TNT explosions of portals
        else if (event.getEntityType() == EntityType.PRIMED_TNT) {
            event.blockList().forEach(block -> {
                // Register portal brake if block was broken
                if (block.getType() == Material.NETHER_PORTAL) {
                    possibleArena.registerPortalBreak(block.getLocation());
                }
            });
        }
    }

    /** Handle shield block breaks breaks. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        // Ensure it was in an arena world
        String possibleArenaName = event.getBlock().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        // Register block break
        possibleArena.registerShieldBlockEdit(event.getBlock().getLocation(), false);
    }

    /** Handle shield block breaks places. */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        // Ensure it was in an arena world
        String possibleArenaName = event.getBlock().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        // Register block break
        possibleArena.registerShieldBlockEdit(event.getBlock().getLocation(), true);
    }

}
