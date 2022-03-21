package io.github.vhorvath2010.missilewars.events;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;

/** Class for managing arena leaving. */
public class ArenaLeaveEvents implements Listener {

    public static Set<Player> beingRespawned = new HashSet<>();

    /** Remove player from Arena if they DC. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Get Arena player is in and remove them
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        playerArena.removePlayer(player.getUniqueId());
    }

    /** Remove player from Arena if they leave the world. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Check if leaving a MissileWars world
        if (!event.getFrom().getName().contains("mwarena")) {
            return;
        }

        // Get Arena player is in and remove them
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null || beingRespawned.remove(player)) {
            if (playerArena != null) {
                player.teleport(playerArena.getPlayerSpawn(player));
            }
            return;
        }
        playerArena.removePlayer(player.getUniqueId());
    }

}
