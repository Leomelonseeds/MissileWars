package io.github.vhorvath2010.missilewars.events;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Class to listen for events relating to Arena game rules. */
public class ArenaGameruleEvents implements Listener {

    /** Events to handle creature spawns. */
    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        // Cancel natural spawns in arena worlds
        if (event.getLocation().getWorld() != null && event.getLocation().getWorld().getName().contains("mwarena_")) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                event.setCancelled(true);
            }
        }
    }

    /** Event to ignore hunger. */
    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld().getName().contains("mwarena_")) {
            event.setCancelled(true);
        }
    }

    /** Event to avoid deaths by void. */
    @EventHandler
    public void onVoidCross(PlayerMoveEvent event) {
        // Check if player is in Arena
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }

        // Check for void TP
        if (player.getLocation().getY() <= ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "default-settings.yml").getInt("void-tp-level")) {
            player.teleport(playerArena.getPlayerSpawn(player));
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
        if (player.getKiller() == null) {
            MissileWarsPlayer killer = playerArena.getPlayerInArena(player.getKiller().getUniqueId());
            killer.incrementKills();
        }
    }

    /** Handle player respawns. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Check if player is in Arena
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }

        // Setup proper respawn location
        event.setRespawnLocation(playerArena.getPlayerSpawn(player));
        playerArena.regear(player.getUniqueId());
    }

}
