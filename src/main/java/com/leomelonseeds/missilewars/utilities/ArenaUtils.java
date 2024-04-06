package com.leomelonseeds.missilewars.utilities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;

public class ArenaUtils {

    /**
     * Determine if a player is out of bounds
     * 
     * @param player
     * @param arena
     * @return
     */
    public static boolean outOfBounds(Player player, Arena arena) {
        if (arena == null || !arena.isRunning() || arena.isWaitingForTie()) {
            return false;
        }
        double toohigh = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-high");
        double toofar = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-far");
        Location loc = player.getLocation();
        return loc.getBlockY() > toohigh || loc.getBlockX() < toofar;
    }

    /**
     * Gets the cause/associated player of a spawned entity
     * mainly used for tracking kills and broken portals
     * 
     * @param killerEntity
     * @param arena
     * @return
     */
    public static Player getAssociatedPlayer(Entity killerEntity, Arena arena) {
        Player player = null;
        if (killerEntity.getType() == EntityType.CREEPER) {
            Creeper creeper = (Creeper) killerEntity;
            if (creeper.isCustomNameVisible()) {
                String name = ConfigUtils.removeColors(ConfigUtils.toPlain(creeper.customName()));
                String[] args = name.split("'");
                player = Bukkit.getPlayer(args[0]);
            }
        } else if (killerEntity.getType() == EntityType.PRIMED_TNT) {
            TNTPrimed tnt = (TNTPrimed) killerEntity;
            if (tnt.getSource() instanceof Player) {
                player = (Player) tnt.getSource();
            }
        } else if (killerEntity.getType() == EntityType.MINECART_TNT) {
            ExplosiveMinecart cart = (ExplosiveMinecart) killerEntity;
            player = arena.getTracker().getTNTMinecartSource(cart);
        }
        return player;
    }

    /**
     * Check if given location is in given arena with given team
     * 
     * @param arena
     * @param location
     * @param team
     * @param bias Number of blocks on all directions of shield that are also counted
     * @return
     */
    public static boolean inShield(Arena arena, Location location, TeamName team, int bias) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String gamemode = arena.getGamemode();
        String mapName = arena.getMapName();
        int x1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.x1");
        int x2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.x2");
        int y1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.y1");
        int y2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.y2");
        int z1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.z1");
        int z2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.z2");
        if (x1 - bias <= x && x <= x2 + bias && 
            y1 - bias <= y && y <= y2 + bias &&
            z1 - bias <= z && z <= z2 + bias) {
            return true;
        }
        return false;
    }

    /**
     * inShield with no biases
     * 
     * @param arena
     * @param location
     * @param team
     * @return
     */
    public static boolean inShield(Arena arena, Location location, TeamName team) {
        return inShield(arena, location, team, 0);
    }
    
    /**
     * Shorthand for getting arena from player
     * 
     * @param player
     * @return null or arena
     */
    public static Arena getArena(Player player) {
        return MissileWarsPlugin.getPlugin().getArenaManager().getArena(player.getUniqueId());
    }
}
