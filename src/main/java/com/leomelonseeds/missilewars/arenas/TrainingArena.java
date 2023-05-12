package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.tracker.Tracked;
import com.leomelonseeds.missilewars.utilities.tracker.TrackedMissile;

public class TrainingArena extends Arena {
    
    boolean hasTurret;
    
    public TrainingArena() {
        super("training", 100);
        hasTurret = false;
    }

    public TrainingArena(Map<String, Object> serializedArena) {
        super(serializedArena);
        hasTurret = false;
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
                if (blueTeam.containsPlayer(uuid)) {
                    return;
                }
                
                removeSpectator(player);
                redTeam.removePlayer(player);
                blueTeam.addPlayer(player);
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
    
    // Reset training arena after 30 min
    @Override
    protected void autoEnd() {
        if (blueTeam.getSize() <= 0) {
            autoEnd = new BukkitRunnable() {
                @Override
                public void run() {
                    if (running && blueTeam.getSize() <= 0) {
                        endGame(null);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), 30 * 60 * 20L);
        }
    }
    
    @Override
    public void checkNotEmpty() {
        if (!MissileWarsPlugin.getPlugin().isEnabled()) {
            return;
        }
        
        if (!running || redTeam == null || blueTeam == null) {
            return;
        }
        
        if (blueTeam.getSize() <= 0) {
            return;
        }
        
        if (autoEnd == null) {
            return;
        }
        
        autoEnd.cancel();
    }
    
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
        queueCount = 0;
        for (MissileWarsPlayer player : players) {
            if (spectators.contains(player)) {
                continue;
            }
            queueCount++;
            blueTeam.addPlayer(player);
        }
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
        
        // Get maximum ranked and distanced player
        int maxLevel = 0;
        for (MissileWarsPlayer player : blueTeam.getMembers()) {
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
        World world = getWorld();
        Location loc = null;
        
        // Setup various collections
        Collection<Tracked> tracked = tracker.getMissiles();
        Set<Tracked> blueMissiles = new HashSet<>();
        Set<Tracked> redMissiles = new HashSet<>();
        for (Tracked t : tracked) {
            if (!(t instanceof TrackedMissile)) {
                continue;
            }
            
            if (!t.isRed()) {
                blueMissiles.add(t);
            } else if (((TrackedMissile) t).isInMotion()) {
                redMissiles.add(t);
            }
        }
        
        // Just a code block that I can break out of :)
        boolean spawnedTurret = false;
        do {
            // Find closest missile to red base
            Tracked toDefend = null; 
            int maxtz = 0; // Larger z = closer to red base
            for (Tracked t : blueMissiles) {
                int tz = Math.max(t.getPos1().getBlockZ(), t.getPos2().getBlockZ());
                if (tz > maxtz) {
                    maxtz = tz;
                    toDefend = t;
                }
            }
            
            // Find closest player to red base
            Location closestPlayer = null;
            int maxpz = 0;
            for (MissileWarsPlayer player : blueTeam.getMembers()) {
                Location mcpl = player.getMCPlayer().getLocation();
                if (mcpl.getBlockZ() > maxpz) {
                    maxpz = mcpl.getBlockZ();
                    closestPlayer = mcpl;
                }
            }
            
            // No need to worry about defending if opponent is far off, or no blue missiles at all
            int max = Math.max(maxpz, maxtz);
            if (max == 0) {
                break;
            }
            
            // If maxpz close to base and within trajectory of an arrow turret, spawn one.
            // Otherwise, random chance for training arena to spawn a fireball turret.
            // Trajectory of arrow turret: -(z-49)^2 / 50 within certain offset of player y, where
            // player x is within bounds of x1 + 3 and x2 - 3
            if (!hasTurret && maxpz > 0) {
                double cpz = closestPlayer.getZ();
                double cpy = closestPlayer.getY();
                double cpx = closestPlayer.getX();
                int spawnx = Math.max(Math.min(closestPlayer.getBlockX(), x2), x1);
                double esty = -1 * Math.pow(cpz - 45, 2) / 50 + 30;
                if (Math.abs(esty - cpy) <= 5) {
                    // Spawn arrow turret if player is within range of a spawner, despawn 10 sec later
                    Location tloc = new Location(world, spawnx, y2 + 1, z);
                    Bukkit.getScheduler().runTask(plugin, () -> SchematicManager.spawnNBTStructure(null, "turret-0", tloc, false, mapName, false, false));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        SchematicManager.spawnNBTStructure(null, "turret_clear-0", tloc, false, mapName, false, false);
                        hasTurret = false;
                    }, 20 * 10L);
                } else if (cpz < z - 10 && (random.nextDouble() < 0.33 || cpy < y1-5 || cpy > y2+2 || cpx < x1-5 || cpx > x2+5)) {
                    // Otherwise, if 33% chance or if player cannot be nuked by missile, spawn a fireball turret at 0, 17, 51, with fire location at 0, 22, 51 
                    Location tloc = new Location(world, 0, y2 + 1, z);
                    Bukkit.getScheduler().runTask(plugin, () -> SchematicManager.spawnNBTStructure(null, "fireball_turret-0", tloc, false, mapName, false, false));

                    // Fireball 3 sec after loading turret
                    int flevel = Math.min(maxLevel, 3);
                    Location pfloc = closestPlayer.add(0, -2, 5);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Location floc = new Location(world, 0, 22, z);
                        Vector fvec = pfloc.toVector().subtract(floc.toVector());
                        Fireball fireball = (Fireball) world.spawnEntity(floc, EntityType.FIREBALL);
                        fireball.setIsIncendiary(true);
                        fireball.setYield(flevel);
                        fireball.setDirection(fvec);
                        ConfigUtils.sendConfigSound("spawn-fireball", floc);
                    }, 20 * 3L);
                    
                    // Despawn turret
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        SchematicManager.spawnNBTStructure(null, "fireball_turret_clear-0", tloc, false, mapName, false, false);
                        hasTurret = false;
                    }, 20 * 6L);
                } else {
                    break;
                }
                
                hasTurret = true;
                spawnedTurret = true;
                break;
            }
            

            // Determine theoretical best location to spawn missile then adjust to actual limits
            // Add a small bias onto player detection so defending against riders are prioritized
            int spawnx;
            int spawny;
            if (maxpz + 8 >= maxtz && closestPlayer != null) {
                spawnx = closestPlayer.getBlockX();
                spawny = closestPlayer.getBlockY() + 6;
            } else {
                spawnx = (toDefend.getPos1().getBlockX() + toDefend.getPos2().getBlockX()) / 2;
                spawny = Math.max(toDefend.getPos1().getBlockY(), toDefend.getPos2().getBlockY()) + 4;
            }
            
            spawnx = Math.max(Math.min(spawnx, x2), x1);
            spawny = Math.max(Math.min(spawny, y2), y1);
            Location defloc = new Location(world, spawnx, spawny, z);
            
            // Make sure it doesn't collide with an existing missile if player not within nuking range
            if (wouldCollide(defloc, redMissiles, max, 1) && maxpz < z - 20) {
                break;
            }
            
            loc = defloc;
        } while (false);
        
        // If a turret was not spawned, spawn a missile
        if (!spawnedTurret) {
            // If all else fails, we just pick a random location
            if (loc == null) {
                // If portals destroyed, target the other portal
                FileConfiguration maps = ConfigUtils.getConfigFile("maps.yml");
                Location p1 = SchematicManager.getVector(maps, "portals.1", gamemode, mapName).toLocation(world);
                Location p2 = SchematicManager.getVector(maps, "portals.2", gamemode, mapName).toLocation(world);
                if (p1.getBlock().getType() != Material.NETHER_PORTAL) {
                    x1 += 15; // p1 is negative x portal, so target positive X by increasing X
                } else if (p2.getBlock().getType() != Material.NETHER_PORTAL) {
                    x2 -= 15; // and vice versa
                }
                
                // Try at most a constant number of times to increase performance
                int count = 0;
                do {
                    int x = random.nextInt(x1, x2 + 1);
                    int y = random.nextDouble() > 0.5 ? y2 : random.nextInt(y1 + 4, y2 + 1);
                    loc = new Location(world, x, y, z);
                    count++;
                } while (wouldCollide(loc, redMissiles, 0, 2) && count < 5);
            }
            
            // Spawn missile
            Location floc = loc;
            Bukkit.getScheduler().runTask(plugin, () -> SchematicManager.spawnNBTStructure(null, missile + "-" + level, floc, true, mapName, true, false));    
        }
        
        // Take an average time to spawn next random missile and spawn
        // Adjust for tick, divide by num players to simulate equal teams.
        // Do not simulate missile randomness because that's boring
        // Subtract one second for each level of the highest levelled player
        int time = plugin.getConfig().getInt("default-cooldown") / 5;
        int interval = Math.max(2, (time / Math.max(playercount, 1)) - maxLevel);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> spawnMissile(missiles), interval * 20L);
    }
    
    // Algorithm to check if at a certain location, there already is a missile heading towards enemy base
    // Higher radius = less conditions for missiles to spawn
    private boolean wouldCollide(Location spawnloc, Set<Tracked> redMissiles, int minz, int rad) {
        for (int z = spawnloc.getBlockZ() - 4; z >= minz; z -= 7) {
            int x = spawnloc.getBlockX();
            int y = spawnloc.getBlockY() - 5;
            World world = getWorld();
            Location check1 = new Location(world, x, y, z);
            Location check2 = new Location(world, x + rad, y + rad, z);
            Location check3 = new Location(world, x - rad, y - rad, z);
            for (Tracked t : redMissiles) {
                if (t.contains(check1) || t.contains(check2) || t.contains(check3)) {
                    return true;
                }
            }
        }
        return false;
    }
}
