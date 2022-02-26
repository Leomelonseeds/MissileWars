package io.github.vhorvath2010.missilewars.arenas;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import io.github.vhorvath2010.missilewars.teams.MissileWarsTeam;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Represents a MissileWarsArena where the game will be played. */
public class Arena implements ConfigurationSerializable {

    /** The arena name. */
    private String name;
    /** The max number of players for this arena. */
    private int capacity;
    /** The current selected map for the arena. */
    private GameMap map;
    /** The list of all players currently in the arena. */
    private List<MissileWarsPlayer> players;
    /** The list of all spectators currently in the arena. */
    private List<MissileWarsPlayer> spectators;
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

    /**
     * Create a new Arena with a given name and max capacity.
     *
     * @param name the name
     * @param capacity the max capacity
     */
    public Arena(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        players = new ArrayList<>();
        spectators = new ArrayList<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
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
        players = new ArrayList<>();
        spectators = new ArrayList<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
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
    private World getWorld() {
        return Bukkit.getWorld("mwarena_" + name);
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
        return SchematicManager.spawnFAWESchematic(mapName, getWorld());
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
            return ChatColor.RED + "red";
        }
        if (blueTeam.containsPlayer(uuid)) {
            return ChatColor.BLUE + "blue";
        }
        for (MissileWarsPlayer missileWarsPlayer : redQueue) {
            if (uuid.equals(missileWarsPlayer.getMCPlayerId())) {
                return ChatColor.RED + "red";
            }
        }
        for (MissileWarsPlayer missileWarsPlayer : blueQueue) {
            if (uuid.equals(missileWarsPlayer.getMCPlayerId())) {
                return ChatColor.BLUE + "blue";
            }
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
        players.add(new MissileWarsPlayer(player));
        player.teleport(Bukkit.getWorld("mwarena_" + name).getSpawnLocation());
        return true;
    }

}
