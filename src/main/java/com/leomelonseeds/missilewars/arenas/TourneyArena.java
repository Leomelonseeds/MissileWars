package com.leomelonseeds.missilewars.arenas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;


/**
 * DO NOT USE: IN CONSTRUCTION
 * 
 * @author LEGEND
 *
 */
public class TourneyArena extends ClassicArena {

    public TourneyArena(String name, int capacity) {
        super(name, capacity);
        gamemode = "tourney";
    }

    /**
     * Constructor from a serialized Arena.
     *
     * @param serializedArena the yml serialized Arena
     */
    public TourneyArena(Map<String, Object> serializedArena) {
        super(serializedArena);
    }
    
    @Override
    public void checkForStart() {}
    
    /**
     * Assigns players and starts the teams
     * DO NOT USE, NEEDS REFACTORING
     */
    @Override
    public void startTeams() {
        // Assign players to teams based on queue (which removes their items)
        List<MissileWarsPlayer> toAssign = new ArrayList<>();
        for (MissileWarsPlayer player : players.values()) {
            if (!spectators.contains(player)) {
                toAssign.add(player);
            }
        }
        Collections.shuffle(toAssign);
        double maxQueue = Math.ceil((double) players.size() / 2);
        
        // Teleport all players to center to remove lobby minigame items/dismount
        for (MissileWarsPlayer player : players.values()) {
            player.getMCPlayer().teleport(getPlayerSpawn(player.getMCPlayer()));
            player.getMCPlayer().closeInventory();
        }
        
        new BukkitRunnable() {
            @Override
            public void run() {
                // Assign queued players. If a queue is larger than a team size put remaining
                // players into the front of the queue to be assigned first into random teams
                while (!blueQueue.isEmpty() || !redQueue.isEmpty()) {
                    if (!redQueue.isEmpty()) {
                        MissileWarsPlayer toAdd = redQueue.remove();
                        toAssign.remove(toAdd);
                        if (redTeam.getSize() < maxQueue) {
                            redTeam.addPlayer(toAdd);
                        } else {
                            toAssign.add(0, toAdd);
                        }
                    }
                    if (!blueQueue.isEmpty()) {
                        MissileWarsPlayer toAdd = blueQueue.remove();
                        toAssign.remove(toAdd);
                        if (blueTeam.getSize() < maxQueue) {
                            blueTeam.addPlayer(toAdd);
                        } else {
                            toAssign.add(0, toAdd);
                        }
                    }
                }

                // Send messages
                redTeam.sendSound("game-start");
                blueTeam.sendSound("game-start");
                redTeam.sendTitle("classic-start");
                blueTeam.sendTitle("classic-start");
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 5L);
        // Start deck distribution for each team and send messages
    }
    
    /**
     * Similar to normal deck distribution, except everyone gets the same things
     */
    public void scheduleItemsRanked() {
        // Dummy method that doesn't do anything
    }
    
    /** Remove Players from the map. */
    @Override
    public void removePlayers() {
        for (MissileWarsPlayer player : new HashSet<>(players.values())) {
            removePlayer(player.getMCPlayerId(), true);
        }
    }
}
