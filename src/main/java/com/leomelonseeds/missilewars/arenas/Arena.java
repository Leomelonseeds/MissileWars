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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.earth2me.essentials.Essentials;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.votes.VoteManager;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.schematics.VoidChunkGenerator;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.tracker.Tracker;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.github.a5h73y.parkour.Parkour;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.exception.NPCLoadException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Represents a MissileWarsArena where the game will be played. */
public abstract class Arena implements ConfigurationSerializable {

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
    /** Set of players who have played but have since left */
    protected HashMap<UUID, Integer> leftPlayers;
    /** Helper variable to check when all queues are done */
    protected int queueCount;

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
        leftPlayers = new HashMap<>();
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
        leftPlayers = new HashMap<>();
        voteManager = new VoteManager(this);
    }
    
    public void unregisterTeams() {
        if (redTeam != null && blueTeam != null) {
            redTeam.unregisterTeam();
            blueTeam.unregisterTeam();
        }
    }
    
    /**
     * Call when a player leaves the game/arena
     * 
     * @param uuid
     */
    public void addLeft(UUID uuid) {
        if (leftPlayers.containsKey(uuid)) {
            leftPlayers.put(uuid, leftPlayers.get(uuid) + 1);
        } else {
            leftPlayers.put(uuid, 1);
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
        if (startTime == null || resetting) {
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
        int untilNextStage;
        String status;
        if (seconds > 1200) {
            status = "75% Cooldown: ";
            untilNextStage = seconds - 1200;
        } else if (seconds > 600) {
            status = "50% Cooldown: ";
            untilNextStage = seconds - 600;
        } else if (seconds > 300) {
            status = "No tie wait: ";
            untilNextStage = seconds - 300;
        } else {
            status = "Game ends in: ";
            untilNextStage = seconds;
        }
        return NamedTextColor.GRAY + status + NamedTextColor.GREEN + 
                String.format("%02d:%02d", (untilNextStage / 60) % 60, untilNextStage % 60);
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
        InventoryUtils.clearInventory(player, true);
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
        
        // Give player items
        ItemStack leave = MissileWarsPlugin.getPlugin().getDeckManager().createItem("held.to-lobby", 0, false);
        addHeldMeta(leave, "leave");
        player.getInventory().setItem(8, leave);
        ItemStack votemap = MissileWarsPlugin.getPlugin().getDeckManager().createItem("held.votemap", 0, false);
        addHeldMeta(votemap, "votemap");
        player.getInventory().setItem(4, votemap);

        // Check for game start
        postJoin(player, asSpectator);
        checkForStart();
        return true;
    }
    
    // Post-join actions that may differ by arena
    protected void postJoin(Player player, boolean asSpectator) { 
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
                enqueue(player.getUniqueId(), "red");
            } else {
                enqueue(player.getUniqueId(), "blue");
            }
        }
    }
    
    private void addHeldMeta(ItemStack item, String s) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "held"),
                PersistentDataType.STRING, s);
        item.setItemMeta(meta);
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
        MissileWarsPlayer toRemove = getPlayerInArena(uuid);
        players.remove(toRemove);
        voteManager.removePlayer(toRemove.getMCPlayer());

        for (MissileWarsPlayer mwPlayer : players) {
            ConfigUtils.sendConfigMessage("messages.leave-arena-others", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
        }

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

        MissileWarsPlayer toRemove = getPlayerInArena(uuid);
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
     * Checks if the game is empty, and ends game/cancels tasks if so
     */
    private void checkEmpty() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();   
        if (!plugin.isEnabled()) {
            return;
        }
        
        if (!running && startTime != null && getNumPlayers() < plugin.getConfig().getInt("minimum-players")) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
            startTime = null;
            return;
        }
        
        if (!running || redTeam == null || blueTeam == null) {
            return;
        }
        
        autoEnd();
    }
    
    protected void autoEnd() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();   
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
            }.runTaskLater(plugin, 60 * 20L);
        } else if (blueTeam.getSize() <= 0) {
            announceMessage("messages.blue-team-empty", null);
            autoEnd = new BukkitRunnable() {
                @Override
                public void run() {
                    if (running && blueTeam.getSize() <= 0) {
                        endGame(redTeam);
                    }
                }
            }.runTaskLater(plugin, 60 * 20L);
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
     * Enqueue a player with a given UUID to a team
     *
     * @param uuid the Player's UUID
     * @param team use "red" or "blue"
     */
    public void enqueue(UUID uuid, String team) {
        for (MissileWarsPlayer player : players) {
            if (!player.getMCPlayerId().equals(uuid)) {
                continue;
            }
            
            // Make sure people can't break the game
            if (startTime != null) {
                int time = (int) Duration.between(LocalDateTime.now(), startTime).toSeconds();
                if (time <= 1 && time >= -1) {
                    ConfigUtils.sendConfigMessage("messages.queue-join-time", player.getMCPlayer(), this, null);
                    return;
                }
            }
            
            Player mcPlayer = player.getMCPlayer();
            if (!running) {
                Queue<MissileWarsPlayer> queue = team.equals("red") ? redQueue : blueQueue;
                Queue<MissileWarsPlayer> otherQueue = team.equals("red") ? blueQueue : redQueue;
                if (!queue.contains(player)) {
                    if (queue.size() >= getCapacity() / 2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-full", mcPlayer, this, null);
                    } else {
                        otherQueue.remove(player);
                        queue.add(player);
                        ConfigUtils.sendConfigMessage("messages.queue-waiting-" + team, mcPlayer, this, null);
                        removeSpectator(player);
                    }
                } else {
                    queue.remove(player);
                    ConfigUtils.sendConfigMessage("messages.queue-leave-" + team, mcPlayer, this, null);
                }
            } else {
                MissileWarsTeam joinTeam = team.equals("red") ? redTeam : blueTeam;
                MissileWarsTeam otherTeam = team.equals("red") ? blueTeam : redTeam;
                if (joinTeam.containsPlayer(uuid)) {
                    return;
                }
                
                if (!mcPlayer.isOp() && joinTeam.getSize() - otherTeam.getSize() >= 1) {
                    ConfigUtils.sendConfigMessage("messages.queue-join-error", mcPlayer, this, null);
                } else if (!mcPlayer.hasPermission("umw.joinfull") && joinTeam.getSize() >= capacity / 2) {
                    ConfigUtils.sendConfigMessage("messages.queue-join-full", mcPlayer, this, null);
                } else {
                    removeSpectator(player);
                    otherTeam.removePlayer(player);
                    joinTeam.addPlayer(player);
                    checkNotEmpty();
                    announceMessage("messages.queue-join-" + team, player);
                }
            }
            return;
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
            if (!player.getMCPlayerId().equals(uuid)) {
                continue;
            }
            
            if (!(running || resetting) || getTeam(uuid).equals("no team")) {
                announceMessage("messages.spectate-join-others", player);
                spectators.add(player);
                redQueue.remove(player);
                blueQueue.remove(player);
                Player mcPlayer = player.getMCPlayer();
                mcPlayer.setGameMode(GameMode.SPECTATOR);
                mcPlayer.sendActionBar(Component.text("Type /spectate to stop spectating"));
                checkEmpty();
            } else {
                player.getMCPlayer().sendMessage(ConfigUtils.getConfigText("messages.spectate-join-fail",
                        player.getMCPlayer(), null, null));
            }
            break;
        }
    }

    /** Schedule the start of the game based on the config time. */
    public void scheduleStart(int secCountdown) {
    	MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();

        // Schedule the start of the game if not already running
        if (startTime != null) {
            return;
        }
        
        // Respawns citizens
        try {
            ((Citizens) CitizensAPI.getPlugin()).reload();
        } catch (NPCLoadException e) {
            Bukkit.getLogger().log(Level.WARNING, "Citizens in " + getWorld().getName() + " couldn't be reloaded.");
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
            performTimeSetup();
            performGamemodeSetup();
            
            // Teleport all players to center to remove lobby minigame items/dismount
            for (MissileWarsPlayer player : players) {
                player.getMCPlayer().teleport(getPlayerSpawn(player.getMCPlayer()));
                player.getMCPlayer().closeInventory();
            }
            
            // Register teams and set running state to true
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> startTeams(), 5L));
        });
    }
    
    /**
     * Time specific setup
     */
    protected void performTimeSetup() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        BukkitScheduler scheduler = Bukkit.getScheduler();
        
        // Stage 1 chaos
        tasks.add(scheduler.runTaskLater(plugin, () -> {
            blueTeam.setMultiplier(blueTeam.getMultiplier() * 0.75);
            redTeam.setMultiplier(redTeam.getMultiplier() * 0.75);
            announceMessage("messages.chaos1", null);
        }, 600 * 20));
        
        // Stage 2 chaos
        tasks.add(scheduler.runTaskLater(plugin, () -> {
            blueTeam.setMultiplier(blueTeam.getMultiplier() * 2 / 3);
            redTeam.setMultiplier(redTeam.getMultiplier() * 2 / 3);
            announceMessage("messages.chaos2", null);
        }, 1200 * 20));
        
        // Stage 3 chaos
        tasks.add(scheduler.runTaskLater(plugin, () -> announceMessage("messages.chaos3", null), 1500 * 20));

        // Reminders 1 minute before the game ends
        int[] reminderTimes = {1740, 1770, 1790, 1795, 1796, 1797, 1798, 1799};
        for (int i : reminderTimes) {
            tasks.add(scheduler.runTaskLater(plugin, () -> announceMessage("messages.game-end-reminder", null), i * 20));
        }
        
        // Setup a tie
        tasks.add(scheduler.runTaskLater(plugin, () -> {
            int blue = blueTeam.getRemainingPortals();
            int red = redTeam.getRemainingPortals();
            if (blue > red) {
                endGame(blueTeam);
            } else if (red > blue) {
                endGame(redTeam);
            } else {
                endGame(null);
            }
        }, getSecondsRemaining() * 20));
    }
    
    /**
     * Gamemode-specific setups
     */
    protected abstract void performGamemodeSetup();
    
    /**
     * Assigns players and starts the teams
     */
    protected void startTeams() {
        // Assign players to teams based on queue (which removes their items)
        List<MissileWarsPlayer> toAssign = new ArrayList<>();
        for (MissileWarsPlayer player : players) {
            if (!spectators.contains(player)) {
                toAssign.add(player);
            }
        }
        queueCount = toAssign.size();
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
        redTeam.sendSound("game-start");
        blueTeam.sendSound("game-start");
        redTeam.sendTitle(gamemode + "-start");
        blueTeam.sendTitle(gamemode + "-start");
    }
    
    /**
     * Call after adding player to arena to schedule item distribution
     * 
     * @param player 
     */
    public void addCallback(MissileWarsPlayer player) {
        // Return if player null, decrease queuecount to not hang game
        if (player.getMCPlayer() == null) {
            queueCount--;
            return;
        }
        
        // Once redteam + blueteam = queuecount, running will be TRUE
        // and therefore no race condition is possible
        if (!running) {
            if (blueTeam.getSize() + redTeam.getSize() < queueCount) {
                return;
            }
            
            applyMultipliers();
            for (MissileWarsPlayer mwp : redTeam.getMembers()) {
                mwp.initDeck(false);
            }
            
            for (MissileWarsPlayer mwp : blueTeam.getMembers()) {
                mwp.initDeck(false);
            }
            running = true;
        } else {
            applyMultipliers();
            player.initDeck(leftPlayers.containsKey(player.getMCPlayerId()) && 
                    leftPlayers.get(player.getMCPlayerId()) >= 2);
        }
    }
    
    /**
     * Apply multipliers for team balancing
     */
    public void applyMultipliers() {
        MissileWarsTeam one = blueTeam; // Team with less players
        MissileWarsTeam two = redTeam; // Team with more players
        if (blueTeam.getSize() > redTeam.getSize()) {
            one = redTeam;
            two = blueTeam;
        } else if (blueTeam.getSize() == redTeam.getSize() || blueTeam.getSize() == 0 || redTeam.getSize() == 0) {
            one.setMultiplier(1);
            two.setMultiplier(1);
            return;
        }
        
        // No need to balance if two / one > 3 / 2
        double oneSize = one.getSize();
        double twoSize = two.getSize();
        if (oneSize * 3 / 2 > twoSize) {
            one.setMultiplier(1);
            two.setMultiplier(1);
            return;
        }
        
        one.setMultiplier(oneSize / twoSize);
        one.broadcastConfigMsg("messages.team-balancing", null);
        two.setMultiplier(1);
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
        if (!plugin.isEnabled()) {
            return;
        }

        // Produce winner/discord messages
        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        String discordMessage;

        // Notify players and discord users of win
        if (winningTeam == null) {
            // Send titles
            redTeam.sendTitle("tie");
            blueTeam.sendTitle("tie");

            // Set notify messages
            discordMessage = ":pencil: A game was tied in arena " + this.getName();
        } else {
            // Send titles
            boolean isRed = winningTeam == redTeam;
            MissileWarsTeam losingTeam = isRed ? blueTeam : redTeam;
            winningTeam.sendTitle("victory");
            losingTeam.sendTitle("defeat");

            // Set notify messages
            List<String> winList = new ArrayList<>();
            for (MissileWarsPlayer player : winningTeam.getMembers()) {
                winList.add(player.getMCPlayer().getName());
            }
            String winners = String.join(", ", winList);
            discordMessage = ":tada: Team **" + winningTeam.getName() + "** (" + winners + ") has won a game in arena " + this.getName();
            
            // Spawn victory pegasus
            SchematicManager.spawnNBTStructure(null, "pegasus-0", winningTeam.getSpawn(), isRed, mapName, false, false);
        }
        discordChannel.sendMessage(discordMessage).queue();
        
        for (MissileWarsPlayer player : players) {
            player.getMCPlayer().setGameMode(GameMode.SPECTATOR);
            player.stopDeck();
        }
        leftPlayers.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> calculateStats(winningTeam));

        // Remove all players after a short time, then reset the world a bit after
        long waitTime = plugin.getConfig().getInt("victory-wait-time") * 20L;
        if (players.size() > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removePlayers();
            }, waitTime);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetWorld();
        }, waitTime + 100L);
    }
    
    // Calculate and store all player stats from the game
    protected abstract void calculateStats(MissileWarsTeam winningTeam);
    
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
        
        // Just in case there are stragglers somehow
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            for (Player player : getWorld().getPlayers()) {
                player.teleport(ConfigUtils.getSpawnLocation());
            }
        }, 1);
    }

    /** Load this Arena's world from the disk. */
    public void loadWorldFromDisk() {
        WorldCreator arenaCreator = new WorldCreator("mwarena_" + name);
        arenaCreator.generator(new VoidChunkGenerator()).createWorld().setAutoSave(false);
    }
    
    /** Reset the arena world. */
    public void resetWorld() {
        Bukkit.unloadWorld(getWorld(), false);
        unregisterTeams();
        loadWorldFromDisk();
        resetting = false;
        startTime = null;
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
     * Get the team color of a given player.
     *
     * @param id the player's UUID
     * @return the team color of the given player
     */
    public NamedTextColor getTeamColor(UUID id) {
        if (blueTeam != null && blueTeam.containsPlayer(id)) {
            return NamedTextColor.BLUE;
        } else if (redTeam != null && redTeam.containsPlayer(id)) {
            return NamedTextColor.RED;
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
