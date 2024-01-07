package com.leomelonseeds.missilewars.arenas;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.SchematicManager;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import net.kyori.adventure.text.Component;

public class TutorialArena extends ClassicArena {
    
    private Map<UUID, Integer> stage;
    private Map<UUID, Location> xs;
    private Map<UUID, BukkitTask> particles;
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
        this.justReset = true;
        
        // Wait 5 seconds for the world to load. then start
        Bukkit.getScheduler().runTaskLater(plugin, () -> start(), 100);
        
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
                stopTrackers();
                resetWorld();
                init();
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
    public void enqueue(UUID uuid, String team, boolean force) {
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
        // Cancel if player isn't in the arena anymore
        if (!player.getWorld().getName().equals(getWorld().getName())) {
            return;
        }
        
        // Cancel if player not on correct stage
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        UUID uuid = player.getUniqueId();
        if (stage.get(uuid) != s) {
            return;
        }

        ConfigUtils.sendTitle("stage" + s, player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> ConfigUtils.sendConfigMessage("messages.stage" + s, player, null, null), 100);
        if (s == 0) {
            ConfigUtils.sendConfigSound("stagecomplete", player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                stage.put(uuid, 1);
                initiateStage(player, 1);
            }, 160);
            return;
        } 

        ConfigUtils.sendConfigSound("stage", player);
        
        // Do some additional work if stage 4 is present
        if (s != 4) {
            return;
        }

        if (!ConfigUtils.inShield(this, player.getLocation(), "blue", 2)) {
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
                player.spawnParticle(Particle.REDSTONE, x, yneg, Z, 2, dustOptions);
                player.spawnParticle(Particle.REDSTONE, x, ypos, Z, 2, dustOptions);
                yneg += 0.25;
                ypos -= 0.25;
            }
        }, 10, 5));
        
        String deck = getPlayerInArena(player.getUniqueId()).getDeck().getName();
        if (deck.equals("Berserker") || deck.equals("Vanguard")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> ConfigUtils.sendConfigMessage("messages.wrong-tutorial-deck", player, null, null), 100);
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

        // End tutorial if stage 6 passes
        ConfigUtils.sendTitle("stagecomplete", player);
        if (s == 6) {
            stage.put(uuid, 7);
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
        if (!xs.containsKey(uuid)) {
            return;
        }
        
        xs.remove(uuid);
        particles.get(uuid).cancel();
        particles.remove(uuid);
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
        if (!broketeam.registerPortalBreak(location, false)) {
            return;
        }
        
        // Reset map after 5 sec
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            SchematicManager.spawnFAWESchematic("default-map", getWorld(), gamemode, null);
        }, 100);
        
        // Check if has associated player
        Player player;
        if (entity instanceof Player) {
            player = (Player) entity;
        } else {
            player = ConfigUtils.getAssociatedPlayer(entity, this);
        }
        
        Component msg = CosmeticUtils.getPortalMessage(player, broketeam.getName());
        for (MissileWarsPlayer mwPlayer : players) {
            mwPlayer.getMCPlayer().sendMessage(msg);
        }
        
        // Check if team still has living portals
        if (player == null || broketeam == blueTeam) {
            return;
        }
        
        ConfigUtils.sendConfigSound("enemy-portal-destroyed", player);
        registerStageCompletion(player, 3);
    }
    
    /**
     * Remove a player with a given UUID from the arena.
     *
     * @param uuid the UUID of the player
     */
    @Override
    public void removePlayer(UUID uuid, Boolean tolobby) {
        // Remove player from all teams and queues
        MissileWarsPlayer toRemove = getPlayerInArena(uuid);
        players.remove(toRemove);
        voteManager.removePlayer(toRemove.getMCPlayer());
        spectators.remove(toRemove);
        blueTeam.removePlayer(toRemove);

        // Run proper clearing commands on the player
        if (tolobby) {
            Arena arena = this;
            Player mcPlayer = toRemove.getMCPlayer();
            mcPlayer.teleport(ConfigUtils.getSpawnLocation());
            mcPlayer.setGameMode(GameMode.ADVENTURE);
            mcPlayer.setHealth(20);
            InventoryUtils.loadInventory(mcPlayer);
            ConfigUtils.sendConfigMessage("messages.leave-arena", mcPlayer, arena, null);
            RankUtils.setPlayerExpBar(mcPlayer);

            // Notify discord
            TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
            discordChannel.sendMessage(":arrow_forward: " + mcPlayer.getName() + " rejoined lobby from arena " + arena.getName()).queue();

            for (Player player : Bukkit.getWorld("world").getPlayers()) {
                ConfigUtils.sendConfigMessage("messages.leave-arena-lobby", player, null, mcPlayer);
            }
            
            // Remove tutorial stuff
            removeXs(uuid);
        }
    }
    
    @Override
    public void applyMultipliers() {
        blueTeam.setMultiplier(1 / 12.0);
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
