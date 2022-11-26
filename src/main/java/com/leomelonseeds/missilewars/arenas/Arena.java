package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.earth2me.essentials.Essentials;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.schematics.VoidChunkGenerator;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.SQLManager;
import com.leomelonseeds.missilewars.utilities.tracker.Tracker;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.github.a5h73y.parkour.Parkour;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.exception.NPCLoadException;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;

/** Represents a MissileWarsArena where the game will be played. */
public class Arena implements ConfigurationSerializable {

    /** Comparator to sort by capacity */
    public static Comparator<Arena> byCapacity = Comparator.comparing(a -> a.getCapacity());
    /** Comparator to sort by capacity */
    public static Comparator<Arena> byPlayers = Comparator.comparing(a -> a.getNumPlayers());
    /** The arena name. */
    protected String name;
    /** The map for the arena. */
    protected String mapName;
    /** The gamemode type for the map for the arena. */
    protected String gamemode;
    /** The max number of players for this arena. */
    protected int capacity;
    /** The ids of the NPCs */
    protected List<Integer> npcs;
    /** The list of all players currently in the arena. */
    protected Set<MissileWarsPlayer> players;
    /** The list of all spectators currently in the arena. */
    protected Set<MissileWarsPlayer> spectators;
    /** The queue to join the red team. Active before the game. */
    protected Queue<MissileWarsPlayer> redQueue;
    /** The queue to join the blue team. Active before the game. */
    protected Queue<MissileWarsPlayer> blueQueue;
    /** The red team. */
    protected MissileWarsTeam redTeam;
    /** The blue team. */
    protected MissileWarsTeam blueTeam;
    /** The start time of the game. In the future if game is yet to start, otherwise, in the past. */
    protected LocalDateTime startTime;
    /** Whether a game is currently running */
    protected boolean running;
    /** Are we waiting for a tie or no */
    protected boolean waitingForTie;
    /** List of currently running tasks. */
    protected List<BukkitTask> tasks;
    /** Whether the arena is currently resetting the world. */
    protected boolean resetting;
    /** A task for if we are waiting for a game to auto-end */
    protected BukkitTask autoEnd;
    /** The tracker for all missiles and utilities */
    protected Tracker tracker;
    /** The vote manager for this arena */
    protected VoteManager voteManager;

    /**
     * Create a new Arena with a given name and max capacity.
     *
     * @param name the name
     * @param capacity the max capacity
     */
    public Arena(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        this.gamemode = "classic";
        players = new HashSet<>();
        spectators = new HashSet<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
        tasks = new LinkedList<>();
        npcs = new ArrayList<>();
        tracker = new Tracker();
        voteManager = new VoteManager(this);
    }

    /**
     * Serialize the Arena for yml storage.
     *
     * @return a map mapping the variables as string to their objects
     */
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> serializedArena = new HashMap<>();
        serializedArena.put("name", name);
        serializedArena.put("capacity", capacity);
        serializedArena.put("gamemode", gamemode);
        List<String> npcStrings = new ArrayList<>();
        for (int i : npcs) {
            npcStrings.add(Integer.toString(i));
        }
        serializedArena.put("npc", String.join(",", npcStrings));
        return serializedArena;
    }

    /**
     * Constructor from a serialized Arena.
     *
     * @param serializedArena the yml serialized Arena
     */
    public Arena(Map<String, Object> serializedArena) {
        name = (String) serializedArena.get("name");
        capacity = (int) serializedArena.get("capacity");
        gamemode = (String) serializedArena.get("gamemode");
        npcs = new ArrayList<>();
        String npcIDs = (String) serializedArena.get("npc");
        for (String s : npcIDs.split(",")) {
            npcs.add(Integer.parseInt(s));
        }
        players = new HashSet<>();
        spectators = new HashSet<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
        tasks = new LinkedList<>();
        tracker = new Tracker();
        voteManager = new VoteManager(this);
    }
    
    public void unregisterTeams() {
        if (redTeam != null && blueTeam != null) {
            redTeam.unregisterTeam();
            blueTeam.unregisterTeam();
        }
    }
    
    /**
     * Gets the arena's missile/utility tracker
     * 
     * @return
     */
    public Tracker getTracker() {
        return tracker;
    }
    
    /**
     * Stops all async tasks from trackers
     */
    public void stopTrackers() {
        tracker.stopAll();
    }

    public void addNPC(int id) {
        npcs.add(id);
    }

    public List<Integer> getNPCs() {
        return npcs;
    }
    
    public VoteManager getVoteManager() {
        return voteManager;
    }

    /**
     * Get the name of the Arena.
     *
     * @return the name of the Arena
     */
    public String getName() {
        return name;
    }

    /**
     * Get the World for this Arena.
     *
     * @return the world this Arena lies in
     */
    public World getWorld() {
        return Bukkit.getWorld("mwarena_" + name);
    }

    /**
     * Get the current map for this Arena.
     *
     * @return the selected map name for this Arena
     */
    public String getMapName() {
        return mapName;
    }

    /**
     * Get the gamemode for this arena.
     *
     * @return the current map type for this arena
     */
    public String getGamemode() {
        return gamemode;
    }

    /**
     * Check if the game is currently running.
     *
     * @return whether a game is currently running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Check if the arena is resetting.
     *
     * @return whether the arena world is resetting
     */
    public boolean isResetting() {
        return resetting;
    }
    
    /**
     * Check if game is waiting for a tie
     * 
     * @return
     */
    public boolean isWaitingForTie() {
        return waitingForTie;
    }

    /**
     * Get the number of seconds remaining when chaos time activates.
     *
     * @return the number of seconds remaining when chaos time activates
     */
    public static int getChaosTime() {
        return MissileWarsPlugin.getPlugin().getConfig().getInt("chaos-mode.time-left") * 60;
    }

    /**
     * Get people in red queue
     *
     * @return
     */
    public int getRedQueue() {
        return redQueue.size();
    }

    /**
     * Get people in blue queue
     *
     * @return
     */
    public int getBlueQueue() {
        return blueQueue.size();
    }

    /**
     * Get the red team
     *
     * @return
     */
    public MissileWarsTeam getRedTeam() {
        return redTeam;
    }

    /**
     * Get the blue team
     *
     * @return
     */
    public MissileWarsTeam getBlueTeam() {
        return blueTeam;
    }
    
    /**
     * Get all players that the arena thinks are participating
     * as Player objects
     * 
     * @return
     */
    public Set<Player> getPlayers() {
        Set<Player> result = new HashSet<>();
        for (MissileWarsPlayer mwp : players) {
            result.add(mwp.getMCPlayer());
        }
        return result;
    }

    /**
     * Get a {@link MissileWarsPlayer} in this arena from a given UUID.
     *
     * @param uuid the Player's UUID
     * @return the {@link MissileWarsPlayer} with the given UUID in this Arena, otherwise null
     */
    public MissileWarsPlayer getPlayerInArena(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Get the team a player with a given UUID is on.
     *
     * @param uuid the UUID to check for
     * @return the team that the player with uuid is on
     */
    public String getTeam(UUID uuid) {
        if (redTeam == null || blueTeam == null) {
            return "no team";
        }
        if (redTeam.containsPlayer(uuid)) {
            return "red";
        }
        if (blueTeam.containsPlayer(uuid)) {
            return "blue";
        }
        return "no team";
    }

    /**
     * Get the position in queue of a certain player with a given UUID.
     *
     * @param uuid the uuid to check for
     * @return the position in queue of a certain player with a given UUID or -1 if they are not in a queue
     */
    public int getPositionInQueue(UUID uuid) {
        // Check red queue
        int pos = 1;
        for (MissileWarsPlayer player : redQueue) {
            if (player.getMCPlayerId().equals(uuid)) {
                return pos;
            }
            pos++;
        }

        // Check blue queue
        pos = 1;
        for (MissileWarsPlayer player : blueQueue) {
            if (player.getMCPlayerId().equals(uuid)) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    /**
     * Get the number of seconds until the game starts.
     *
     * @return the number of seconds until the game starts
     */
    public long getSecondsUntilStart() {
        // Ensure arena is set to start
        if (startTime == null) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(), startTime).toSeconds();
    }

    /**
     * Get the number of seconds remaining in the game.
     *
     * @return the number of seconds remaining in the game
     */
    public long getSecondsRemaining() {
        if (startTime == null) {
            return 0;
        }
        int totalSecs = MissileWarsPlugin.getPlugin().getConfig().getInt("game-length") * 60;
        long secsTaken = Duration.between(startTime, LocalDateTime.now()).toSeconds();
        return totalSecs - secsTaken;
    }

    /**
     * Gets the time remaining
     *
     * @return a formatted time with mm:ss
     */
    public String getTimeRemaining() {
        // Adjust for correct timings
        int seconds = Math.max((int) getSecondsRemaining() - 1, 0);
        return String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60);
    }

    /**
     * Get the number of player currently in the game.
     *
     * @return the number of player currently in the game
     */
    public int getNumPlayers() {
        if (blueTeam == null || blueTeam.getSize() == 0) {
            return players.size() - spectators.size();
        }
        return redTeam.getSize() + blueTeam.getSize();
    }

    /**
     * Get total number of players in the arena
     *
     * @return the number of player currently in the game
     */
    public int getTotalPlayers() {
        return players.size();
    }

    /**
     * Get the max number of players allowed in the game.
     *
     * @return the max number of players allowed in the game
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Join a player without setting them to spectator mode
     * 
     * @param player
     * @return
     */
    public boolean joinPlayer(Player player) {
        return joinPlayer(player, false);
    }

    /**
     * Attempt to add a player to the Arena.
     *
     * @param player the player
     * @param asSpectator whether the player should be set to spectator mode
     * @return true if the player joined the Arena, otherwise false
     */
    public boolean joinPlayer(Player player, boolean asSpectator) {

        // Ensure world isn't resetting
        if (resetting) {
            ConfigUtils.sendConfigMessage("messages.arena-full", player, this, null);
            return false;
        }
        
        // Ensure player can play >:D
        if (player.hasPermission("umw.new")) {
            ConfigUtils.sendConfigMessage("messages.watch-the-fucking-video", player, this, null);
            return false;
        }

        // Make sure player not in parkour
        if (Parkour.getInstance().getParkourSessionManager().isPlayingParkourCourse(player)) {
            ConfigUtils.sendConfigMessage("messages.leave-parkour", player, this, null);
            return false;
        }
        
        // Save inventory if player in world
        if (player.getWorld().getName().equals("world")) {
            InventoryUtils.saveInventory(player, true);
        }

        player.teleport(getPlayerSpawn(player));
        InventoryUtils.clearInventory(player);
        ConfigUtils.sendConfigMessage("messages.join-arena", player, this, null);

        for (MissileWarsPlayer mwPlayer : players) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-others", mwPlayer.getMCPlayer(), null, player);
        }

        ConfigUtils.sendConfigMessage("messages.joined-arena", player, this, null);
        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        discordChannel.sendMessage(":arrow_backward: " + player.getName() + " left and joined arena " + this.getName()).queue();

        player.setHealth(20);
        player.setFoodLevel(20);
        players.add(new MissileWarsPlayer(player.getUniqueId()));
        player.setBedSpawnLocation(getPlayerSpawn(player), true);
        player.setGameMode(GameMode.ADVENTURE);

        for (Player worldPlayer : Bukkit.getWorld("world").getPlayers()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-lobby", worldPlayer, this, player);
        }
        
        // Check for AFK
        Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess.getUser(player).isAfk()) {
            addSpectator(player.getUniqueId());
            ConfigUtils.sendConfigMessage("messages.afk-spectator", player, null, null);
        }
        
        // Auto spectate if asSpectator
        else if (asSpectator) {
            addSpectator(player.getUniqueId());
        }
        
        // Auto-join team if setting turned on
        else if (!player.hasPermission("umw.disableautoteam") && running) {
            if (getRedTeam().getSize() < getBlueTeam().getSize()) {
                enqueueRed(player.getUniqueId());
            } else {
                enqueueBlue(player.getUniqueId());
            }
        }

        // Check for game start
        checkForStart();
        return true;
    }
    
    /**
     * Checks if the game is ready to auto-start
     */
    public void checkForStart() {
        if (running || resetting) {
            return;
        }
        int minPlayers = MissileWarsPlugin.getPlugin().getConfig().getInt("minimum-players");
        if (getNumPlayers() >= minPlayers) {
            scheduleStart();
        }
    }
    
    /**
     * Equivalent to starting with the default timer
     */
    public void scheduleStart() {
        scheduleStart(MissileWarsPlugin.getPlugin().getConfig().getInt("lobby-wait-time"));
    }
    
    /**
     * Check if a player with a given UUID is in this Arena.
     *
     * @param uuid the UUID of the player
     * @return true if the player is in this Arena, otherwise false
     */
    public boolean isInArena(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a player with a given UUID from the arena.
     *
     * @param uuid the UUID of the player
     */
    public void removePlayer(UUID uuid, Boolean tolobby) {
        // Remove player from all teams and queues
        MissileWarsPlayer toRemove = new MissileWarsPlayer(uuid);
        players.remove(toRemove);
        voteManager.removePlayer(toRemove.getMCPlayer());

        for (MissileWarsPlayer mwPlayer : players) {
            ConfigUtils.sendConfigMessage("messages.leave-arena-others", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
        }

        // Cancel tasks if starting and below min players
        int minPlayers = MissileWarsPlugin.getPlugin().getConfig().getInt("minimum-players");
        if (!running && startTime != null && getNumPlayers() < minPlayers) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
            startTime = null;
        }

        toRemove.getMCPlayer().setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        spectators.remove(toRemove);
        blueQueue.remove(toRemove);
        redQueue.remove(toRemove);
        if (redTeam != null) {
            redTeam.removePlayer(toRemove);
            blueTeam.removePlayer(toRemove);
        }

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
        }

        checkEmpty();

    }

    /**
     * Warp player back to the waiting lobby
     *
     * @param uuid the UUID of player
     */
    public boolean leaveGame(UUID uuid) {

        MissileWarsPlayer toRemove = new MissileWarsPlayer(uuid);
        Player player = Bukkit.getPlayer(uuid);

        if (!isRunning() || redTeam == null || blueTeam == null) {
            removePlayer(player.getUniqueId(), true);
            return true;
        }

        if (redTeam.containsPlayer(uuid)) {
            redTeam.removePlayer(toRemove);
            for (MissileWarsPlayer mwPlayer : players) {
                ConfigUtils.sendConfigMessage("messages.leave-team-red", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
            }
        } else if (blueTeam.containsPlayer(uuid)) {
            blueTeam.removePlayer(toRemove);
            for (MissileWarsPlayer mwPlayer : players) {
                ConfigUtils.sendConfigMessage("messages.leave-team-blue", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
            }
        } else {
            removePlayer(player.getUniqueId(), true);
            return true;
        }

        checkEmpty();

        player.teleport(getPlayerSpawn(player));
        player.setGameMode(GameMode.ADVENTURE);

        return true;
    }
    
    /**
     * Checks if the game is not empty, and cancels game end task if so
     */
    public void checkNotEmpty() {
        if (!MissileWarsPlugin.getPlugin().isEnabled()) {
            return;
        }
        
        if (!running || redTeam == null || blueTeam == null) {
            return;
        }
        
        if (redTeam.getSize() <= 0 || blueTeam.getSize() <= 0) {
            return;
        }
        
        if (autoEnd == null) {
            return;
        }
        
        autoEnd.cancel();
    }

    /**
     * Checks if the game is empty, and ends game if so
     */
    public void checkEmpty() {

        if (!MissileWarsPlugin.getPlugin().isEnabled()) {
            return;
        }

        if (!running || redTeam == null || blueTeam == null) {
            return;
        }
        
        if (redTeam.getSize() <= 0 && blueTeam.getSize() <= 0) {
            endGame(null);
        } else if (redTeam.getSize() <= 0) {
            announceMessage("messages.red-team-empty", null);
            autoEnd = new BukkitRunnable() {
                @Override
                public void run() {
                    if (running && redTeam.getSize() <= 0) {
                        endGame(blueTeam);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), 60 * 20L);
        } else if (blueTeam.getSize() <= 0) {
            announceMessage("messages.blue-team-empty", null);
            autoEnd = new BukkitRunnable() {
                @Override
                public void run() {
                    if (running && blueTeam.getSize() <= 0) {
                        endGame(redTeam);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), 60 * 20L);
        }
    }

    /**
     * Check if a player is spectating.
     *
     * @param player the player
     * @return true if player is spectating otherwise false
     */
    public boolean isSpectating(MissileWarsPlayer player) {
        return spectators.contains(player);
    }

    /**
     * Enqueue a player with a given UUID to the red team.
     *
     * @param uuid the Player's UUID
     */
    public void enqueueRed(UUID uuid) {
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
                    if (!redQueue.contains(player)) {
                        if (redQueue.size() >= getCapacity() / 2) {
                            ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                        } else {
                            blueQueue.remove(player);
                            redQueue.add(player);
                            ConfigUtils.sendConfigMessage("messages.queue-waiting-red", player.getMCPlayer(), this, null);
                            removeSpectator(player);
                        }
                    } else {
                        redQueue.remove(player);
                        ConfigUtils.sendConfigMessage("messages.queue-leave-red", player.getMCPlayer(), this, null);
                    }
                } else {
                    if (!player.getMCPlayer().isOp() && redTeam.getSize() - blueTeam.getSize() >= 1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-error", player.getMCPlayer(), this, null);
                    } else if (!player.getMCPlayer().hasPermission("umw.joinfull") && redTeam.getSize() >= getCapacity() / 2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                    } else {
                        removeSpectator(player);
                        blueTeam.removePlayer(player);
                        redTeam.addPlayer(player);
                        redTeam.giveItems(player);
                        player.giveDeckGear();
                        checkNotEmpty();
                        announceMessage("messages.queue-join-red", player);
                    }
                }
                break;
            }
        }
    }

    /**
     * Enqueue a player with a given UUID to the blue team.
     *
     * @param uuid the Player's UUID
     */
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
                    if (!blueQueue.contains(player)) {
                        if (blueQueue.size() >= getCapacity() / 2) {
                            ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                        } else {
                            redQueue.remove(player);
                            blueQueue.add(player);
                            ConfigUtils.sendConfigMessage("messages.queue-waiting-blue", player.getMCPlayer(), this, null);
                            removeSpectator(player);
                        }
                    } else {
                        blueQueue.remove(player);
                        ConfigUtils.sendConfigMessage("messages.queue-leave-blue", player.getMCPlayer(), this, null);
                    }
                } else {
                    if (!player.getMCPlayer().isOp() && blueTeam.getSize() - redTeam.getSize() >= 1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-error", player.getMCPlayer(), this, null);
                    } else if (!player.getMCPlayer().hasPermission("umw.joinfull") && blueTeam.getSize() >= getCapacity() / 2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                    } else {
                        removeSpectator(player);
                        redTeam.removePlayer(player);
                        blueTeam.addPlayer(player);
                        blueTeam.giveItems(player);
                        player.giveDeckGear();
                        checkNotEmpty();
                        announceMessage("messages.queue-join-blue", player);
                    }
                }
                break;
            }
        }
    }

    /**
     * Remove the given player from the spectators list.
     *
     * @param player the player
     */
    public void removeSpectator(MissileWarsPlayer player) {
        if (spectators.remove(player)) {
            announceMessage("messages.spectate-leave-others", player);
            player.getMCPlayer().setGameMode(GameMode.ADVENTURE);
            player.getMCPlayer().teleport(getPlayerSpawn(player.getMCPlayer()));
            checkForStart();
        }
    }

    /**
     * Add the player with a given UUID to the spectators.
     *
     * @param uuid the UUID of the player
     */
    public void addSpectator(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                if (!(running || resetting) || getTeam(uuid).equals("no team")) {
                    announceMessage("messages.spectate-join-others", player);
                    spectators.add(player);
                    redQueue.remove(player);
                    blueQueue.remove(player);
                    
                    // Cancel tasks if starting and below min players
                    int minPlayers = MissileWarsPlugin.getPlugin().getConfig().getInt("minimum-players");
                    if (!running && startTime != null && getNumPlayers() < minPlayers) {
                        for (BukkitTask task : tasks) {
                            task.cancel();
                        }
                        startTime = null;
                    }
                    
                    Player mcPlayer = player.getMCPlayer();
                    mcPlayer.setGameMode(GameMode.SPECTATOR);
                    mcPlayer.sendActionBar(Component.text("Type /spectate to stop spectating"));
                } else {
                    player.getMCPlayer().sendMessage(ConfigUtils.getConfigText("messages.spectate-join-fail",
                            player.getMCPlayer(), null, null));
                }
                break;
            }
        }
    }

    /** Schedule the start of the game based on the config time. */
    public void scheduleStart(int secCountdown) {
    	MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();

        // Schedule the start of the game if not already running
        if (startTime == null) {

            // Respawns citizens if they are not present
            if (getWorld().getEntityCount() - spectators.size() < 9) {
                try {
                    ((Citizens) CitizensAPI.getPlugin()).reload();
                } catch (NPCLoadException e) {
                    Bukkit.getLogger().log(Level.WARNING, "Citizens in " + getWorld().getName() + " couldn't be reloaded.");
                }
            }

            startTime = LocalDateTime.now().plusSeconds(secCountdown);
            String startMsg = "messages.lobby-countdown-start";
            announceMessage(startMsg, null);
            tasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    if (!start()) {
                        announceMessage("messages.start-failed", null);
                    }
                }
            }.runTaskLater(plugin, secCountdown * 20));

            // Schedule 30-second countdown
            int cdNear = plugin.getConfig().getInt("lobby-countdown-near");
            for (int secInCd = secCountdown; secInCd > 0; secInCd--) {
                int finalSecInCd = secInCd;
                tasks.add(new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (finalSecInCd <= cdNear) {
                            String startMsg = "messages.lobby-countdown-near";
                            announceMessage(startMsg, null);
                        }
                        setXpLevel(finalSecInCd);
                    }
                }.runTaskLater(plugin, (secCountdown - secInCd) * 20));
            }
        }
    }

    /**
     * Starts a game in the arena with the classic arena. Different gamemodes and maps coming soon.
     *
     * @return true if the game started. Otherwise false
     */
    public boolean start() {
        if (running) {
            return false;
        }

        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();

        // Select Map
        mapName = voteManager.getVotedMap();

        // Generate map.
        announceMessage("messages.starting", null);
        return SchematicManager.spawnFAWESchematic(mapName, getWorld(), gamemode, result -> {
            // Result will only run if map loading is a success
            // Acquire red and blue spawns
            FileConfiguration mapConfig = ConfigUtils.getConfigFile("maps.yml");
            Vector blueSpawnVec = SchematicManager.getVector(mapConfig, "blue-spawn", gamemode, mapName);
            Location blueSpawn = new Location(getWorld(), blueSpawnVec.getX(), blueSpawnVec.getY(), blueSpawnVec.getZ());
            Vector redSpawnVec = SchematicManager.getVector(mapConfig, "red-spawn", gamemode, mapName);
            Location redSpawn = new Location(getWorld(), redSpawnVec.getX(), redSpawnVec.getY(), redSpawnVec.getZ());
            redSpawn.setYaw(180);
            blueSpawn.setWorld(getWorld());
            redSpawn.setWorld(getWorld());

            // Setup scoreboard and teams
            blueTeam = new MissileWarsTeam("blue", this, blueSpawn);
            redTeam = new MissileWarsTeam("red", this, redSpawn);
            
            // Setup game timers
            // Game start
            startTime = LocalDateTime.now();
            long gameLength = getSecondsRemaining();
            performGamemodeSetup();

            // Chaos time start
            int chaosStart = getChaosTime();
            tasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    blueTeam.setChaosMode(true);
                    redTeam.setChaosMode(true);
                    blueTeam.sendTitle("chaos-mode");
                    redTeam.sendTitle("chaos-mode");
                    announceMessage("messages.chaos-mode", null);
                }
            }.runTaskLater(plugin, (gameLength - chaosStart) * 20));

            // Game is 1800 seconds long.
            int[] reminderTimes = {600, 1500, 1740, 1770, 1790, 1795, 1796, 1797, 1798, 1799};

            for (int i : reminderTimes) {
                tasks.add(new BukkitRunnable() {
                    @Override
                    public void run() {
                        announceMessage("messages.game-end-reminder", null);
                    }
                }.runTaskLater(plugin, i * 20));
            }
            
            // Teleport all players to center to remove lobby minigame items/dismount
            for (MissileWarsPlayer player : players) {
                player.getMCPlayer().teleport(getPlayerSpawn(player.getMCPlayer()));
                player.getMCPlayer().closeInventory();
            }
            
            // Register teams and set running state to true
            tasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    startTeams();
                    running = true;
                }
            }.runTaskLater(plugin, 5L));
        });
    }
    
    /**
     * Gamemode-specific setups
     */
    public void performGamemodeSetup() {
        // Setup a tie
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                int blue = blueTeam.getRemainingPortals();
                int red = redTeam.getRemainingPortals();
                if (blue > red) {
                    endGame(blueTeam);
                } else if (red > blue) {
                    endGame(redTeam);
                } else {
                    endGame(null);
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), getSecondsRemaining() * 20));
        
        // Setup portals
        for (MissileWarsTeam team : new MissileWarsTeam[] {blueTeam, redTeam}) {
            FileConfiguration maps = ConfigUtils.getConfigFile("maps.yml");
            ConfigurationSection config = maps.getConfigurationSection(gamemode + "." + mapName + "." + "portals");
            if (config == null) {
                config = maps.getConfigurationSection(gamemode + ".default-map.portals");
            }
            for (String s : config.getKeys(false)) {
                double x = config.getDouble(s + ".x");
                double y = config.getDouble(s + ".y");
                double z = config.getDouble(s + ".z");
                if (team == redTeam) {
                    z = z * -1;
                }
                Location portalLoc = new Location(getWorld(), x, y, z);
                team.getPortals().put(portalLoc, true);
            }
        }
    }
    
    /**
     * Assigns players and starts the teams
     */
    public void startTeams() {
        // Assign players to teams based on queue (which removes their items)
        List<MissileWarsPlayer> toAssign = new ArrayList<>();
        for (MissileWarsPlayer player : players) {
            if (!spectators.contains(player)) {
                toAssign.add(player);
            }
        }
        Collections.shuffle(toAssign);
        double maxSize = getCapacity() / 2;
        double maxQueue = Math.ceil((double) (players.size() - spectators.size()) / 2);
        
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

        // Assign remaining players
        for (MissileWarsPlayer player : toAssign) {
            // Ignore if player is a spectator
            if (spectators.contains(player)) {
                continue;
            }
            if (blueTeam.getSize() <= redTeam.getSize()) {
                if (blueTeam.getSize() >= maxSize) {
                    ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), null, null);
                } else {
                    blueTeam.addPlayer(player);
                }
            } else {
                if (redTeam.getSize() >= maxSize) {
                    ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), null, null);
                } else {
                    redTeam.addPlayer(player);
                }
            }
        }

        // Send messages
        redTeam.distributeGear();
        redTeam.sendSound("game-start");
        blueTeam.distributeGear();
        blueTeam.sendSound("game-start");
        redTeam.scheduleDeckItems();
        redTeam.sendTitle(gamemode + "-start");
        blueTeam.scheduleDeckItems();
        blueTeam.sendTitle(gamemode + "-start");
    }

    /**
     * End a MissileWars game with a winning team
     *
     * @param winningTeam the winning team
     */
    public void endGame(MissileWarsTeam winningTeam) {
        // Ignore if game isn't running
        if (!running) {
            return;
        }

        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();

        // Cancel all tasks
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        stopTrackers();
        running = false;
        resetting = true;
        waitingForTie = false;
        redTeam.stopDeckItems();
        blueTeam.stopDeckItems();

        // Produce winner/discord messages
        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        String discordMessage;
        String winner;

        // Notify players and discord users of win
        if (winningTeam == null) {
            // Send titles
            redTeam.sendTitle("tie");
            blueTeam.sendTitle("tie");

            // Set notify messages
            discordMessage = ":pencil: A game was tied in arena " + this.getName();
            winner = "&e&lNONE";
        } else if (winningTeam == blueTeam) {
            // Send titles
            blueTeam.sendTitle("victory");
            redTeam.sendTitle("defeat");

            // Set notify messages
            List<String> blueList = new ArrayList<>();
            for (MissileWarsPlayer player : players) {
                if (blueTeam.containsPlayer(player.getMCPlayerId())) {
                    blueList.add(player.getMCPlayer().getName());
                }
            }
            String blueWinners = String.join(", ", blueList);
            discordMessage = ":tada: Team **blue** (" + blueWinners + ") has won a game in arena " + this.getName();

            winner = "&9&lBLUE";
        } else {
            // Send titles
            redTeam.sendTitle("victory");
            blueTeam.sendTitle("defeat");

            // Set notify messages
            List<String> redList = new ArrayList<>();
            for (MissileWarsPlayer player : players) {
                if (redTeam.containsPlayer(player.getMCPlayerId())) {
                    redList.add(player.getMCPlayer().getName());
                }
            }
            String redWinners = String.join(", ", redList);
            discordMessage = ":tada: Team **red** (" + redWinners + ") has won a game in arena " + this.getName();

            winner = "&c&lRED";
        }

        discordChannel.sendMessage(discordMessage).queue();

        // Setup player variables
        List<String> winningMessages = ConfigUtils.getConfigTextList("messages.classic-end", null, null, null);
        String earnMessage = ConfigUtils.getConfigText("messages.earn-currency", null, null, null);
        FileConfiguration ranksConfig = ConfigUtils.getConfigFile("ranks.yml");
        int spawn_missile = ranksConfig.getInt("experience.spawn_missile");
        int use_utility = ranksConfig.getInt("experience.use_utility");
        int kill = ranksConfig.getInt("experience.kill");
        double portal_broken = (double) ranksConfig.getInt("experience.portal_broken") / blueTeam.getTotalPortals();
        int shield_health = ranksConfig.getInt("experience.shield_health");
        int win = ranksConfig.getInt("experience.win");

        int red_shield_health_amount = ((int) ((100 - blueTeam.getShieldHealth())) / 10) * shield_health;
        int blue_shield_health_amount = ((int) ((100 - redTeam.getShieldHealth())) / 10) * shield_health;

        // Find players with mvp, most deaths, and kills
        List<MissileWarsPlayer> mvp = new ArrayList<>();
        List<MissileWarsPlayer> mostKills = new ArrayList<>();
        List<MissileWarsPlayer> mostDeaths = new ArrayList<>();
        for (MissileWarsPlayer player : players) {
            if (!getTeam(player.getMCPlayerId()).equals("no team")) {
                // Top MVPs
                if (mvp.isEmpty() || mvp.get(0).getMVP() < player.getMVP()) {
                    mvp.clear();
                    mvp.add(player);
                } else if (mvp.get(0).getMVP() == player.getMVP()) {
                    mvp.add(player);
                }
                // Top kills
                if (mostKills.isEmpty() || mostKills.get(0).getKills() < player.getKills()) {
                    mostKills.clear();
                    mostKills.add(player);
                } else if (mostKills.get(0).getKills() == player.getKills()) {
                    mostKills.add(player);
                }
                // Top deaths
                if (mostDeaths.isEmpty() || mostDeaths.get(0).getDeaths() < player.getDeaths()) {
                    mostDeaths.clear();
                    mostDeaths.add(player);
                } else if (mostDeaths.get(0).getDeaths() == player.getDeaths()) {
                    mostDeaths.add(player);
                }
            }
        }

        // Produce most mvp/kills/deaths list
        List<String> mostMVPList = new ArrayList<>();
        for (MissileWarsPlayer player : mvp) {
            mostMVPList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_mvp = String.join(", ", mostMVPList);
        
        List<String> mostKillsList = new ArrayList<>();
        for (MissileWarsPlayer player : mostKills) {
            mostKillsList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_kills = String.join(", ", mostKillsList);

        List<String> mostDeathsList = new ArrayList<>();
        for (MissileWarsPlayer player : mostDeaths) {
            mostDeathsList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_deaths = String.join(", ", mostDeathsList);

        int most_mvp_amount = mvp.isEmpty() ? 0 : mvp.get(0).getMVP();
        int most_kills_amount = mostKills.isEmpty() ? 0 : mostKills.get(0).getKills();
        int most_deaths_amount = mostDeaths.isEmpty() ? 0 : mostDeaths.get(0).getDeaths();

        Economy econ = MissileWarsPlugin.getPlugin().getEconomy();
        LocalDateTime endTime = LocalDateTime.now();
        long gameTime = Duration.between(startTime, endTime).toSeconds();
        
        // Calculate win message
        List<String> actualWinMessages = new ArrayList<>();
        for (String s : winningMessages) {
            s = s.replaceAll("%umw_winning_team%", winner);
            s = s.replaceAll("%umw_most_mvp_amount%", Integer.toString(most_mvp_amount));
            s = s.replaceAll("%umw_most_kills_amount%", Integer.toString(most_kills_amount));
            s = s.replaceAll("%umw_most_deaths_amount%", Integer.toString(most_deaths_amount));
            s = s.replaceAll("%umw_most_mvp%", most_mvp);
            s = s.replaceAll("%umw_most_kills%", most_kills);
            s = s.replaceAll("%umw_most_deaths%", most_deaths);
            actualWinMessages.add(ChatColor.translateAlternateColorCodes('&', s));
        }

        // Update stats for each player
        for (MissileWarsPlayer player : players) {

            player.getMCPlayer().setGameMode(GameMode.SPECTATOR);

            // Send win message
            for (String s : actualWinMessages) {
                player.getMCPlayer().sendMessage(s);
            }

            // -1 = TIE, 0 = LOST, 1 = WIN
            int won = winningTeam == null ? -1 : 0;

            // Calculate currency gain per-game
            int amountEarned = 0;
            int playerAmount = 0;
            int teamAmount = 0;
            UUID uuid = player.getMCPlayerId();
            if (!getTeam(uuid).equals("no team")) {
                long playTime = Duration.between(player.getJoinTime(), endTime).toSeconds();
                if (playTime > 40) {
                    playerAmount = spawn_missile * player.getMissiles() +
                                   use_utility * player.getUtility() +
                                   kill * player.getKills() +
                                   (int) (portal_broken * player.getMVP());
                    if (blueTeam.containsPlayer(uuid)) {
                        teamAmount = blue_shield_health_amount;
                        if (winningTeam == blueTeam) {
                            teamAmount += win;
                            won = 1;
                        }
                    } else {
                        teamAmount = red_shield_health_amount;
                        if (winningTeam == redTeam) {
                            teamAmount += win;
                            won = 1;
                        }
                    }
                    
                    double percentPlayed = (double) playTime / gameTime;
                    amountEarned = playerAmount + (int) (percentPlayed * teamAmount);
    
                    // Update player stats
                    SQLManager sql = MissileWarsPlugin.getPlugin().getSQL();
    
                    sql.updateClassicStats(uuid, player.getMVP(), won, 1, player.getKills(), player.getMissiles(), player.getUtility(), player.getDeaths());
                    sql.updateWinstreak(uuid, gamemode, won);
                    RankUtils.addExp(player.getMCPlayer(), amountEarned);
    
                    String earnMessagePlayer = earnMessage.replaceAll("%umw_amount_earned%", Integer.toString(amountEarned));
                    player.getMCPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', earnMessagePlayer));
                    econ.depositPlayer(player.getMCPlayer(), amountEarned);
                } else {
                    ConfigUtils.sendConfigMessage("messages.earn-none-time", player.getMCPlayer(), null, null);
                }
            }
        }

        long waitTime = plugin.getConfig().getInt("victory-wait-time") * 20L;
        
        if (!plugin.isEnabled()) {
            return;
        }

        // Remove all players after a short time, then reset the world a bit after
        startTime = null;
        if (players.size() > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removePlayers();
            }, waitTime);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetWorld();
        }, waitTime + 100L);
    }
    
    /** Remove Players from the map */
    public void removePlayers() {
        int cap = MissileWarsPlugin.getPlugin().getConfig().getInt("arena-cap");
        for (MissileWarsPlayer mwPlayer : new HashSet<>(players)) {
            // If DOES NOT HAVE the permission, then we DO REQUEUE the player
            // Also only requeue if capacity is 20
            Player player = mwPlayer.getMCPlayer();
            if (!player.hasPermission("umw.disablerequeue") && capacity == cap) {
                Boolean success = false;
                for (Arena arena : MissileWarsPlugin.getPlugin().getArenaManager().getLoadedArenas(gamemode)) {
                    if (arena.getCapacity() == cap && arena.getNumPlayers() < arena.getCapacity() && 
                            (!arena.isRunning() && !arena.isResetting())) {
                        // Switch player arenas
                        boolean spectate = spectators.contains(mwPlayer) && player.hasPermission("umw.continuespectating");
                        removePlayer(player.getUniqueId(), false);
                        arena.joinPlayer(player, spectate);
                        success = true;
                        break;
                    }
                }
                if (!success) {
                    ConfigUtils.sendConfigMessage("messages.requeue-failed", player, null, null);
                    removePlayer(player.getUniqueId(), true);
                }
            } else {
                removePlayer(player.getUniqueId(), true);
            }
        }
    }

    /** Load this Arena's world from the disk. */
    public void loadWorldFromDisk() {
        WorldCreator arenaCreator = new WorldCreator("mwarena_" + name);
        arenaCreator.generator(new VoidChunkGenerator()).createWorld().setAutoSave(false);
    }
    
    /** Reset the arena world. */
    public void resetWorld() {
        Bukkit.unloadWorld(getWorld(), false);
        loadWorldFromDisk();
        resetting = false;
        voteManager = new VoteManager(this);
    }

    /**
     * Get the spawn of a Spigot Player in the arena.
     *
     * @param player the player
     * @return player's spawn
     */
    public Location getPlayerSpawn(Player player) {
		if (redTeam != null && redTeam.containsPlayer(player.getUniqueId())) {
			return redTeam.getSpawn();
		} else if (blueTeam != null && blueTeam.containsPlayer(player.getUniqueId())) {
			return blueTeam.getSpawn();
		}
         else {
        	FileConfiguration schematicConfig = ConfigUtils.getConfigFile("maps.yml");
            Vector spawnVec = SchematicManager.getVector(schematicConfig, "lobby.spawn", null, null);
            Location spawnLoc = new Location(Bukkit.getWorld("mwarena_" + name), spawnVec.getX(), spawnVec.getY(), spawnVec.getZ());
            spawnLoc.setYaw(90);
            return spawnLoc;
        }
    }

    /**
     * Send a message to all players in the arena.
     *
     * @param path the path to the configurable message
     */
    public void announceMessage(String path, MissileWarsPlayer focus) {
        for (MissileWarsPlayer player : players) {
        	Player mcPlayer = player.getMCPlayer();
            ConfigUtils.sendConfigMessage(path, mcPlayer, this, focus != null ? focus.getMCPlayer() : null);
        }
    }

    /**
     * Sets the XP level for everyone in the arena.
     *
     * @param level the level to set XP to
     */
    public void setXpLevel(int level) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayer() != null) {
                player.getMCPlayer().setLevel(level);
            }
        }
    }

    /**
     * Register the breaking of a portal at a location in this Arena.
     *
     * @param location the location
     */
    public void registerPortalBreak(Location location, Entity entity) {
        // Ignore if game not running
        if (!running) {
            return;
        }

        // Check if portal broke at blue or red z
        MissileWarsTeam broketeam = blueTeam;
        MissileWarsTeam enemy = redTeam;
        int z = location.getBlockZ();
        if (z > 0) {
            broketeam = redTeam;
            enemy = blueTeam;
        }
        
        // Check if portal break was registered
        if (!broketeam.registerPortalBreak(location)) {
            return;
        }
        
        // Check if team still has living portals
        if (broketeam.hasLivingPortal()) {
            broketeam.sendTitle("own-portal-destroyed");
            enemy.sendTitle("enemy-portal-destroyed");
        }
        
        // Check if has associated player
        Player player = ConfigUtils.getAssociatedPlayer(entity, this);
        
        // Send messages if player found
        if (player != null && !getTeam(player.getUniqueId()).equals("no team")) {
            // Only add to stats if on opposite team
            if (enemy.containsPlayer(player.getUniqueId())) {
                getPlayerInArena(player.getUniqueId()).addToMVP(1);
            }
        }
        Component msg = CosmeticUtils.getPortalMessage(player, broketeam.getName());
        for (MissileWarsPlayer mwPlayer : players) {
            mwPlayer.getMCPlayer().sendMessage(msg);
        }
        
        // Waiting for a tie in this case
        if (!redTeam.hasLivingPortal() && !blueTeam.hasLivingPortal()) {
            endGame(null);
            return;
        }
        
        if (waitingForTie) {
            return;
        }

        // Check if either team's last portal has been broken
        int wait = MissileWarsPlugin.getPlugin().getConfig().getInt("tie-wait-time");
        
        if (!redTeam.hasLivingPortal()) {
            if (getSecondsRemaining() <= getChaosTime()) {
                endGame(blueTeam);
            } else {
                blueTeam.sendTitle("enemy-portals-destroyed");
                redTeam.sendTitle("own-portals-destroyed");
                waitingForTie = true;
                tasks.add(new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (blueTeam.hasLivingPortal()) {
                            endGame(blueTeam);
                        }
                    }
                }.runTaskLater(MissileWarsPlugin.getPlugin(), wait * 20L));
            }
        } else if (!blueTeam.hasLivingPortal()) {
            if (getSecondsRemaining() <= getChaosTime()) {
                endGame(redTeam);
            } else {
                blueTeam.sendTitle("own-portals-destroyed");
                redTeam.sendTitle("enemy-portals-destroyed");
                waitingForTie = true;
                tasks.add(new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (redTeam.hasLivingPortal()) {
                            endGame(redTeam);
                        }
                    }
                }.runTaskLater(MissileWarsPlugin.getPlugin(), wait * 20L));
            }
        }
    }

    /**
     * Get the team color of a given player.
     *
     * @param id the player's UUID
     * @return the team color of the given player
     */
    public ChatColor getTeamColor(UUID id) {
        if (blueTeam != null && blueTeam.containsPlayer(id)) {
            return ChatColor.BLUE;
        } else if (redTeam != null && redTeam.containsPlayer(id)) {
            return ChatColor.RED;
        } else {
            return null;
        }
    }

    /**
     * Register the editing of a block in the Arena.
     *
     * @param location the location of the edited block
     */
    public void registerShieldBlockEdit(Location location, boolean place) {
        // Ignore if game not running
        if (!(running || resetting)) {
            return;
        }

        // Only attempt blue update if red unchanged
        if (location.getBlockZ() > 0) {
            redTeam.registerShieldBlockUpdate(location, place);
        } else {
            blueTeam.registerShieldBlockUpdate(location, place);
        }
    }
}
