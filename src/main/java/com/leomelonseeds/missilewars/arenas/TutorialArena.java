package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public class TutorialArena extends ClassicArena {
    
    // The number of stages in the tutorial + 1
    // Last stage is for completion
    public static final int MAX_STAGES = 5;
    
    private Map<UUID, Integer> stage;
    private Set<Player> stage4Disabled;
    private Map<UUID, Location> attackLocs;
    private Set<UUID> hasGlow;
    private boolean justReset;
    
    public TutorialArena() {
        super("tutorial", 100);
        init();
    }

    public TutorialArena(Map<String, Object> serializedArena) {
        super(serializedArena);
        init();
    }
    
    private void init() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        this.stage = new HashMap<>();
        this.stage4Disabled = new HashSet<>();
        this.attackLocs = new HashMap<>();
        this.hasGlow = new HashSet<>();
        this.justReset = true;
        voteManager.addVote("default-map", 64);
        ConfigUtils.schedule(1, () -> {
            start();
            
            // Spawn another default map for replays
            World world = getWorld();
            SchematicManager.spawnFAWESchematic("tutorial-replay", world);
            
            // Spawn a barrier wall for the replay map
            FileConfiguration settings = plugin.getConfig();
            int length = settings.getInt("barrier.length");
            int x = 99;
            int zCenter = settings.getInt("barrier.center.z");
            for (int y = 0; y <= 320; ++y) {
                for (int z = zCenter - length / 2; z < zCenter + length / 2; z++) {
                    world.getBlockAt(x, y, z).setType(Material.BARRIER);
                }
            }
        });
        
        // World reset task
        int minute = 20 * 60;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (justReset) {
                    return;
                }
                
                if (!players.isEmpty()) {
                    return;
                }
                
                plugin.log("Resetting tutorial arena...");
                running = false;
                resetting = true;
                cancelTasks();
                resetWorld(r -> init());
                this.cancel();
            }
        }.runTaskTimer(plugin, minute, minute);
    }
    
    /**
     * Get the stage of a player. Returns -1 if the player isn't in any stage
     * 
     * @param uuid
     * @return
     */
    public Integer getStage(UUID uuid) {
        return stage.getOrDefault(uuid, -1);
    }

    @Override
    protected void postJoin(Player player, boolean asSpectator) { 
        UUID uuid = player.getUniqueId();
        if (!stage.containsKey(uuid)) {
            stage.put(uuid, 0);
        }
        enqueue(uuid, "blue");
        initiateStage(player, stage.get(uuid));
        justReset = false;
    }
    
    /**
     * Mark the player in the arena but don't give him anything, use for cinematic entry
     * 
     * @param player
     */
    public void softJoin(Player player) {
        players.put(player.getUniqueId(), new MissileWarsPlayer(player.getUniqueId()));
    }
    
    @Override
    public Location getPlayerSpawn(Player player) {
        UUID uuid = player.getUniqueId();
        Integer s = stage.get(uuid);
        if (blueTeam == null || s == null || s != 3 || !blueTeam.containsPlayer(uuid)) {
            return super.getPlayerSpawn(player);
        }
        
        // Spawn player near blue base, adding a platform and clearing blocks if necessary
        Location loc = attackLocs.get(uuid);
        if (loc == null) {
            double xspawn = getRandomLane() + 0.5;
            loc = new Location(getWorld(), xspawn, 17, 25.5, 0, 0);
            attackLocs.put(uuid, loc);
        }
        
        loc.getBlock().setType(Material.AIR);
        loc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        Location spawnBlock = loc.clone().add(0, -1, 0);
        SchematicManager.spawnNBTStructure(player, "platform-2", spawnBlock, false, false, false);
        return loc;
    }
    
    @Override
    public void enqueue(UUID uuid, String team, boolean force) {
        MissileWarsPlayer player = players.get(uuid);
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
    }
    
    private void sendTutorialTitle(String path, Player player) {
        // Find titles and subtitles from config
        FileConfiguration msg = ConfigUtils.getConfigFile("messages.yml");
        if (!msg.contains("titles.tutorial." + path)) {
            ConfigUtils.sendTitle(path, player);
            return;
        }
        
        String title = msg.getString("titles.tutorial.new-objective");
        String subtitle = msg.getString("titles.tutorial." + path);
        // subtitle += msg.getString("titles.tutorial.subadd");
        int dur = msg.getInt("titles.tutorial.length") * 50;
        
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(dur), Duration.ofMillis(1000));
        Title finalTitle = Title.title(ConfigUtils.toComponent(title), ConfigUtils.toComponent(subtitle), times);
        player.showTitle(finalTitle);
    }
    
    // Activates bossbar, chat, and title of a stage
    private void initiateStage(Player player, int s) {
        // Cancel if player isn't in the arena anymore
        UUID uuid = player.getUniqueId();
        if (!isInArena(uuid)) {
            return;
        }
        
        // Cancel if player not on correct stage
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        if (stage.get(uuid) != s) {
            return;
        }

        // Bukkit.getScheduler().runTaskLater(plugin, () -> ConfigUtils.sendConfigMessage("messages.stage" + s, player, null, null), 50);
        sendTutorialTitle("stage" + s, player);
        
        // Stage-specific tasks
        if (s == 0) {
            ConfigUtils.sendConfigSound("stagecomplete", player);
            ConfigUtils.schedule(80, () -> ConfigUtils.sendTitle("stage0b", player));
            ConfigUtils.schedule(140, () -> ConfigUtils.sendTitle("stage0c", player));
            ConfigUtils.schedule(200, () -> {
                stage.put(uuid, 1);
                initiateStage(player, 1);
            });
            return;
        } else {
            
        }
        
        Deck playerDeck = getPlayerInArena(uuid).getDeck();
        if (s == 1 && playerDeck != null) {
            playerDeck.disableItems(i -> !playerDeck.getMissiles().contains(i));
        }

        ConfigUtils.sendConfigSound("stage", player);
        
        // Teleport player nearer red base on stage 3
        if (s == 3) {
            player.teleport(getPlayerSpawn(player));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 6, true, false));
            redTeam.setPortalGlow(getPlayerInArena(uuid), true);
            hasGlow.add(uuid);
            return;
        }
        
        // Spawn missile for stage 4 (defense)
        if (s == 4) {
            if (!ArenaUtils.inShield(this, player.getLocation(), TeamName.BLUE, 2)) {
                player.teleport(getPlayerSpawn(player));
            }

            // Disable all items except the ones to be used (assuming Sentinel)
            if (playerDeck != null) {
                playerDeck.enableAllItems();
                playerDeck.disableItems(i -> 
                    i.getType().toString().contains("SPAWN_EGG") || 
                    i.getType().toString().contains("BOW") ||
                    i.getType() == Material.ARROW);
            }
            
            // Spawn the attacking missile
            spawnAttackingMissile(player);
            
            // Warn players if they are using the wrong deck
            DeckStorage deck = DeckStorage.fromString(plugin.getJSON().getPlayer(uuid).getString("Deck"));
            if (deck == DeckStorage.VANGUARD) {
                ConfigUtils.schedule(100, () -> ConfigUtils.sendConfigMessage("messages.wrong-tutorial-deck", player, null, null));
            }
            return;
        }
    }
    
    /**
     * Completes stage s for the player. If the player is also
     * currently on stage s, the stage will be incremented.
     * 
     * @param player
     * @param s
     */
    public void registerStageCompletion(Player player, int s) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        UUID uuid = player.getUniqueId();
        Integer actualStage = stage.get(uuid);
        if (actualStage == null || s != actualStage) {
            return;
        }
        
        if (s == 4 && stage4Disabled.contains(player)) {
            return;
        }
        
        Bukkit.getLogger().info(player.getName() + " completed stage " + s);

        ConfigUtils.sendTitle("stagecomplete", player);
        if (s == 3) {
            redTeam.setPortalGlow(getPlayerInArena(uuid), false);
            hasGlow.remove(uuid);
            attackLocs.remove(uuid);
            stage4Disabled.add(player);
            ConfigUtils.schedule(200, () -> stage4Disabled.remove(player));
        } else if (s == MAX_STAGES - 1) {
            stage.put(uuid, MAX_STAGES);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ConfigUtils.sendConfigMessage("messages.tutorial-complete", player, null, null);
                stage.remove(uuid);
                removePlayer(uuid, true);
            }, 100);
            return;
        }

        stage.put(uuid, s + 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            initiateStage(player, s + 1);
        }, 60);
    }
    
    /**
     * Skip the current stage
     * 
     * @param player
     */
    public void registerStageSkip(Player player) {
        UUID uuid = player.getUniqueId();
        int s = stage.get(uuid);
        if (s != 3) {
            ConfigUtils.sendConfigMessage("messages.stage-skip-fail", player, null, null);
            return;
        }
        
        if (!blueTeam.containsPlayer(uuid)) {
            ConfigUtils.sendConfigMessage("messages.stage-skip-fail-game", player, null, null);
            return;
        }
        
        stage.put(uuid, s + 1);
        initiateStage(player, s + 1);
    }
    
    /**
     * Spawn a missile that the player can defend against
     */
    private void spawnAttackingMissile(Player player) {
        boolean foundLane = false;
        Location loc1 = null;
        Location loc2 = null;
        World world = getWorld();
        
        // Make up to 10 attempts to find a valid lane to spawn a missile in
        // A lane is valid simply if no players are detected in it
        for (int i = 0; i < 10; i++) {
            int testLane = getRandomLane();
            loc1 = new Location(world, testLane - 2, 17, 15);
            loc2 = new Location(world, testLane + 2, 11, -47);
            if (world.getNearbyEntities(
                    BoundingBox.of(loc1, loc2), 
                    e -> e.getType() == EntityType.PLAYER)
                .isEmpty()) {
                foundLane = true;
                break;
            }
        }
        
        // Spawn another missile/try again if this player hasn't completed in 30 seconds
        ConfigUtils.schedule(20 * 30, () -> {
           if (getStage(player.getUniqueId()) == 4) {
               spawnAttackingMissile(player);
           }
        });
        
        // It would be crazy if this actually happened
        if (!foundLane) {
            return;
        }
        
        // Clear the lane and then spawn a missile
        Location spawnLoc = loc1.clone().add(2, 0, 0);
        SchematicManager.setAirAsync(
                loc1.getBlockX(), loc1.getBlockY(), loc1.getBlockZ(), 
                loc2.getBlockX(), loc2.getBlockY(), loc2.getBlockZ(),
                world, o -> {
            SchematicManager.spawnNBTStructure(null, "warhead-2", spawnLoc, true, true, false);
        });
    }
    
    /**
     * Gets a random x-coord to use as a lane for a player
     * 
     * @return
     */
    private int getRandomLane() {
        return new Random().nextInt(-15, 16);
    }
    
    @Override
    public void registerPortalBreak(Location location, Entity entity) {
        // Check if portal broke at blue or red z
        MissileWarsTeam broketeam = location.getBlockZ() > 0 ? redTeam : blueTeam;
        
        // Check if portal break was registered
        if (!broketeam.registerPortalBreak(location, 5)) {
            return;
        }
        
        // Reset map after 5 sec
        ConfigUtils.schedule(100, () -> SchematicManager.spawnFAWESchematic("default-map", getWorld(), gamemode, null));
        ConfigUtils.schedule(140, () -> {
            redTeam.destroyPortalGlow(true);
            hasGlow.forEach(uid -> redTeam.setPortalGlow(getPlayerInArena(uid), true));
        });
        
        // Check if has associated player
        Player player;
        if (entity instanceof Player) {
            player = (Player) entity;
        } else {
            player = ArenaUtils.getAssociatedPlayer(entity, this);
        }
        
        Component msg = CosmeticUtils.getPortalMessage(player, broketeam.getName());
        for (MissileWarsPlayer mwPlayer : players.values()) {
            mwPlayer.getMCPlayer().sendMessage(msg);
        }
        
        // Check if team still has living portals
        if (player == null || broketeam == blueTeam) {
            return;
        }
        
        ConfigUtils.sendConfigSound("enemy-portal-destroyed", player);
        registerStageCompletion(player, 3);
    }
    
    @Override
    public void removePlayer(UUID uuid, Boolean tolobby) {
        redTeam.setPortalGlow(getPlayerInArena(uuid), false);
        hasGlow.remove(uuid);
        super.removePlayer(uuid, tolobby);
    }
    
    @Override
    public void applyMultipliers() {
        blueTeam.setMultiplier(1 / 12.0);
    }
    
    @Override
    protected void startTeams() {
        // Literally place everyone on blue
        queueCount = 0;
        for (MissileWarsPlayer player : players.values()) {
            if (spectators.contains(player)) {
                continue;
            }
            queueCount++;
            blueTeam.addPlayer(player);
        }
    }
    
    @Override
    protected void glowPortals(MissileWarsTeam team) {}
    
    @Override
    protected void glowPortals(MissileWarsTeam team, MissileWarsPlayer player) {}
    
    // No need to track who left the arena here
    @Override
    public void addLeft(UUID uuid) {}
    
    @Override
    public void performTimeSetup() {}
    
    @Override
    protected void calculateStats(MissileWarsTeam winningTeam) {}
    
    @Override
    protected void autoEnd() {}
}
