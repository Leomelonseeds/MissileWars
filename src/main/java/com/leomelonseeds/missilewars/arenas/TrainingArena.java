package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.tracker.Tracked;
import com.leomelonseeds.missilewars.utilities.tracker.TrackedMissile;

public class TrainingArena extends Arena {
    
    public TrainingArena() {
        super("training", 100);
    }

    public TrainingArena(Map<String, Object> serializedArena) {
        super(serializedArena);
    }

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
    
    // Post-join actions that may differ by arena
    @Override
    protected void postJoin(Player player, boolean asSpectator) { 
        // Check for AFK
        ConfigUtils.sendConfigMessage("messages.joined-training", player, null, null);
        
        // Auto-join team if setting turned on
        if (!player.hasPermission("umw.disableautoteam") && running) {
            enqueue(player.getUniqueId(), "blue");
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
        Bukkit.getScheduler().runTaskLaterAsynchronously(MissileWarsPlugin.getPlugin(), () -> spawnMissile(missiles), 40L);
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
    protected void calculateStats(MissileWarsTeam winningTeam) {}
    
    // Spawn a missile based on a few conditions at the red base
    private void spawnMissile(Map<String, Integer> missiles) {
        if (!running) {
            return;
        }

        // Schedule next iteration later if nobody online
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        int playercount = blueTeam.getSize();
        if (playercount <= 0) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> spawnMissile(missiles), 10 * 20L);
            return;
        }
        
        // Get maximum ranked player
        int maxLevel = 0;
        for (MissileWarsPlayer player : players) {
            int level = RankUtils.getRankLevel(plugin.getSQL().getExpSync(player.getMCPlayerId()));
            if (level > maxLevel) {
                maxLevel = level;
            }
        }

        // Get shield coordinates
        int x1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.x1");
        int x2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.x2");
        int y1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.y1");
        int y2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.y2");
        int z = (int) ConfigUtils.getMapNumber(gamemode, mapName, "red-shield.z1");
        
        // Find random missile
        Random random = new Random();
        List<String> m = new ArrayList<>(missiles.keySet());
        String missile = m.get(random.nextInt(m.size()));
        int level = random.nextInt(missiles.get(missile)) + 1;

        // Spawn missile randomly, with bias towards top of shield
        int x = random.nextInt(x1, x2 + 1);
        int y = random.nextDouble() > 0.5 ? y2 : random.nextInt(y1 + 4, y2 + 1);
        Location loc = new Location(getWorld(), x, y, z);
        
        // Just a code block that I can break out of :)
        do {
            // The higher level you are, the higher chance of defense, starting at 50%
            if (random.nextDouble() > 0.5 + maxLevel * 0.05) {
                break;
            }
            
            // Find closest missile to red base
            Collection<Tracked> tracked = tracker.getMissiles();
            TrackedMissile toDefend = null; 
            int maxtz = 0; // Larger z = closer to red base
            for (Tracked t : tracked) {
                if (!(t instanceof TrackedMissile)) {
                    continue;
                }
                
                if (t.isRed()) {
                    continue;
                }
                
                int tz = Math.max(t.getPos1().getBlockZ(), t.getPos2().getBlockZ());
                if (tz > maxtz) {
                    maxtz = z;
                    toDefend = (TrackedMissile) t;
                }
            }
            
            // No need to worry about defending if opponent is far off, or no blue missiles at all
            if (maxtz <= 0 || toDefend == null) {
                break;
            }
            
            // Determine theoretical best location to spawn missile then adjust to actual limits
            int spawnx = (toDefend.getPos1().getBlockX() + toDefend.getPos2().getBlockX()) / 2;
            int spawny = Math.max(toDefend.getPos1().getBlockY(), toDefend.getPos2().getBlockY()) + 5;
            spawnx = Math.max(Math.min(spawnx, x2), x1);
            spawny = Math.max(Math.min(spawny, y2), y1);
            loc.set(spawnx, spawny, z);
        } while (false);
        
        // Spawn missile
        Bukkit.getScheduler().runTask(plugin, () -> SchematicManager.spawnNBTStructure(null, missile + "-" + level, loc, true, mapName, true, false));
        
        // Take an average time to spawn next random missile
        FileConfiguration settings = MissileWarsPlugin.getPlugin().getConfig();
        int time = settings.getInt("item-frequency." + Math.max(1, Math.min(playercount, 3)));
        
        // Adjust for tick, divide by num players to simulate equal teams.
        // Do not simulate missile randomness because that's boring
        // Subtract one second for each level of the highest levelled player
        int interval = Math.max(2, (time / Math.max(playercount, 1)) - maxLevel);
        Bukkit.getScheduler().runTaskLaterAsynchronously(MissileWarsPlugin.getPlugin(), () -> spawnMissile(missiles), interval * 20L);
    }
}
