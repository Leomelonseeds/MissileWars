package io.github.vhorvath2010.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import io.github.vhorvath2010.missilewars.schematics.VoidChunkGenerator;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import io.github.vhorvath2010.missilewars.teams.MissileWarsTeam;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import io.github.vhorvath2010.missilewars.utilities.InventoryUtils;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.exception.NPCLoadException;

/** Represents a MissileWarsArena where the game will be played. */
public class Arena implements ConfigurationSerializable {

    /** Comparator to sort by active players */
    public static Comparator<Arena> byPlayers = Comparator.comparing(a -> a.getNumPlayers());

    /** The arena name. */
    private String name;
    /** The map for the arena. */
    private String mapName;
    /** The gamemode type for the map for the arena. */
    private String mapType;
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
    /** Whether the arena is currently resetting the world. */
    private boolean resetting;
    /** The current number of votes for each map. */
    private Map<String, Integer> mapVotes;
    /** Connect players and their votes. */
    private Map<UUID, String> playerVotes;

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
        setupMapVotes();
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
        setupMapVotes();
    }

    /**
     * Setup map voting for the next game.
     */
    public void setupMapVotes() {
        mapType = "classic";

        // Add maps in map type to voting pool
        mapVotes = new HashMap<>();
        FileConfiguration mapConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "maps.yml");
        for (String mapName : mapConfig.getConfigurationSection(mapType).getKeys(false)) {
            mapVotes.put(mapName, 0);
        }
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
     * Get the current map type for this arena.
     *
     * @return the current map type for this arena
     */
    public String getMapType() {
        return mapType;
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
     * Get the number of minutes remaining when chaos time activates.
     *
     * @return the number of minutes remaining when chaos time activates
     */
    public static int getChaosTime() {
        return MissileWarsPlugin.getPlugin().getConfig().getInt("chaos-mode.time-left");
    }

    /**
     * Generate a map given the map name.
     *
     * @param mapName the name of the map
     * @return true if the map successfully generated, otherwise false
     */
    public boolean generateMap(String mapName) {
        return SchematicManager.spawnFAWESchematic(mapType + "." + mapName, getWorld(), true);
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
        int totalMins = MissileWarsPlugin.getPlugin().getConfig().getInt("game-length");
        long minsTaken = Duration.between(startTime, LocalDateTime.now()).toMinutes();
        return totalMins - minsTaken;
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
     * Attempt to add a player to the Arena.
     *
     * @param player the player
     * @return true if the player joined the Arena, otherwise false
     */
    public boolean joinPlayer(Player player) {
        
        ConfigUtils.sendConfigMessage("messages.join-arena", player, this, null);

        // Ensure world isn't resetting
        if (resetting) {
            return false;
        }
        
        for (MissileWarsPlayer mwPlayer : players) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-others", mwPlayer.getMCPlayer(), null, player);
        }

        ConfigUtils.sendConfigMessage("messages.joined-arena", player, this, null);
        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        discordChannel.sendMessage(":arrow_backward: " + player.getName() + " left and joined arena " + this.getName()).queue();

        player.setHealth(20);
        player.setFoodLevel(20);
        players.add(new MissileWarsPlayer(player.getUniqueId()));
        player.teleport(getPlayerSpawn(player));
        player.setBedSpawnLocation(getPlayerSpawn(player), true);
        player.setGameMode(GameMode.ADVENTURE);
        InventoryUtils.clearInventory(player);

        for (Player worldPlayer : Bukkit.getWorld("world").getPlayers()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-lobby", worldPlayer, this, player);
        }
        
        // Check for game start
        int minPlayers = MissileWarsPlugin.getPlugin().getConfig().getInt("minimum-players");
        if (getNumPlayers() >= minPlayers) {
            scheduleStart();
        }

        // Open map voting inventory
        openMapVote(player);

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
        // Remove player from all teams and queues
        MissileWarsPlayer toRemove = new MissileWarsPlayer(uuid);
        players.remove(toRemove);
        
        for (MissileWarsPlayer mwPlayer : players) {
            ConfigUtils.sendConfigMessage("messages.leave-arena-others", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
        }

        // Cancel tasks if starting and below min players
        int minPlayers = MissileWarsPlugin.getPlugin().getConfig().getInt("minimum-players");
        if (!running && startTime != null && players.size() < minPlayers) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
            startTime = null;
        }

        spectators.remove(toRemove);
        blueQueue.remove(toRemove);
        redQueue.remove(toRemove);
        if (redTeam != null) {
            redTeam.removePlayer(toRemove);
            blueTeam.removePlayer(toRemove);
        }

        // Run proper clearing commands on the player
        Player mcPlayer = toRemove.getMCPlayer();
        if (mcPlayer != null) {
        	mcPlayer.teleport(ConfigUtils.getSpawnLocation());
        	mcPlayer.setGameMode(GameMode.ADVENTURE);
        	InventoryUtils.loadInventory(mcPlayer);
            ConfigUtils.sendConfigMessage("messages.leave-arena", mcPlayer, this, null);
            
            // Notify discord
            TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
            discordChannel.sendMessage(":arrow_forward: " + mcPlayer.getName() + " rejoined lobby from arena " + this.getName()).queue();
        }

        // Check for empty team win condition
        if (running && redTeam != null && blueTeam != null) {
            if (redTeam.getSize() <= 0 && blueTeam.getSize() <= 0) {
                endGame(null);
            } else if (redTeam.getSize() <= 0) {
                announceMessage("messages.red-team-empty", null);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (running && redTeam.getSize() <= 0) {
                            endGame(blueTeam);
                        }
                    }
                }.runTaskLater(MissileWarsPlugin.getPlugin(), 60 * 20L);
            } else if (blueTeam.getSize() <= 0) {
                announceMessage("messages.blue-team-empty", null);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (running && blueTeam.getSize() <= 0) {
                            endGame(redTeam);
                        }
                    }
                }.runTaskLater(MissileWarsPlugin.getPlugin(), 60 * 20L);
            }
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
     * Remove the given player from the spectators list.
     *
     * @param player the player
     */
    public void removeSpectator(MissileWarsPlayer player) {
        if (spectators.remove(player)) {
            announceMessage("messages.spectate-leave-others", player);
            player.getMCPlayer().setGameMode(GameMode.ADVENTURE);
            player.getMCPlayer().teleport(getPlayerSpawn(player.getMCPlayer()));
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
                    if (!player.getMCPlayer().isOp() && redTeam.getSize() - blueTeam.getSize() >= 1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-error", player.getMCPlayer(), this, null);
                    } else if (!player.getMCPlayer().hasPermission("umw.joinfull") && redTeam.getSize() >= getCapacity() / 2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                    } else {
                        blueTeam.removePlayer(player);
                        redTeam.addPlayer(player);
                        redTeam.giveItems(player);
                        player.giveDeckGear();
                        removeSpectator(player);
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
                if (!running) {
                    if (!blueQueue.contains(player)) {
                        redQueue.remove(player);
                        blueQueue.add(player);
                        ConfigUtils.sendConfigMessage("messages.queue-waiting", player.getMCPlayer(), this, null);
                        removeSpectator(player);
                    }
                } else {
                    if (!player.getMCPlayer().isOp() && blueTeam.getSize() - redTeam.getSize() >= 1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-error", player.getMCPlayer(), this, null);
                    } else if (!player.getMCPlayer().hasPermission("umw.joinfull") && blueTeam.getSize() >= getCapacity() / 2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                    } else {
                        redTeam.removePlayer(player);
                        blueTeam.addPlayer(player);
                        blueTeam.giveItems(player);
                        player.giveDeckGear();
                        removeSpectator(player);
                        announceMessage("messages.queue-join-blue", player);
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
                if (running && !blueTeam.containsPlayer(uuid) && !redTeam.containsPlayer(uuid)) {
                    announceMessage("messages.spectate-join-others", player);
                    spectators.add(player);
                    redQueue.remove(player);
                    blueQueue.remove(player);
                    Player mcPlayer = player.getMCPlayer();
                    mcPlayer.setGameMode(GameMode.SPECTATOR);
                } else {
                    player.getMCPlayer().sendMessage(ConfigUtils.getConfigText("messages.spectate-join-fail",
                            player.getMCPlayer(), null, null));
                }
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
            announceMessage(startMsg, null);
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
        mapName = "default-map";
        List<String> mapsWithTopVotes = new LinkedList<>();
        for (String map : mapVotes.keySet()) {
            if (mapsWithTopVotes.isEmpty()) {
                mapsWithTopVotes.add(map);
            } else {
                String previousTop = mapsWithTopVotes.get(0);
                if (mapVotes.get(previousTop) == mapVotes.get(map)) {
                    mapsWithTopVotes.add(map);
                } else if (mapVotes.get(previousTop) < mapVotes.get(map)) {
                    mapsWithTopVotes.clear();
                    mapsWithTopVotes.add(map);
                }
            }
        }
        if (!mapsWithTopVotes.isEmpty()) {
            // Set map to random map with top votes
            mapName = mapsWithTopVotes.get(new Random().nextInt(mapsWithTopVotes.size()));
        }

        // Generate map.
        if (!generateMap(mapName)) {
        	announceMessage("messages.map-failed", null);
        	return false;
        } else {
        	announceMessage("messages.starting", null);
        }

        // Acquire red and blue spawns
        FileConfiguration mapConfig = ConfigUtils.getConfigFile(plugin.getDataFolder()
                .toString(), "maps.yml");
        Vector blueSpawnVec = SchematicManager.getVector(mapConfig, mapType + "." + mapName + ".blue-spawn");
        Location blueSpawn = new Location(getWorld(), blueSpawnVec.getX(), blueSpawnVec.getY(), blueSpawnVec.getZ());
        Vector redSpawnVec = SchematicManager.getVector(mapConfig, mapType + "." + mapName + ".red-spawn");
        Location redSpawn = new Location(getWorld(), redSpawnVec.getX(), redSpawnVec.getY(), redSpawnVec.getZ());
        redSpawn.setYaw(180);
        blueSpawn.setWorld(getWorld());
        redSpawn.setWorld(getWorld());

        // Setup scoreboard and teams
        blueTeam = new MissileWarsTeam(ChatColor.BLUE + "" + ChatColor.BOLD + "Blue", this, blueSpawn);
        redTeam = new MissileWarsTeam(ChatColor.RED + "" + ChatColor.BOLD + "Red",this , redSpawn);

        // Assign players to teams based on queue (which removes their items)
        Set<MissileWarsPlayer> toAssign = new HashSet<>(players);
        double maxSize = Math.ceil((double) (players.size() - spectators.size()) / 2);
        
        // Teleport teams slightly later to wait for map generation
        tasks.add(new BukkitRunnable() {
        	@Override
        	public void run() {
        		// Assign queued players
        		while (!blueQueue.isEmpty() || !redQueue.isEmpty()) {
		            if (!redQueue.isEmpty()) {
		                MissileWarsPlayer toAdd = redQueue.remove();
		                if (redTeam.getSize() < maxSize) {
		                	redTeam.addPlayer(toAdd);
			                toAssign.remove(toAdd);
		                }     
		            }
		            if (!blueQueue.isEmpty()) {
		                MissileWarsPlayer toAdd = blueQueue.remove();           
		                if (blueTeam.getSize() < maxSize) {
		                	blueTeam.addPlayer(toAdd);
		                	toAssign.remove(toAdd);
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
		                blueTeam.addPlayer(player);
		            } else {
		                redTeam.addPlayer(player);
		            }
		        }

		        // Start deck distribution for each team and send messages
		        redTeam.scheduleDeckItems();
		        redTeam.broadcastConfigMsg("messages.classic-start", null);
		        redTeam.distributeGear();
                redTeam.sendTitle("classic-start");
                redTeam.sendSound("game-start");
		        blueTeam.scheduleDeckItems();
		        blueTeam.distributeGear();
		        blueTeam.broadcastConfigMsg("messages.classic-start", null);
                blueTeam.sendTitle("classic-start");
                blueTeam.sendSound("game-start");
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

    /** Load this Arena's world from the disk. */
    public void loadWorldFromDisk() {
        WorldCreator arenaCreator = new WorldCreator("mwarena_" + name);
        arenaCreator.generator(new VoidChunkGenerator()).createWorld().setAutoSave(false);
        // Load Citizens NPCs
        try {
            ((Citizens) CitizensAPI.getPlugin()).reload();
        } catch (NPCLoadException e) {
            e.printStackTrace();
        }
    }

    /** Reset the arena world. */
    public void resetWorld() {
        Bukkit.unloadWorld(getWorld(), false);
        loadWorldFromDisk();
        resetting = false;
        setupMapVotes();
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

        // Cancel all tasks
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        running = false;
        redTeam.stopDeckItems();
        blueTeam.stopDeckItems();

        // Find players with most deaths and kills
        List<MissileWarsPlayer> mostKills = new ArrayList<MissileWarsPlayer>();
        List<MissileWarsPlayer> mostDeaths = new ArrayList<MissileWarsPlayer>();
        for (MissileWarsPlayer player : players) {
            if (mostKills.isEmpty() || mostKills.get(0).getKills() < player.getKills()) {
                mostKills.clear();
                mostKills.add(player);
            } else if (mostKills.get(0).getKills() == player.getKills()) {
                mostKills.add(player);
            }
            if (mostDeaths.isEmpty() || mostDeaths.get(0).getDeaths() < player.getDeaths()) {
                mostDeaths.clear();
                mostDeaths.add(player);
            } else if (mostDeaths.get(0).getDeaths() == player.getDeaths()) {
                mostDeaths.add(player);
            }
        }
        
        // Produce most kills/deaths list
        List<String> mostKillsList = new ArrayList<String>();
        for (MissileWarsPlayer player : mostKills) {
            mostKillsList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_kills = String.join(", ", mostKillsList);
        
        List<String> mostDeathsList = new ArrayList<String>();
        for (MissileWarsPlayer player : mostDeaths) {
            mostDeathsList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_deaths = String.join(", ", mostDeathsList);
        
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
            List<String> blueList = new ArrayList<String>();
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
            List<String> redList = new ArrayList<String>();
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
        
        List<String> winningMessages = ConfigUtils.getConfigTextList("messages.classic-end", null, null, null);

        for (MissileWarsPlayer player : players) {
            player.getMCPlayer().setGameMode(GameMode.SPECTATOR);
            for (String s : winningMessages) {
                s = s.replaceAll("%umw_winning_team%", winner);
                s = s.replaceAll("%umw_most_kills_amount%", Integer.toString(mostKills.get(0).getKills()));
                s = s.replaceAll("%umw_most_deaths_amount%", Integer.toString(mostDeaths.get(0).getDeaths()));
                s = s.replaceAll("%umw_most_kills%", most_kills);
                s = s.replaceAll("%umw_most_deaths%", most_deaths);
                player.getMCPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', s));
            }
        }

        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        long waitTime = plugin.getConfig().getInt("victory-wait-time") * 20L;

        // Remove all players after a short time or immediately if none exist
        resetting = true;
        if (plugin.isEnabled() && players.size() > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    removePlayers();
                    startTime = null;
                }
            }.runTaskLater(plugin, waitTime);
            new BukkitRunnable() {
                @Override
                public void run() {
                    resetWorld();
                }
            }.runTaskLater(plugin, waitTime + 40L);
        } else {
            removePlayers();
            resetWorld();
            startTime = null;
        }
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
     * @param path the path to the configurable message
     */
    public void announceMessage(String path, MissileWarsPlayer focus) {
        for (MissileWarsPlayer player : players) {
        	Player mcPlayer = player.getMCPlayer();
            ConfigUtils.sendConfigMessage(path, mcPlayer, this, focus != null ? focus.getMCPlayer() : null);
        }
        for (MissileWarsPlayer player : spectators) {
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
        for (MissileWarsPlayer spectator : spectators) {
            if (spectator.getMCPlayer() != null) {
                spectator.getMCPlayer().setLevel(level);
            }
        }
    }

    /**
     * Register the breaking of a portal at a location in this Arena.
     *
     * @param location the location
     */
    public void registerPortalBreak(Location location) {
        // Ignore if game not running
        if (!running) {
            return;
        }

        // Check if portal broke at blue or red z
        int z = location.getBlockZ();
        if (z == Math.round(ConfigUtils.getMapNumber("classic", mapName, "portal.blue-z"))) {
            // Register breaking of blue team's portal and send titles
            if (blueTeam.registerPortalBreak(location) && blueTeam.hasLivingPortal()) {
                blueTeam.sendTitle("own-portal-destroyed");
                redTeam.sendTitle("enemy-portal-destroyed");
            }
        } else if (z == Math.round(ConfigUtils.getMapNumber("classic", mapName, "portal.red-z"))) {
            if (redTeam.registerPortalBreak(location) && redTeam.hasLivingPortal()) {
                redTeam.sendTitle("own-portal-destroyed");
                blueTeam.sendTitle("enemy-portal-destroyed");
            }
        }

        // Check if either team's last portal has been broken
        if (!redTeam.hasLivingPortal()) {
            endGame(blueTeam);
        } else if (!blueTeam.hasLivingPortal()) {
            endGame(redTeam);
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
        if (!running) {
            return;
        }

        // Only attempt blue update if red unchanged
        if (location.getBlockZ() > 0) {
            redTeam.registerShieldBlockUpdate(location, place);   
        } else {
            blueTeam.registerShieldBlockUpdate(location, place);
        }
    }

    /**
     * Get the red team's current shield health.
     *
     * @return the red team's current shield health
     */
    public double getRedShieldHealth() {
        // Full health if game not running
        if (!running) {
            return 100.00;
        }

        // Acquire shield data
        int totalBlocks = redTeam.getShieldVolume();
        int broken = redTeam.getShieldBlocksBroken();

        return 100 * ((totalBlocks - broken) / (double) totalBlocks);
    }

    /**
     * Get the blue team's current shield health as a percentage.
     *
     * @return the blue team's current shield health
     */
    public double getBlueShieldHealth() {
        // Full health if game not running
        if (!running) {
            return 100.00;
        }

        // Acquire shield data
        int totalBlocks = blueTeam.getShieldVolume();
        int broken = blueTeam.getShieldBlocksBroken();

        return 100 * ((totalBlocks - broken) / (double) totalBlocks);
    }

    /**
     * Open the map voting GUI for a player.
     *
     * @param player the player
     */
    public void openMapVote(Player player) {
        Inventory mapInv = Bukkit.createInventory(null, 27, Component.translatable("Vote for a Map"));
        for (String mapName : mapVotes.keySet()) {
            ItemStack mapItem = new ItemStack(Material.PAPER);
            ItemMeta mapItemMeta = mapItem.getItemMeta();
            mapItemMeta.setDisplayName(ConfigUtils.getMapText(mapType, mapName, "name"));
            List<String> lore = new LinkedList<>();
            lore.add(ChatColor.GRAY + "Left click to vote for this map");
            mapItemMeta.setLore(lore);
            mapItem.setItemMeta(mapItemMeta);
            mapInv.addItem(mapItem);
        }
        player.openInventory(mapInv);
    }

    /**
     * Register a player's map vote, removing any of their previous votes.
     *
     * @param id the player's UUID
     * @param map the map the player is voting for
     */
    public void registerVote(UUID id, String map) {
        if (playerVotes.containsKey(id)) {
            String previousVote = playerVotes.remove(id);
            mapVotes.put(previousVote, mapVotes.get(previousVote) - 1);
        }
        mapVotes.put(map, mapVotes.get(map) + 1);
        playerVotes.put(id, map);
    }

}
