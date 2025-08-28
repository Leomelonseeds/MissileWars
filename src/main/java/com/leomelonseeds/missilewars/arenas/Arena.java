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

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.earth2me.essentials.Essentials;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.arenas.tracker.Tracker;
import com.leomelonseeds.missilewars.arenas.votes.VoteManager;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.db.DBCallback;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.github.a5h73y.parkour.Parkour;

/** Represents a MissileWarsArena where the game will be played. */
public abstract class Arena implements ConfigurationSerializable {

    /** Comparator for sorting arenas */
    public static Comparator<Arena> byCapacity = Comparator.comparing(a -> a.getCapacity());
    public static Comparator<Arena> byName = Comparator.comparing(a -> a.getName());
    public static Comparator<Arena> byPlayers = Comparator.comparing(a -> a.getNumPlayers());
    
    protected MissileWarsPlugin plugin;
    protected String name;
    protected String mapName;
    protected String gamemode;
    protected int capacity;
    protected List<Integer> npcs;
    protected Map<UUID, MissileWarsPlayer> players;
    protected Set<MissileWarsPlayer> spectators;
    protected Queue<MissileWarsPlayer> redQueue;
    protected Queue<MissileWarsPlayer> blueQueue;
    protected MissileWarsTeam redTeam;
    protected MissileWarsTeam blueTeam;
    protected LocalDateTime startTime;
    protected boolean running;
    protected boolean waitingForTie;
    protected List<BukkitTask> tasks;
    protected boolean resetting;
    /** Task for automatically ending the game if no players are present */
    protected BukkitTask autoEnd;
    protected Tracker tracker;
    protected VoteManager voteManager;
    /** Set of players who have played but have since left */
    protected HashMap<UUID, Integer> leftPlayers;
    /** Helper variable to check when all queues are done */
    protected int queueCount;
    /** The rank level to evaluate map selection by */
    protected int rankMedian;

    /**
     * Create a new Arena with a given name and max capacity.
     *
     * @param name the name
     * @param capacity the max capacity
     */
    public Arena(String name, int capacity) {
        this.plugin = MissileWarsPlugin.getPlugin();
        this.name = name;
        this.capacity = capacity;
        this.gamemode = "classic";
        npcs = new ArrayList<>();
        init();
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
        plugin = MissileWarsPlugin.getPlugin();
        name = (String) serializedArena.get("name");
        capacity = (int) serializedArena.get("capacity");
        gamemode = (String) serializedArena.get("gamemode");
        npcs = new ArrayList<>();
        String npcIDs = (String) serializedArena.get("npc");
        for (String s : npcIDs.split(",")) {
            npcs.add(Integer.parseInt(s));
        }
        init();
    }
    
    private void init() {
        players = new HashMap<>();
        spectators = new HashSet<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
        tasks = new LinkedList<>();
        tracker = new Tracker();
        leftPlayers = new HashMap<>();
        voteManager = new VoteManager(gamemode);
        startSpectatorActionBarTask();
    }
    
    /**
     * Simple task to remind players how to exit spectator mode
     */
    private void startSpectatorActionBarTask() {
        tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (MissileWarsPlayer mwp : spectators) {
                Player player = mwp.getMCPlayer();
                player.sendActionBar(ConfigUtils.toComponent("Type /spectate to stop spectating"));
            }
        }, 20, 2));
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
    public void cancelTasks() {
        tasks.forEach(t -> t.cancel());
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
     * Get people in red queue. If the game has already started,
     * it returns the corresponding team's size.
     *
     * @return
     */
    public int getRedQueue() {
        if (running) {
            return getRedTeam().getSize();
        }
        return redQueue.size();
    }

    /**
     * Get people in blue queue. If the game has already started,
     * it returns the corresponding team's size.
     *
     * @return
     */
    public int getBlueQueue() {
        if (running) {
            return getBlueTeam().getSize();
        }
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
     * Get a {@link MissileWarsPlayer} in this arena from a given UUID.
     *
     * @param uuid the Player's UUID
     * @return the {@link MissileWarsPlayer} with the given UUID in this Arena, otherwise null
     */
    public MissileWarsPlayer getPlayerInArena(UUID uuid) {
        return players.get(uuid);
    }

    /**
     * Get the team a player with a given UUID is on.
     *
     * @param uuid the UUID to check for
     * @return the team that the player with uuid is on
     */
    public TeamName getTeam(UUID uuid) {
        if (redTeam == null || blueTeam == null) {
            return TeamName.NONE;
        }
        if (redTeam.containsPlayer(uuid)) {
            return redTeam.getName();
        }
        if (blueTeam.containsPlayer(uuid)) {
            return blueTeam.getName();
        }
        return TeamName.NONE;
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
     * Adds offset of 50ms to account for a tick of lag
     *
     * @return the number of seconds until the game starts
     */
    public long getSecondsUntilStart() {
        // Ensure arena is set to start
        if (startTime == null) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(), startTime).plusMillis(50).toSeconds();
    }

    /**
     * Get the number of seconds remaining in the game.
     * Adds offset of 50ms to account for a tick of lag
     *
     * @return the number of seconds remaining in the game
     */
    public long getSecondsRemaining() {
        if (startTime == null || resetting) {
            return 0;
        }
        int totalSecs = plugin.getConfig().getInt("game-length") * 60;
        long secsTaken = Duration.between(startTime, LocalDateTime.now()).plusMillis(50).toSeconds();
        return totalSecs - secsTaken;
    }

    /**
     * Gets the time remaining
     *
     * @return a formatted time with mm:ss
     */
    public String getTimeRemaining() {
        // Adjust for correct timings
        int seconds = Math.max((int) getSecondsRemaining(), 0);
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
        return "§7" + status + "§a" + 
                String.format("%02d:%02d", (untilNextStage / 60) % 60, untilNextStage % 60);
    }

    /**
     * @return the number of player currently in the game
     */
    public int getNumPlayers() {
        if (blueTeam == null || blueTeam.getSize() == 0) {
            return players.size() - spectators.size();
        }
        return redTeam.getSize() + blueTeam.getSize();
    }

    /**
     * @return the total number of players in the arena
     */
    public int getTotalPlayers() {
        return players.size();
    }

    /**
     * @return the max number of allowed participants
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Check if the given map is available to play (i.e. lobby
     * meets the rank requirements)
     * 
     * @param map
     * @return
     */
    public boolean isAvailable(String map) {
        return rankMedian >= ArenaUtils.getRankRequirement(gamemode, map);
    }
    
    /**
     * Re-calculates the mode of the rank in this arena to
     * evaluate map selection by
     */
    private void calculateRankMedian() {
        if (running || resetting) {
            return;
        }
        
        List<Integer> ranks = new ArrayList<>();
        for (MissileWarsPlayer mwp : players.values()) {
            if (spectators.contains(mwp)) {
                continue;
            }
            
            int exp = plugin.getSQL().getExpSync(mwp.getMCPlayerId());
            ranks.add(RankUtils.getRankLevel(exp));
        }
        
        if (ranks.isEmpty()) {
            return;
        }
        
        Collections.sort(ranks);
        this.rankMedian = ranks.get((ranks.size() - 1) / 2);
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
        ArenaUtils.updatePlayerBoots(player);
        ConfigUtils.sendConfigMessage("messages.join-arena", player, this, null);

        for (MissileWarsPlayer mwPlayer : players.values()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-others", mwPlayer.getMCPlayer(), null, player);
        }

        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        discordChannel.sendMessage(":arrow_backward: " + player.getName() + " left and joined arena " + this.getName()).queue();

        player.setHealth(ArenaUtils.getMaxHealth(player));
        player.setFoodLevel(20);
        players.put(player.getUniqueId(), new MissileWarsPlayer(player.getUniqueId()));
        player.setRespawnLocation(getPlayerSpawn(player), true);
        player.setGameMode(GameMode.ADVENTURE);

        for (Player worldPlayer : Bukkit.getWorld("world").getPlayers()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-lobby", worldPlayer, this, player);
        }
        
        giveHeldItems(player);

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
            int redSize = getRedTeam().getSize();
            int blueSize = getBlueTeam().getSize();
            if (redSize == blueSize) {
                enqueue(player.getUniqueId(), Math.random() > 0.5 ? "red" : "blue");
            } else {
                enqueue(player.getUniqueId(), blueSize > redSize ? "red" : "blue");
            }
        }
       
        else {
            calculateRankMedian();
        }
    }
    
    // Give player necessary items
    protected void giveHeldItems(Player player) {
        String[] items = {"votemap", "to-lobby", "red", "blue", "deck", "spectate"};
        FileConfiguration itemConfig = ConfigUtils.getConfigFile("items.yml");
        for (String i : items) {
            String path = "held." + i;
            ItemStack item = InventoryUtils.createItem(path);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "held"),
                    PersistentDataType.STRING, i);
            item.setItemMeta(meta);
            player.getInventory().setItem(itemConfig.getInt(path + ".slot"), item);
        }
    }
    
    /**
     * Checks if the game is ready to auto-start
     */
    public void checkForStart() {
        if (running || resetting) {
            return;
        }
        int minPlayers = plugin.getConfig().getInt("minimum-players");
        if (getNumPlayers() >= minPlayers) {
            scheduleStart();
        }
    }
    
    /**
     * Equivalent to starting with the default timer
     */
    public void scheduleStart() {
        scheduleStart(plugin.getConfig().getInt("lobby-wait-time"));
    }
    
    /**
     * Check if a player with a given UUID is in this Arena.
     *
     * @param uuid the UUID of the player
     * @return true if the player is in this Arena, otherwise false
     */
    public boolean isInArena(UUID uuid) {
        return players.containsKey(uuid);
    }

    /**
     * Remove a player with a given UUID from the arena.
     *
     * @param uuid the UUID of the player
     */
    public void removePlayer(UUID uuid, Boolean tolobby) {
        // Remove player from all teams and queues
        MissileWarsPlayer toRemove = getPlayerInArena(uuid);
        players.remove(uuid);
        Player mcPlayer = toRemove.getMCPlayer();
        voteManager.removePlayer(mcPlayer);
        calculateRankMedian();

        for (MissileWarsPlayer mwPlayer : players.values()) {
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
        	mcPlayer.teleport(ConfigUtils.getSpawnLocation());
        	mcPlayer.setGameMode(GameMode.ADVENTURE);
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
            removePlayer(uuid, true);
            return true;
        }

        if (redTeam.containsPlayer(uuid)) {
            redTeam.removePlayer(toRemove);
            for (MissileWarsPlayer mwPlayer : players.values()) {
                ConfigUtils.sendConfigMessage("messages.leave-team-red", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
            }
        } else if (blueTeam.containsPlayer(uuid)) {
            blueTeam.removePlayer(toRemove);
            for (MissileWarsPlayer mwPlayer : players.values()) {
                ConfigUtils.sendConfigMessage("messages.leave-team-blue", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
            }
        } else {
            removePlayer(uuid, true);
            return true;
        }

        checkEmpty();
        player.teleport(getPlayerSpawn(player));
        player.setGameMode(GameMode.ADVENTURE);
        ArenaUtils.updatePlayerBoots(player);
        giveHeldItems(player);

        return true;
    }
    
    /**
     * Checks if the game is not empty, and cancels game end task if so
     */
    public void checkNotEmpty() {
        if (!plugin.isEnabled()) {
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
        if (!plugin.isEnabled()) {
            return;
        }
        
        // Cancel if not running and there's enough time left
        // Don't cancel game if its in the process of starting
        if (!running && startTime != null && getSecondsUntilStart() >= 0 && 
                getNumPlayers() < plugin.getConfig().getInt("minimum-players")) {
            cancelTasks();
            startTime = null;
            return;
        }
        
        if (!running || redTeam == null || blueTeam == null) {
            return;
        }
        
        autoEnd();
    }
    
    protected void autoEnd() {
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
     * @param force whether to allow uneven team sizes
     */
    public void enqueue(UUID uuid, String team, boolean force) {
        // Make sure people can't break the game
        MissileWarsPlayer player = getPlayerInArena(uuid);
        Player mcPlayer = player.getMCPlayer();
        if (startTime != null) {
            long time = getSecondsUntilStart();
            if (time <= 1 && time >= -1) {
                ConfigUtils.sendConfigMessage("messages.queue-join-time", mcPlayer, this, null);
                return;
            }
        }

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
            
            if (joinTeam.getSize() - otherTeam.getSize() >= 1 && !force) {
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
    }
    
    /**
     * Enqueue a player with a given UUID to a team
     * Do not override this function! Override the
     * 3 parameter one instead.
     *
     * @param uuid the Player's UUID
     * @param team use "red" or "blue"
     */
    public void enqueue(UUID uuid, String team) {
        enqueue(uuid, team, false);
    }

    /**
     * Remove the given player from the spectators list.
     *
     * @param player the player
     */
    public void removeSpectator(MissileWarsPlayer player) {
        if (!spectators.remove(player)) {
            return;
        }
        
        announceMessage("messages.spectate-leave-others", player);
        Player mcPlayer = player.getMCPlayer();
        mcPlayer.setGameMode(GameMode.ADVENTURE);
        mcPlayer.teleport(getPlayerSpawn(mcPlayer));
        mcPlayer.sendActionBar(ConfigUtils.toComponent(""));
        calculateRankMedian();
        checkForStart();
    }

    /**
     * Add the player with a given UUID to the spectators.
     *
     * @param uuid the UUID of the player
     */
    public void addSpectator(UUID uuid) {
        MissileWarsPlayer player = getPlayerInArena(uuid);
        if (!(running || resetting) || getTeam(uuid) == TeamName.NONE) {
            announceMessage("messages.spectate-join-others", player);
            spectators.add(player);
            redQueue.remove(player);
            blueQueue.remove(player);
            Player mcPlayer = player.getMCPlayer();
            mcPlayer.setGameMode(GameMode.SPECTATOR);
            voteManager.removePlayer(mcPlayer);
            calculateRankMedian();
            checkEmpty();
        } else {
            ConfigUtils.sendConfigMessage("messages.spectate-join-fail", player.getMCPlayer(), null, null);
        }
    }

    /** Schedule the start of the game based on the config time. */
    public void scheduleStart(int secCountdown) {
        // Schedule the start of the game if not already running
        if (startTime != null) {
            return;
        }

        // Schedule start
        startTime = LocalDateTime.now().plusSeconds(secCountdown);
        announceMessage("messages.lobby-countdown-start", null);
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (running) {
                return;
            }
            
            if (!start()) {
                announceMessage("messages.start-failed", null);
            }
        }, secCountdown * 20));

        // Schedule 30-second countdown
        int cdNear = plugin.getConfig().getInt("lobby-countdown-near");
        for (int secInCd = secCountdown; secInCd > 0; secInCd--) {
            int finalSecInCd = secInCd;
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (running) {
                    return;
                }
                
                if (finalSecInCd <= cdNear) {
                    announceMessage("messages.lobby-countdown-near", null);
                }
                
                for (MissileWarsPlayer player : players.values()) {
                    if (player.getMCPlayer() != null) {
                        player.getMCPlayer().setLevel(finalSecInCd);
                    }
                }
            }, (secCountdown - secInCd) * 20));
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

        // Select Map
        mapName = voteManager.getVotedMap(map -> isAvailable(map));

        // Generate map.
        announceMessage("messages.starting", null);
        return SchematicManager.spawnFAWESchematic(mapName, getWorld(), gamemode, result -> {
            // Result will only run if map loading is a success
            // Acquire red and blue spawns
            FileConfiguration mapConfig = ConfigUtils.getConfigFile("maps.yml");
            Vector blueSpawnVec = SchematicManager.getVector(mapConfig, "blue-spawn", gamemode, mapName);
            World world = getWorld();
            Location blueSpawn = new Location(world, blueSpawnVec.getX(), blueSpawnVec.getY(), blueSpawnVec.getZ());
            Vector redSpawnVec = SchematicManager.getVector(mapConfig, "red-spawn", gamemode, mapName);
            Location redSpawn = new Location(world, redSpawnVec.getX(), redSpawnVec.getY(), redSpawnVec.getZ());
            redSpawn.setYaw(180);

            // Setup scoreboard and teams
            blueTeam = new MissileWarsTeam(TeamName.BLUE, this, blueSpawn);
            redTeam = new MissileWarsTeam(TeamName.RED, this, redSpawn);
            
            // Setup game timers
            // Game start
            startTime = LocalDateTime.now();
            performTimeSetup();
            performGamemodeSetup();
            
            // Teleport all players to center to remove lobby minigame items/dismount
            for (MissileWarsPlayer player : players.values()) {
                player.getMCPlayer().teleport(getPlayerSpawn(player.getMCPlayer()));
                player.getMCPlayer().closeInventory();
            }
            
            // Register teams and set running state to true
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Checks if all players are spectating (and if there are no players)
                if (spectators.size() == players.size()) {
                    running = true;
                    return;
                }
                startTeams();
            }, 5L));
            
            // Tint task if any player is in the other team's base
            WorldBorder virtualBorder = Bukkit.createWorldBorder();
            virtualBorder.setSize(world.getWorldBorder().getSize());
            virtualBorder.setCenter(world.getWorldBorder().getCenter());
            virtualBorder.setWarningDistance(2048);
            tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (!running) {
                    return;
                }
                
                for (MissileWarsTeam team : List.of(redTeam, blueTeam)) {
                    MissileWarsTeam opposite = team == redTeam ? blueTeam : redTeam;
                    boolean isInShield = false;
                    for (MissileWarsPlayer mwp : team.getMembers()) {
                        Player player = mwp.getMCPlayer();
                        if (player == null) {
                            continue;
                        }
                        
                        if (!ArenaUtils.inShield(this, player.getLocation(), opposite.getName(), 8)) {
                            continue;
                        }
                        
                        isInShield = true;
                        break;
                    }
                    
                    boolean inShieldFinal = isInShield;
                    ConfigUtils.schedule(0, () -> opposite.getMembers().forEach(mwp -> {
                        Player player = mwp.getMCPlayer();
                        if (player == null) {
                            return;
                        }
                        
                        if (inShieldFinal) {
                            player.setWorldBorder(virtualBorder);
                        } else {
                            player.setWorldBorder(null);
                        }
                    }));
                }
            }, 200, 10));
        });
    }
    
    /**
     * Time specific setup
     */
    protected void performTimeSetup() {
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
        for (MissileWarsPlayer player : players.values()) {
            if (!spectators.contains(player)) {
                toAssign.add(player);
            }
        }
        
        // Assign queued players. If a queue is larger than a team size put remaining
        // players into the front of the queue to be assigned first into random teams
        queueCount = Math.min(capacity, toAssign.size());
        double maxTeamSize = capacity / 2;
        double maxQueue = Math.ceil(toAssign.size() / 2.0);
        Collections.shuffle(toAssign);
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
            if (blueTeam.getSize() <= redTeam.getSize()) {
                if (blueTeam.getSize() >= maxTeamSize) {
                    ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), null, null);
                } else {
                    blueTeam.addPlayer(player);
                }
            } else {
                if (redTeam.getSize() >= maxTeamSize) {
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
        // If game is running, no null/queuecount checks are needed
        if (running) {
            applyMultipliers();
            UUID uuid = player.getMCPlayerId();
            player.initDeck(leftPlayers.getOrDefault(uuid, 0) >= 3, this, redTeam.containsPlayer(uuid));
            return;
        }
        
        // Return if player is not in game, decrease queuecount to not hang game
        if (!players.containsKey(player.getMCPlayerId()) || spectators.contains(player)) {
            queueCount--;
        }

        // Once redteam + blueteam = queuecount, running will be TRUE
        if (blueTeam.getSize() + redTeam.getSize() < queueCount) {
            return;
        }
        
        applyMultipliers();
        for (MissileWarsPlayer mwp : redTeam.getMembers()) {
            mwp.initDeck(false, this, true);
        }
        
        for (MissileWarsPlayer mwp : blueTeam.getMembers()) {
            mwp.initDeck(false, this, false);
        }
        running = true;
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
     * Instantly end a game, with a winning team
     * 
     * @param winningTeam, null for tie
     */
    public void endGame(MissileWarsTeam winningTeam) {
        endGame(winningTeam, 0);
    }

    /**
     * End a MissileWars game with a winning team
     *
     * @param winningTeam the winning team, null for tie
     * @param how long to wait for a tie in ticks
     */
    public void endGame(MissileWarsTeam winningTeam, int delay) {
        // Ignore if game isn't running
        if (!running) {
            return;
        }
        
        // Players shouldn't be able to play anymore
        for (MissileWarsPlayer player : players.values()) {
            player.stopDeck();
            Player p = player.getMCPlayer();
            p.setGameMode(GameMode.SPECTATOR);
            p.removePotionEffect(PotionEffectType.GLOWING);
        }
        
        // Schedule tie wait. If endGame gets called from somewhere else,
        // the scheduled task will ignore endGame since running will be false
        if (delay > 0) {
            waitingForTie = true;
            tasks.add(ConfigUtils.schedule(delay, () -> endGame(winningTeam)));
            return;
        }             

        // Cancel all tasks
        cancelTasks();
        leftPlayers.clear();
        running = false;
        resetting = true;
        waitingForTie = false;
        
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
            SchematicManager.spawnNBTStructure(null, "pegasus-0", winningTeam.getSpawn(), isRed, false, false);
        }

        // Send messages, calculate winning stats
        discordChannel.sendMessage(discordMessage).queue();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> calculateStats(winningTeam));

        // Remove all players after a short time, then reset the world a bit after
        int waitTime = plugin.getConfig().getInt("victory-wait-time") * 20;
        if (players.size() > 0) {
            ConfigUtils.schedule(waitTime, () -> removePlayers());
        }
        ConfigUtils.schedule(waitTime + 40, () -> resetWorld());
    }
    
    // Calculate and store all player stats from the game
    protected abstract void calculateStats(MissileWarsTeam winningTeam);
    
    /** Remove Players from the map */
    public void removePlayers() {
        int cap = plugin.getConfig().getInt("arena-cap");
        for (MissileWarsPlayer mwPlayer : new HashSet<>(players.values())) {
            // If DOES NOT HAVE the permission, then we DO REQUEUE the player
            // Also only requeue if capacity is 20
            List<Arena> togo = plugin.getArenaManager().getLoadedArenas(gamemode);
            Player player = mwPlayer.getMCPlayer();
            if (!player.hasPermission("umw.disablerequeue") && capacity == cap) {
                Boolean success = false;
                for (Arena arena : togo) {
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
        ConfigUtils.schedule(1, () -> {
            for (Player player : getWorld().getPlayers()) {
                player.teleport(ConfigUtils.getSpawnLocation());
            }
        });
    }

    /** Reset the arena world */
    protected void resetWorld() {
        resetWorld(null);
    }
    
    /**
     * Reset the arena world
     * 
     * @param callback to run when reset completes
     */
    protected void resetWorld(DBCallback callback) {
        plugin.log("Resetting arena " + name + "...");
        int maxHeight = plugin.getConfig().getInt("max-height");
        int maxX = plugin.getConfig().getInt("barrier.center.x") - 1;
        tasks.add(Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SchematicManager.setAir(-250, -64, -250, maxX, maxHeight, 250, getWorld());
            plugin.log("First arena clear finished");
            SchematicManager.setAir(-250, -64, -250, maxX, maxHeight, 250, getWorld(), false);
            resetting = false;
            plugin.log("Reset completed");
            
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.onQueryDone(null));
            }
        }));
        
        startTime = null;
        voteManager = new VoteManager(gamemode);
        startSpectatorActionBarTask();
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
            Vector spawnVec = SchematicManager.getVector(schematicConfig, "lobby.spawn");
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
        for (MissileWarsPlayer player : players.values()) {
        	Player mcPlayer = player.getMCPlayer();
            ConfigUtils.sendConfigMessage(path, mcPlayer, this, focus != null ? focus.getMCPlayer() : null);
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
