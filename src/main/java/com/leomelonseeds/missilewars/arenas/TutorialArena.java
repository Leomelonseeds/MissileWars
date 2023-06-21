package com.leomelonseeds.missilewars.arenas;

import java.util.Map;
import java.util.UUID;

import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class TutorialArena extends ClassicArena {
    
    public TutorialArena() {
        super("tutorial", 100);
    }

    public TutorialArena(Map<String, Object> serializedArena) {
        super(serializedArena);
    }
    
    // No need to track who left the arena here
    @Override
    public void addLeft(UUID uuid) {}
    
    @Override
    public void enqueue(UUID uuid, String team) {
        for (MissileWarsPlayer player : players) {
            if (!player.getMCPlayerId().equals(uuid)) {
                continue;
            }
            
            if (team.equals("red")) {
                ConfigUtils.sendConfigMessage("messages.training-blue-only", player.getMCPlayer(), this, null); 
                return;
            }
            
            // The game should NEVER be ended in this situation
            if (blueTeam.containsPlayer(uuid)) {
                return;
            }
            
            removeSpectator(player);
            blueTeam.addPlayer(player);
            checkNotEmpty();
            announceMessage("messages.queue-join-blue", player);
            break;
        }
    }
    
    @Override
    public String getTimeRemaining() {
        return "∞";
    }
    
    @Override
    public void performTimeSetup() {}
    
    @Override
    protected void startTeams() {}
    
    @Override
    protected void calculateStats(MissileWarsTeam winningTeam) {}
    
    @Override
    protected void autoEnd() {}
}
