package io.github.vhorvath2010.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import io.github.vhorvath2010.missilewars.teams.MissileWarsTeam;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

/** Represents a MissileWarsArena where the game will be played. */
public class Arena implements ConfigurationSerializable {

    /** The arena name. */
    private String name;
    /** The max number of players for this arena. */
    private int capacity;
    /** The list of all players currently in the arena. */
    private Set<MissileWarsPlayer> players;
    /** The list of all spectators currently in the arena. */
    private Set<MissileWarsPlayer> spectators;
    /** The queue to join the red team. Active before the game. */
    private Queue<MissileWarsPlayer> redQueue;
    /** The queue to join the blue team. Active before the game. */
    private Queue<MissileWarsPlayer> blueQueue;
    /** The red team. */
    private MissileWarsTeam redTeam;
    /** The blue team. */
    private MissileWarsTeam blueTeam;
    /** The start time of the game. In the future if game is yet to start, otherwise, in the past. */
    private LocalDateTime startTime;
    /** Whether a game is currently running */
    private boolean running;
    /** List of currently running tasks. */
    private List<BukkitTask> tasks;
    /** Whether the arena is in chaos mode. */
    private boolean inChaos;

    /**
     * Create a new Arena with a given name and max capacity.
     *
     * @param name the name
     * @param capacity the max capacity
     */
    public Arena(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        players = new HashSet<>();
        spectators = new HashSet<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
        tasks = new LinkedList<>();
    }

    /**
     * Serialize the Arena for yml storage.
     *
     * @return a map mapping the variables as string to their objects
     */
    public Map<String, Object> serialize() {
        Map<String, Object> serializedArena = new HashMap<>();
        serializedArena.put("name", name);
        serializedArena.put("capacity", capacity);
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
        players = new HashSet<>();
        spectators = new HashSet<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
        tasks = new LinkedList<>();
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
     * Check if the game is currently running.
     *
     * @return whether a game is currently running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the number of minutes remaining when chaos time activates.
     *
     * @return the number of minutes remaining when chaos time activates
     */
    public static int getChaosTime() {
        return ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "default-settings.yml").getInt("chaos-mode.time-left");
    }

    /**
     * Generate a map given the map name.
     *
     * @param mapName the name of the map
     * @return true if the map successfully generated, otherwise false
     */
    public boolean generateMap(String mapName) {
        return SchematicManager.spawnFAWESchematic(mapName, getWorld(), true);
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
        for (MissileWarsPlayer player : redQueue) {
            if (player.getMCPlayerId().equals(uuid)) {
                return ChatColor.RED + "red" + ChatColor.RESET;
            }
        }
        for (MissileWarsPlayer player : blueQueue) {
            if (player.getMCPlayerId().equals(uuid)) {
                return ChatColor.BLUE + "blue" + ChatColor.RESET;
            }
        }
        if (redTeam == null || blueTeam == null) {
            return "no team";
        }
        if (redTeam.containsPlayer(uuid)) {
            return ChatColor.RED + "red" + ChatColor.RESET;
        }
        if (blueTeam.containsPlayer(uuid)) {
            return ChatColor.BLUE + "blue" + ChatColor.RESET;
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
     * Get the number of minutes remaining in the game.
     *
     * @return the number of minutes remaining in the game
     */
    public long getMinutesRemaining() {
        if (startTime == null) {
            return 0;
        }
        int totalMins = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "default-settings.yml").getInt("game-length");
        long minsTaken = Duration.between(startTime, LocalDateTime.now()).toMinutes();
        return totalMins - minsTaken;
    }

    /**
     * Get the number of player currently in the game.
     *
     * @return the number of player currently in the game
     */
    public int getNumPlayers() {
        return players.size() - spectators.size();
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
     * Attempt to add a player to the Arena.
     *
     * @param player the player
     * @return true if the player joined the Arena, otherwise false
     */
    public boolean joinPlayer(Player player) {
        if (getNumPlayers() >= capacity) {
            return false;
        }
        player.setHealth(20);
        player.setFoodLevel(20);
        players.add(new MissileWarsPlayer(player.getUniqueId()));
        player.teleport(getPlayerSpawn(player));
        player.setBedSpawnLocation(getPlayerSpawn(player), true);
        player.setGameMode(GameMode.ADVENTURE);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "clear " + player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:item replace entity " + player.getName() + " armor.legs with air");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:item replace entity " + player.getName() + " armor.chest with air");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:item replace entity " + player.getName() + " armor.feet with air");
        // Check for game start
        int minPlayers = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "default-settings.yml").getInt("minimum-players");
        if (getNumPlayers() >= minPlayers) {
            scheduleStart();
        }
        return true;
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
     * Remove a player with a given UUID from the game.
     *
     * @param uuid the UUID of the player
     */
    public void removePlayer(UUID uuid) {
        MissileWarsPlayer toRemove = new MissileWarsPlayer(uuid);
        players.remove(toRemove);
        spectators.remove(toRemove);
        blueQueue.remove(toRemove);
        redQueue.remove(toRemove);
        if (redTeam != null) {
            redTeam.removePlayer(toRemove);
            blueTeam.removePlayer(toRemove);
        }
        Player mcPlayer = toRemove.getMCPlayer();
        if (mcPlayer != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + mcPlayer.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:item replace entity " + mcPlayer.getName() + " armor.legs with air");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:item replace entity " + mcPlayer.getName() + " armor.chest with air");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:item replace entity " + mcPlayer.getName() + " armor.feet with air");
            ConfigUtils.sendConfigMessage("messages.leave-arena", mcPlayer, this, null);
        }
    }

    /**
     * Remove the given player from the spectators list.
     *
     * @param player the player
     */
    private void removeSpectator(MissileWarsPlayer player) {
        if (spectators.remove(player)) {
            String joinMsg = "messages.spectate-join-others";
            announceMessage(joinMsg);
        }
    }

    /**
     * Enqueue a player with a given UUID to the red team.
     *
     * @param uuid the Player's UUID
     */
    public void enqueueRed(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                if (!running) {
                    if (!redQueue.contains(player)) {
                        blueQueue.remove(player);
                        redQueue.add(player);
                        ConfigUtils.sendConfigMessage("messages.queue-waiting", player.getMCPlayer(), this, null);
                        removeSpectator(player);
                    }
                } else {
                    if (redTeam.getSize() - blueTeam.getSize() >= 1) {
                        player.getMCPlayer().sendMessage(ConfigUtils.getConfigText("messages.queue-join-error",
                                null, this, null));
                    } else {
                        redTeam.addPlayer(player);
                        player.giveDeckGear();
                        removeSpectator(player);
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
                if (!running) {
                    if (!blueQueue.contains(player)) {
                        redQueue.remove(player);
                        blueQueue.add(player);
                        ConfigUtils.sendConfigMessage("messages.queue-waiting", player.getMCPlayer(), this, null);
                        removeSpectator(player);
                    }
                } else {
                    if (blueTeam.getSize() - redTeam.getSize() >= 1) {
                        player.getMCPlayer().sendMessage(ConfigUtils.getConfigText("messages.queue-join-error", null,
                                this, null));
                    } else {
                        blueTeam.addPlayer(player);
                        player.giveDeckGear();
                        removeSpectator(player);
                    }
                }
                break;
            }
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
                spectators.add(player);
                redQueue.remove(player);
                blueQueue.remove(player);
                Player mcPlayer = player.getMCPlayer();
                mcPlayer.setGameMode(GameMode.SPECTATOR);
                String joinMsg = "messages.spectate-join-others";
                announceMessage(joinMsg);
                break;
            }
        }
    }

    /** Schedule the start of the game based on the config time. */
    public void scheduleStart() {
    	MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        int secCountdown = plugin.getConfig().getInt("lobby-wait-time");
        // Schedule the start of the game if not already running
        if (startTime == null) {
            startTime = LocalDateTime.now().plusSeconds(secCountdown);
            String startMsg = "messages.lobby-countdown-start";
            announceMessage(startMsg);
            tasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    start();
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
                            announceMessage(startMsg);
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
        
        // Generate map.
        if (!generateMap("default-map")) {
        	announceMessage("messages.map-failed");
        	return false;
        } else {
        	announceMessage("messages.starting");
        }

        // Acquire red and blue spawns
        FileConfiguration mapConfig = ConfigUtils.getConfigFile(plugin.getDataFolder()
                .toString(), "maps.yml");
        Vector blueSpawnVec = SchematicManager.getVector(mapConfig, "default-map.blue-spawn");
        Location blueSpawn = new Location(getWorld(), blueSpawnVec.getX(), blueSpawnVec.getY(), blueSpawnVec.getZ());
        Vector redSpawnVec = SchematicManager.getVector(mapConfig, "default-map.red-spawn");
        Location redSpawn = new Location(getWorld(), redSpawnVec.getX(), redSpawnVec.getY(), redSpawnVec.getZ());
        redSpawn.setYaw(180);
        blueSpawn.setWorld(getWorld());
        redSpawn.setWorld(getWorld());

        // Setup scoreboard and teams
        blueTeam = new MissileWarsTeam(ChatColor.BLUE + "" + ChatColor.BOLD + "Blue", this, blueSpawn);
        redTeam = new MissileWarsTeam(ChatColor.RED + "" + ChatColor.BOLD + "Red",this , redSpawn);

        // Assign players to teams based on queue (which removes their items)
        Set<MissileWarsPlayer> toAssign = new HashSet<>(players);
        
        // Teleport teams slightly later to wait for map generation
        tasks.add(new BukkitRunnable() {
        	@Override
        	public void run() {
		        do {
		            if (!redQueue.isEmpty()) {
		                MissileWarsPlayer toAdd = redQueue.remove();
		                redTeam.addPlayer(toAdd);
		                toAssign.remove(toAdd);
		            }
		            if (!blueQueue.isEmpty()) {
		                MissileWarsPlayer toAdd = blueQueue.remove();
		                blueTeam.addPlayer(toAdd);
		                toAssign.remove(toAdd);
		            }
		        } while (!blueQueue.isEmpty() && !redQueue.isEmpty());
		
		        // Assign remaining players
		        for (MissileWarsPlayer player : toAssign) {
		            // Ignore if player is a spectator
		            if (spectators.contains(player)) {
		                continue;
		            }
		            if (blueTeam.getSize() <= redTeam.getSize()) {
		                blueTeam.addPlayer(player);
		            } else {
		                redTeam.addPlayer(player);
		            }
		        }

		        // Start deck distribution for each team and send messages
		        redTeam.scheduleDeckItems();
		        redTeam.broadcastConfigMsg("messages.classic-start", null);
		        redTeam.distributeGear();
		        blueTeam.scheduleDeckItems();
		        blueTeam.distributeGear();
		        blueTeam.broadcastConfigMsg("messages.classic-start", null);
        	}
        }.runTaskLater(plugin, 20));

        // Setup game timers
        // Game start
        startTime = LocalDateTime.now();
        long gameLength = getMinutesRemaining();
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                // End game in a tie
                endGame(null);
            }
        }.runTaskLater(plugin, gameLength * 20 * 60));

        // Chaos time start
        int chaosStart = getChaosTime();
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                inChaos = true;
                blueTeam.setChaosMode(true);
                redTeam.setChaosMode(true);
                redTeam.broadcastConfigMsg("messages.chaos-mode", null);
                blueTeam.broadcastConfigMsg("messages.chaos-mode", null);
            }
        }.runTaskLater(plugin, (gameLength - chaosStart) * 20 * 60));

        running = true;
        return true;
    }

    /** Remove Players from the map. */
    public void removePlayers() {
        for (MissileWarsPlayer player : new HashSet<>(players)) {
            removePlayer(player.getMCPlayerId());
        }
        for (MissileWarsPlayer player : new HashSet<>(spectators)) {
            removePlayer(player.getMCPlayerId());
        }
    }

    /** Reset the arena world. */
    public void resetWorld() {
        Bukkit.unloadWorld(getWorld(), false);
        new WorldCreator("mwarena_" + name).createWorld().setAutoSave(false);
    }

    /**
     * End a MissileWars game with a winning team
     *
     * @param winningTeam the winning team
     */
    public void endGame(MissileWarsTeam winningTeam) {
        // Cancel all tasks
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        running = false;
        redTeam.stopDeckItems();
        blueTeam.stopDeckItems();

        // Stop game and send messages
        redTeam.broadcastConfigMsg("messages.classic-end", null);
        blueTeam.broadcastConfigMsg("messages.classic-end", null);
        
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();

        // Remove all players after a short time
        new BukkitRunnable() {
            @Override
            public void run() {
                removePlayers();
                resetWorld();
                startTime = null;
            }
        }.runTaskLater(plugin, plugin.getConfig().getInt("victory-wait-time"));
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
        	FileConfiguration schematicConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                    .toString(), "maps.yml");
            Vector spawnVec = SchematicManager.getVector(schematicConfig, "lobby.spawn");
            Location spawnLoc = new Location(Bukkit.getWorld("mwarena_" + name), spawnVec.getX(), spawnVec.getY(), spawnVec.getZ());
            spawnLoc.setYaw(90);
            return spawnLoc;
        }
    }

    /**
     * Send a message to all players in the arena.
     *
     * @param msg the message
     */
    public void announceMessage(String path) {
        for (MissileWarsPlayer player : players) {
        	Player mcPlayer = player.getMCPlayer();
            ConfigUtils.sendConfigMessage(path, mcPlayer, this, null);
        }
        for (MissileWarsPlayer player : spectators) {
        	Player mcPlayer = player.getMCPlayer();
            ConfigUtils.sendConfigMessage(path, mcPlayer, this, null);
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
        for (MissileWarsPlayer spectator : spectators) {
            if (spectator.getMCPlayer() != null) {
                spectator.getMCPlayer().setLevel(level);
            }
        }
    }

}
