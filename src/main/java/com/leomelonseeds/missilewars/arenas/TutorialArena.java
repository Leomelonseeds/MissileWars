package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.SchematicManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public class TutorialArena extends ClassicArena {
    
    public static int MAX_STAGES = 8;
    
    private Map<UUID, Integer> stage;
    private Map<UUID, Location> xs;
    private Map<UUID, BukkitTask> particles;
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
        this.xs = new HashMap<>();
        this.particles = new HashMap<>();
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
    
    public Integer getStage(UUID uuid) {
        return stage.get(uuid);
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
    
    @Override
    public Location getPlayerSpawn(Player player) {
        Integer s = stage.get(player.getUniqueId());
        if (blueTeam == null || s == null || s != 3 || !blueTeam.containsPlayer(player.getUniqueId())) {
            return super.getPlayerSpawn(player);
        }
        
        // Spawn player near blue base, adding a platform and clearing blocks if necessary
        double xspawn = (new Random()).nextInt(-15, 16) + 0.5;
        Location loc = new Location(getWorld(), xspawn, 17, 42.5, 0, 0);
        loc.getBlock().setType(Material.AIR);
        loc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        Location spawnBlock = loc.clone().add(0, -1, 0);
        for (Location l : new Location[] {
            spawnBlock,
            spawnBlock.clone().add(1, 0, 0),
            spawnBlock.clone().add(-1, 0, 0),
            spawnBlock.clone().add(0, 0, 1),
            spawnBlock.clone().add(0, 0, -1),
        }) {
            l.getBlock().setType(Material.BLUE_STAINED_GLASS);
        }
        
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
    
    private void sendTutorialTitle(String path, Player player, boolean init) {
        // Find titles and subtitles from config
        FileConfiguration msg = ConfigUtils.getConfigFile("messages.yml");
        if (!msg.contains("titles.tutorial." + path)) {
            ConfigUtils.sendTitle(path, player);
            return;
        }
        
        String title = msg.getString("titles.tutorial." + (init ? "new-objective" : "remind"));
        String subtitle = msg.getString("titles.tutorial." + path);
        subtitle += msg.getString("titles.tutorial.subadd");
        int dur = msg.getInt("titles.tutorial.length") * 50;
        
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(dur), Duration.ofMillis(1000));
        Title finalTitle = Title.title(ConfigUtils.toComponent(title), ConfigUtils.toComponent(subtitle), times);
        player.showTitle(finalTitle);
    }
    
    // Activates bossbar, chat, and title of a stage
    private void initiateStage(Player player, int s) {
        // Cancel if player isn't in the arena anymore
        if (!isInArena(player.getUniqueId())) {
            return;
        }
        
        // Cancel if player not on correct stage
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        UUID uuid = player.getUniqueId();
        if (stage.get(uuid) != s) {
            return;
        }

        // Title task: Send titles once every 20 seconds to make sure players on right track
        sendTutorialTitle("stage" + s, player, true);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isInArena(player.getUniqueId()) && stage.get(uuid) == s) {
                    sendTutorialTitle("stage" + s, player, false);
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 500, 500);
        
        // Bukkit.getScheduler().runTaskLater(plugin, () -> ConfigUtils.sendConfigMessage("messages.stage" + s, player, null, null), 50);
        if (s == 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> 
                ConfigUtils.sendConfigMessage("messages.stage0", player, null, null), 50);
            ConfigUtils.sendConfigSound("stagecomplete", player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                stage.put(uuid, 1);
                initiateStage(player, 1);
            }, 160);
            return;
        } 

        ConfigUtils.sendConfigSound("stage", player);
        
        // Teleport player nearer red base on stage 3
        if (s == 3) {
            player.teleport(getPlayerSpawn(player));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 128, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 6, true, false));
            redTeam.setPortalGlow(getPlayerInArena(uuid), true);
            hasGlow.add(uuid);
            return;
        }
        
        // Spawn particles for stage 4 (defense)
        if (s == 4) {
            if (!ArenaUtils.inShield(this, player.getLocation(), TeamName.BLUE, 2)) {
                player.teleport(getPlayerSpawn(player));
            }
            
            // Give player location to throw shield at if stage 4
            Random random = new Random();
            final double X = random.nextInt(-15, 16) + 0.5;
            final double Y = 13.5;
            final double Z = -24.5;
            xs.put(uuid, new Location(getWorld(), X, Y, Z));
            DustOptions dustOptions = new DustOptions(Color.FUCHSIA, 1.5F);
            particles.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                double yneg = Y - 1;
                double ypos = Y + 1;
                for (double x = X - 1; x <= X + 1; x += 0.25) {
                    player.spawnParticle(Particle.DUST, x, yneg, Z, 2, dustOptions);
                    player.spawnParticle(Particle.DUST, x, ypos, Z, 2, dustOptions);
                    yneg += 0.25;
                    ypos -= 0.25;
                }
            }, 10, 5));
            
            DeckStorage deck = DeckStorage.fromString(plugin.getJSON().getPlayer(uuid).getString("Deck"));
            if (deck == DeckStorage.BERSERKER || deck == DeckStorage.VANGUARD) {
                ConfigUtils.schedule(100, () -> ConfigUtils.sendConfigMessage("messages.wrong-tutorial-deck", player, null, null));
            }
            return;
        }
        
        // Spawn particles above NPCs
        if (s == 5 || s == 6) {
            FileConfiguration schematicConfig = ConfigUtils.getConfigFile("maps.yml");
            Vector vec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos.berserker", null, null);
            Location loc = new Location(getWorld(), vec.getX(), vec.getY() + 1, vec.getZ());
            particles.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                player.spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.4, 0.4, 0.4);
            }, 0, 20));
        }

        // Close inventory for stage 6 + 7 so people can see the instructions
        if (s == 6 || s == 7) {
            player.closeInventory();
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
        
        Bukkit.getLogger().info(player.getName() + " completed stage " + s);

        ConfigUtils.sendTitle("stagecomplete", player);
        if (s == 3) {
            redTeam.setPortalGlow(getPlayerInArena(uuid), false);
            hasGlow.remove(uuid);
        } else if (s == MAX_STAGES - 1) {
            stage.put(uuid, MAX_STAGES);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ConfigUtils.sendConfigMessage("messages.tutorial-complete", player, null, null);
                stage.remove(uuid);
                removePlayer(uuid, true);
            }, 60);
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
    
    // Remove the Xs for obsidian shield throw
    private void removeXs(UUID uuid) {
        if (xs.containsKey(uuid)) {
            xs.remove(uuid);
        }
        
        if (particles.containsKey(uuid)) {
            particles.remove(uuid).cancel();
        }
    }
    
    /**
     * Register placing a throwable projectile 
     * 
     * @param location
     * @param player
     */
    public void registerProjectilePlacement(Location location, Player player) {
        UUID uuid = player.getUniqueId();
        if (stage.get(uuid) != 4) {
            return;
        }
        
        Location loc = xs.get(uuid);
        if (loc.distance(location) > 3) {
            return;
        }
        
        registerStageCompletion(player, 4);
        removeXs(player.getUniqueId());
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
        removeXs(uuid);
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
