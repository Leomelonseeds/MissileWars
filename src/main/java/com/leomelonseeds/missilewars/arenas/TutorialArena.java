package com.leomelonseeds.missilewars.arenas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.bossbar.BossBar;

public class TutorialArena extends ClassicArena {
    
    private Map<UUID, Integer> stage;
    private List<BossBar> bossbars;
    
    public TutorialArena() {
        super("tutorial", 100);
        this.stage = new HashMap<>();
        
        // Initialize boss bars
        FileConfiguration messages = ConfigUtils.getConfigFile("messages.yml");
        for (int i = 0; i <= 6; i++) {
            String line = messages.getString("bossbar.stage" + i);
            BossBar bar = BossBar.bossBar(ConfigUtils.toComponent(line), 1, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            bossbars.set(i, bar);
        }
    }

    public TutorialArena(Map<String, Object> serializedArena) {
        super(serializedArena);
    }

    @Override
    protected void postJoin(Player player, boolean asSpectator) { 
        UUID uuid = player.getUniqueId();
        if (!stage.containsKey(uuid)) {
            stage.put(uuid, 0);
        }
        enqueue(uuid, "blue");
        initiateStage(player, stage.get(uuid));
    }
    
    @Override
    public void enqueue(UUID uuid, String team) {
        for (MissileWarsPlayer player : players) {
            if (!player.getMCPlayerId().equals(uuid)) {
                continue;
            }
            
            if (!running) {
                ConfigUtils.sendConfigMessage("messages.tutorial-not-available", player.getMCPlayer(), this, null); 
                return; 
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
            break;
        }
    }
    
    // Activates bossbar, chat, and title of a stage
    private void initiateStage(Player player, int s) {
        player.showBossBar(bossbars.get(s));
        ConfigUtils.sendConfigMessage("messages.stage" + s, player, null, null);
        ConfigUtils.sendTitle("stage" + s, player);
        if (s == 0) {
            ConfigUtils.sendConfigSound("stagecomplete", player);
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                initiateStage(player, 1);
            }, 120);
            return;
        } 
        
        ConfigUtils.sendConfigSound("stage", player);
    }
    
    /**
     * Completes stage s for the player. If the player is also
     * currently on stage s, the stage will be incremented.
     * 
     * @param player
     * @param s
     */
    public void registerStageCompletion(Player player, int s) {
        UUID uuid = player.getUniqueId();
        int actualStage = stage.get(uuid);
        if (s != actualStage) {
            return;
        }

        ConfigUtils.sendTitle("stagecomplete", player);
        if (s == 6) {
            ConfigUtils.sendConfigMessage("messages.tutorial-complete", player, null, null);
            removePlayer(uuid, true);
        }
        
        stage.put(uuid, actualStage + 1);
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            initiateStage(player, actualStage + 1);
        }, 60);
    }
    
    /**
     * Hides all tutorial bossbars for the player
     * 
     * @param player
     */
    public void removeBossBars(Player player) {
        for (BossBar b : bossbars) {
            player.hideBossBar(b);
        }
    }
    
    @Override
    protected void giveHeldItems(Player player) {}
    
    // No need to track who left the arena here
    @Override
    public void addLeft(UUID uuid) {}
    
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
