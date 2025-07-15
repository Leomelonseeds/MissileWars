package com.leomelonseeds.missilewars.utilities.cinematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.cinematic.TutorialReplay.Type;

public class CinematicManager implements Listener {
    
    private Map<Player, ArmorStand> cache;
    private Map<Integer, BukkitTask> bukkitTasks;
    private Map<Integer, Consumer<Player>> replayTasks;
    private Map<TutorialReplay.Type, TutorialReplay> replays;
    private Set<Player> justChangedWorld;
    private List<Location> frames;
    private int startingFrame = 0;
    int nextTaskId;
    
    public CinematicManager() {
        this.cache = new HashMap<>();
        this.bukkitTasks = new HashMap<>();
        this.replayTasks = new HashMap<>();
        this.replays = new HashMap<>();
        this.justChangedWorld = new HashSet<>();
        this.frames = new ArrayList<>();
        init();
    }
    
    public void setStartingFrame(int frame) {
        this.startingFrame = frame;
    }
    
    
    /**
     * Load a hardcoded path into frames
     */
    public void init() {
        frames.clear();
        World world = Bukkit.getWorld("world");
        World tutorial = Bukkit.getWorld("mwarena_tutorial");
        if (world == null || tutorial == null) {
            return;
        }
        
        // Initiate bot replays
        replays.put(Type.RIDING, new RidingReplay(tutorial));
        replays.put(Type.PORTAL_BREAKING, new PortalReplay(tutorial));
        replays.put(Type.DEFENDING, new DefendingReplay(tutorial));
        replays.put(Type.ABILITIES, new AbilitiesReplay(tutorial));
        
        // Initiate spectator armorstand keyframes
        List<Pair<Integer, Location>> keyframes = new ArrayList<>();
        
        // Scene 1 - missile silo
        keyframes.add(Pair.of(0,   new Location(world, 170.5, 71, 113.5, 0f, 0f)));
        keyframes.add(Pair.of(88,  new Location(world, 170.5, 71, 180.5, 0f, 0f)));
        
        // Scene 2 - new map work area
        keyframes.add(Pair.of(89,  new Location(world, 50.5, 158, -113.5, -90f, 0f)));
        keyframes.add(Pair.of(160, new Location(world, 100.5, 158, -113.5, -90f, 0f)));
        
        // Scene 3 - Lobby decks
        keyframes.add(Pair.of(161, new Location(world, 174.5, 66, 70.5, -180f, 0f)));
        keyframes.add(Pair.of(201, new Location(world, 190.5, 66, 70.5, -180f, 0f)));
        
        // Scene 4 - Lobby play NPCs
        keyframes.add(Pair.of(202, new Location(world, 166.5, 62, 94.5, -90f, 0f)));
        keyframes.add(Pair.of(248, new Location(world, 166.5, 62, 84.5, -90f, 0f)));
        
        // Scene 5 - Zoom into to lobby missile wars logo
        keyframes.add(Pair.of(249, new Location(world, 192.5, 76, 89.5, 90f, 0f)));
        keyframes.add(Pair.of(340, new Location(world, 112.5, 76, 89.5, 90f, 0f)));
        
        // Scene 6 - Zoom out from tutorial world missile wars logo
        keyframes.add(Pair.of(341, new Location(tutorial, 55, 0.7, 0.5, -90f, 0f)));
        keyframes.add(Pair.of(390, new Location(tutorial, 1, 0.7, 0.5, -90f, 0f)));
        keyframes.add(Pair.of(452, new Location(tutorial, -88, 0.7, 0.5, -90f, 0f)));
        
        // Scene 7 - Replay 1 - riding missiles
        keyframes.add(Pair.of(453, new Location(tutorial, 163, 17, -64.5, -37f, 9f)));
        keyframes.add(Pair.of(501, new Location(tutorial, 163, 17, -55.5, -37f, 9f)));
        keyframes.add(Pair.of(541, new Location(tutorial, 166.5, 16, -47.5, 0f, 14f)));
        
        // Scene 8 - Replay 2 - blowing up portals
        keyframes.add(Pair.of(565, new Location(tutorial, 172.5, 14.5, 50, 130f, 8f)));
        keyframes.add(Pair.of(620, new Location(tutorial, 172.5, 14.5, 50, 80f, 8f)));
        keyframes.add(Pair.of(650, new Location(tutorial, 172.5, 14.5, 50, 80f, 8f)));
        keyframes.add(Pair.of(700, new Location(tutorial, 170, 10, 88, 163f, 16f)));
        keyframes.add(Pair.of(780, new Location(tutorial, 170, 10, 88, 163f, 16f)));
        
        // Scene 9 - Replay 3 - Defending
        keyframes.add(Pair.of(790, new Location(tutorial, 133.5, 17, 55.5, 160f, 12f)));
        keyframes.add(Pair.of(820, new Location(tutorial, 134, 17, 53, 138f, 12.5f)));
        keyframes.add(Pair.of(840, new Location(tutorial, 141.5, 11.5, 19.5, 65f, 19f)));
        keyframes.add(Pair.of(880, new Location(tutorial, 138, 11.5, 13, 32f, 21f)));
        
        // Scene 10 - Replay 4 - Abilities
        keyframes.add(Pair.of(900, new Location(tutorial, 131, 17, -55, -21f, 4f)));
        keyframes.add(Pair.of(960, new Location(tutorial, 134, 17, -54.5, -2f, 4f)));
        keyframes.add(Pair.of(1020, new Location(tutorial, 134, 17, -54.5, -2f, 4f)));
        
        // Scene 11 - Decks
        keyframes.add(Pair.of(1021, new Location(tutorial, 74, -3.5, 7.5, 90f, -5f)));
        keyframes.add(Pair.of(1100, new Location(tutorial, 74, -3.5, -6.5, 90f, -5f)));
        generateFrames(keyframes);
        
        // Initiate replay tasks
        replayTasks.put(1, p -> {
            ConfigUtils.sendTitle("cinematic-1", p);
            ConfigUtils.sendConfigSound("intro-1", p, true);
        });
        replayTasks.put(90, p -> ConfigUtils.sendTitle("cinematic-2", p));
        replayTasks.put(162, p -> ConfigUtils.sendTitle("cinematic-3", p));
        replayTasks.put(203, p -> ConfigUtils.sendTitle("cinematic-4", p));
        replayTasks.put(342, p -> ConfigUtils.sendConfigSound("intro-2", p, true));
        replayTasks.put(454, p -> {
            ConfigUtils.sendTitle("cinematic-5", p);
            replays.get(Type.RIDING).startReplay();
        });
        replayTasks.put(566, p -> replays.get(Type.PORTAL_BREAKING).startReplay());
        replayTasks.put(580, p -> ConfigUtils.sendTitle("cinematic-6", p));
        replayTasks.put(670, p -> ConfigUtils.sendTitle("cinematic-7", p));
        replayTasks.put(785, p -> replays.get(Type.DEFENDING).startReplay());
        replayTasks.put(790, p -> ConfigUtils.sendTitle("cinematic-8", p));
        replayTasks.put(900, p -> replays.get(Type.ABILITIES).startReplay());
        replayTasks.put(970, p -> ConfigUtils.sendTitle("cinematic-9", p));
        replayTasks.put(1022, p -> ConfigUtils.sendTitle("cinematic-10", p));
        replayTasks.put(1060, p -> ConfigUtils.sendTitle("cinematic-11", p));
    }
    
    /**
     * Play the cinematic for the player by spawning an armor stand, setting
     * the player to spectator mode, and teleporting the armor stand every tick
     * while having the player spectate it.
     * 
     * @param player
     */
    public void play(Player player) {
        if (cache.containsKey(player) || frames.isEmpty()) {
            return;
        }
        
        // Load chunks in second area
        player.getWorld().getChunksAtAsync(2, -13, 17, -2, true, () -> {});
        
        // Create armor stand and let player spectate it
        player.setGameMode(GameMode.SPECTATOR);
        createArmorStand(frames.get(0), player);
        int id = nextTaskId++;
        bukkitTasks.put(id, new BukkitRunnable() {
            
            int frameNum = startingFrame;
            boolean paused = false;
            boolean worldChange = false;
            int worldChangeFrames = 0;
            
            @Override
            public void run() {
                if (paused) {
                    return;
                }
                
                // World change handler. After event registration, wait
                // a bit more in paused state for player to load in as well
                ArmorStand curAS = cache.get(player);
                if (worldChange) {
                    if (!justChangedWorld.contains(player)) {
                        worldChangeFrames++;
                        return;
                    }
                    
                    justChangedWorld.remove(player);
                    worldChange = false;
                    paused = true;
                    ConfigUtils.schedule(Math.max(8, 20 - worldChangeFrames), () -> {
                        createArmorStand(player.getLocation(), player);
                        paused = false;
                    });
                    worldChangeFrames = 0;
                    return;
                }

                frameNum++;
                if (frameNum >= frames.size() || !player.isOnline()) {
                    curAS.remove();
                    cache.remove(player);
                    bukkitTasks.remove(id);
                    this.cancel();
                    return;
                }
                
                if (replayTasks.containsKey(frameNum)) {
                    replayTasks.get(frameNum).accept(player);
                }
                 
                Location curFrame = frames.get(frameNum).clone();
                Location playerLoc = player.getLocation();
                boolean sameWorld = playerLoc.getWorld().equals(curFrame.getWorld());
                double distSquared = sameWorld ? curFrame.distanceSquared(playerLoc) : Integer.MAX_VALUE;
                if (!sameWorld || distSquared > 20 * 20) {
                    // Teleport player to the current frame and set gamemode back to spectator in case world forces gamemode
                    player.teleport(curFrame);
                    player.setGameMode(GameMode.SPECTATOR);
                    curAS.remove();
                    
                    // Add delay if the world is the same, otherwise use special 
                    // world change handler above that waits for event registration
                    if (sameWorld) {
                        paused = true;
                        int delay = (distSquared > 40 * 40 || frameNum == 1) ? 8 : 1;
                        ConfigUtils.schedule(delay, () -> {
                            createArmorStand(curFrame, player);
                            paused = false;
                        });
                    } else {
                        worldChange = true;
                    }
                } else {
                    curAS.teleport(curFrame);
                }
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 1, 1));
    }
    
    /**
     * Creates an armor stand and forces the player to spectate it, adding it to the cache.
     * Requires player to be in spectator mode, make sure that's set :)
     * 
     * @param loc
     * @param player
     */
    private void createArmorStand(Location loc, Player player) {
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setGravity(false);
        as.setInvisible(true);
        as.setInvulnerable(true);
        cache.put(player, as);
        player.setSpectatorTarget(as);
    }
    
    
    /**
     * Takes in a list of keyframes and interpolates them as frames for movement
     * of an armor stand. It first generates every frame necessary, then creates
     * a repeating BukkitTask to teleport the armor stand each tick.
     * 
     * Frames should be given as a list of locations, and the tick timestamp
     * at which the location occurs. The timestamps should start at 0 and should
     * always be in order from least to greatest, with no repeating values. If the
     * world changes, the subsequent keyframes can start from 0 again.
     * 
     * Cosine interpolation from https://paulbourke.net/miscellaneous/interpolation/
     * 
     * @param armorStand
     * @param frames
     */
    private void generateFrames(List<Pair<Integer, Location>> keyframes) {
        Pair<Integer, Location> lastKeyframe = null;
        for (Pair<Integer, Location> keyframe : keyframes) {
            if (lastKeyframe == null) {
                lastKeyframe = keyframe;
                frames.add(keyframe.getRight());
                continue;
            }
            
            // If worlds are different, you can't interpolate, so just add the keyframe
            Location initLoc = lastKeyframe.getRight();
            Location destLoc = keyframe.getRight();
            if (!initLoc.getWorld().equals(destLoc.getWorld())) {
                lastKeyframe = keyframe;
                frames.add(destLoc);
                continue;
            }
            
            // Load locations into arrays for per-value interpolation
            int length = keyframe.getLeft() - lastKeyframe.getLeft();
            double[] init = { initLoc.getX(), initLoc.getY(), initLoc.getZ(), initLoc.getYaw(), initLoc.getPitch() };
            double[] dest = { destLoc.getX(), destLoc.getY(), destLoc.getZ(), destLoc.getYaw(), destLoc.getPitch() };
            
            // Yaw values jump from 180 to -180. If the difference is larger than 180 one of them must be negative.
            // Take the smaller of the two values and add 360 to it to normalize the difference.
            if (Math.abs(init[3] - dest[3]) > 180) {
                if (init[3] > dest[3]) {
                    dest[3] += 360;
                } else {
                    init[3] += 360;
                }
            }
            
            // Generate frames equal to the length of the keyframe
            for (int i = 0; i < length; i++) {
                double[] frame = new double[5];
                double mu = (i + 1) / (double) length;
                // mu = (1 - Math.cos(mu * Math.PI)) / 2;
                for (int j = 0; j < 5; j++) {
                    frame[j] = init[j] * (1 - mu) + dest[j] * mu;
                }

                // Load the finalized frame into a location using destination world
                frames.add(new Location(destLoc.getWorld(), frame[0], frame[1], frame[2], (float) frame[3], (float) frame[4]));
            }
            
            lastKeyframe = keyframe;
        }
    }
    
    // Stops players being able to teleport to people in spectator mode
    @EventHandler
    private void onTeleport(PlayerTeleportEvent event) {
        if (cache.containsKey(event.getPlayer()) && event.getCause() == TeleportCause.SPECTATE) {
            event.setCancelled(true);
        }
    }
    
    // Stop players from being able to exit the spectator target
    @EventHandler
    private void onShift(PlayerToggleSneakEvent event) {
        if (cache.containsKey(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
    
    // Stop players from being able to move
    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (cache.containsKey(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    private void onWorldChange(PlayerChangedWorldEvent event) {
        justChangedWorld.add(event.getPlayer());
        ConfigUtils.schedule(200, () -> justChangedWorld.remove(event.getPlayer()));
    }
    
    /**
     * Check if a player is watching the cinematic
     * 
     * @param player
     */
    public boolean isWatching(Player player) {
        return cache.containsKey(player);
    }
    
    public void disable() {
        cache.values().forEach(as -> as.remove());
    }
}
