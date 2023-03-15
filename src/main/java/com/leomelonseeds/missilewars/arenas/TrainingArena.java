package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class TrainingArena extends Arena {
    
    public TrainingArena() {
        super("training", 100);
    }

    public TrainingArena(Map<String, Object> serializedArena) {
        super(serializedArena);
    }

    @Override
    public void enqueueRed(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                ConfigUtils.sendConfigMessage("messages.training-blue-only", player.getMCPlayer(), this, null); 
                return;
            }
        }
    }
    
    @Override
    public void enqueueBlue(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                
                // Make sure people can't break the game
                if (startTime != null) {
                    int time = (int) Duration.between(LocalDateTime.now(), startTime).toSeconds();
                    if (time <= 1 && time >= -1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-time", player.getMCPlayer(), this, null);
                        return;
                    }
                }
                
                if (!running) {
                    blueQueue.add(player);
                    ConfigUtils.sendConfigMessage("messages.queue-waiting-blue", player.getMCPlayer(), this, null);
                    removeSpectator(player);
                } else {
                    removeSpectator(player);
                    redTeam.removePlayer(player);
                    blueTeam.addPlayer(player);
                    player.giveDeckGear();
                    checkNotEmpty();
                    announceMessage("messages.queue-join-blue", player);
                }
                break;
            }
        }
    }
    
    // Post-join actions that may differ by arena
    @Override
    protected void postJoin(Player player, boolean asSpectator) { 
        // Check for AFK
        ConfigUtils.sendConfigMessage("messages.joined-training", player, null, null);
        
        // Auto-join team if setting turned on
        if (!player.hasPermission("umw.disableautoteam") && running) {
            enqueueBlue(player.getUniqueId());
        }
    }
    
    @Override
    public void checkForStart() {
        if (running || resetting) {
            return;
        }
        if (getNumPlayers() >= 1) {
            scheduleStart(6);
        }
    }
    
    @Override
    public void checkEmpty() {}
    
    @Override
    public String getTimeRemaining() {
        return "∞";
    }
    
    @Override
    public void performTimeSetup() {
        // Compile list of available missiles to use
        FileConfiguration items = ConfigUtils.getConfigFile("items.yml");
        Map<String, Integer> missiles = new HashMap<>();
        for (String key : items.getKeys(false)) {
            if (items.contains(key + ".1.tnt")) {
                int amount = items.getConfigurationSection(key).getKeys(false).size() - 1;
                missiles.put(key, amount);
            }
        }
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> spawnMissileRandomly(missiles), 100L);
    }
    
    @Override
    protected void startTeams() {
        // Literally place everyone on blue
        for (MissileWarsPlayer player : players) {
            if (!spectators.contains(player)) {
                blueTeam.addPlayer(player);
            }
        }

        // Send messages
        redTeam.distributeGear();
        blueTeam.distributeGear();
        redTeam.scheduleDeckItems();
        blueTeam.scheduleDeckItems();
    }
    
    @Override
    protected void calculateStats(MissileWarsTeam winningTeam) {
        for (MissileWarsPlayer player : players) {
            player.getMCPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }
    
    // Spawn a missile in a random location at red base
    private void spawnMissileRandomly(Map<String, Integer> missiles) {
        if (!running) {
            return;
        }
        
        int players = blueTeam.getSize();
        if (players > 0) {
            int x1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.x1");
            int x2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.x2");
            int y1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.y1");
            int y2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.y2");
            int z = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.z1");
            
            Random random = new Random();
            int x = random.nextInt(x1, x2 + 1);
            int y = random.nextInt(y1, y2 + 1);
            
            List<String> m = new ArrayList<>(missiles.keySet());
            String missile = m.get(random.nextInt(m.size()));
            int level = random.nextInt(missiles.get(missile)) + 1;
            Location loc = new Location(getWorld(), x, y, z);
            SchematicManager.spawnNBTStructure(null, missile + "-" + level, loc, true, mapName, true, false);
        }
        
        // Take an average time to spawn next random missile
        FileConfiguration settings = MissileWarsPlugin.getPlugin().getConfig();
        int time = settings.getInt("item-frequency." + Math.max(1, Math.min(players, 3)));
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> spawnMissileRandomly(missiles), (20 * time * 8) / 5);
    }
}
