package com.leomelonseeds.missilewars.arenas;

import java.io.File;
import java.io.IOException;
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
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.earth2me.essentials.Essentials;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.arenas.tracker.Tracker;
import com.leomelonseeds.missilewars.arenas.votes.VoteManager;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.VoidChunkGenerator;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

import eu.decentsoftware.holograms.api.DHAPI;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.github.a5h73y.parkour.Parkour;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.citizensnpcs.trait.CommandTrait;
import net.citizensnpcs.trait.Gravity;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SheepTrait;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.VillagerProfession;

/** Represents a MissileWarsArena where the game will be played. */
public abstract class Arena implements ConfigurationSerializable {

    /** Comparator for sorting arenas */
    public static Comparator<Arena> byCapacity = Comparator.comparing(a -> a.getCapacity());
    public static Comparator<Arena> byName = Comparator.comparing(a -> a.getName());
    public static Comparator<Arena> byPlayers = Comparator.comparing(a -> a.getNumPlayers());
    public static Comparator<Arena> byPriority = Comparator.comparing(a -> a.getIntSetting(ArenaSetting.PRIORITY));

    private final ArenaType type;
    
    protected MissileWarsPlugin plugin;
    protected String name; // This is the arena's identifier
    protected String mapName;
    protected ArenaGamemode gamemode;
    protected World world;
    protected WorldBorder virtualBorder;
    protected ArenaSettings settings;
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
    protected boolean resetting;
    protected List<BukkitTask> tasks;
    /** Task for automatically ending the game if no players are present */
    protected BukkitTask autoEnd;
    protected BukkitTask spectatorActionBar;
    protected BukkitTask autoUnload;
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
    public Arena(String name, int capacity, ArenaType type) {
        this.plugin = MissileWarsPlugin.getPlugin();
        this.settings = new ArenaSettings();
        settings.set(ArenaSetting.CAPACITY, capacity);
        this.name = name;
        this.type = type;
        this.gamemode = ArenaGamemode.CLASSIC;
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
        serializedArena.put("gamemode", getGamemode());
        serializedArena.put("type", type.toString());
        serializedArena.put("settings", settings);
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
        gamemode = ArenaGamemode.valueOf(((String) serializedArena.get("gamemode")).toUpperCase());
        type = ArenaType.valueOf((String) serializedArena.get("type"));
        settings = (ArenaSettings) serializedArena.get("settings");
        init();
    }
    
    private void init() {
        npcs = new ArrayList<>();
        players = new HashMap<>();
        spectators = new HashSet<>();
        redQueue = new LinkedList<>();
        blueQueue = new LinkedList<>();
        tasks = new LinkedList<>();
        tracker = new Tracker();
        leftPlayers = new HashMap<>();
        voteManager = new VoteManager(getGamemode(), settings.getSelectedMaps(), 
            !getBooleanSetting(ArenaSetting.MAPS_EDITED));
    }
    
    /**
     * Loads the arena world. This function MUST be called for
     * the arena to work. Do not allow players to join or run
     * any other function if the world has not been loaded. If
     * the world has already loaded, this does nothing.
     * 
     * This function also starts the spectator action bar task
     * that reminds people to use /spectate to stop spectating
     * 
     * @return The world that was loaded
     */
    public World loadWorld() {
        if (world != null) {
            return world;
        }

        Bukkit.getConsoleSender().sendMessage(ConfigUtils.toComponent("&aLoading arena world " + name + "..."));
        
        // Copy world folder from plugin directory to main directory (if exists)
        String worldName = "mwarena_" + name;
        File worldFolder = new File(worldName);
        if (!worldFolder.exists()) {
            File stored = new File(ArenaManager.storageDirectory, worldName);
            if (stored.exists()) {
                try {
                    FileUtils.copyDirectory(stored, worldFolder);
                } catch (IOException e) {
                    Bukkit.getLogger().warning("Something went wrong copying " + worldName + "!");
                    return null;
                }
            }
        }
        
        // Load or create world
        WorldCreator arenaCreator = new WorldCreator(worldName);
        arenaCreator.type(WorldType.FLAT);
        arenaCreator.generatorSettings("{\"layers\": [{\"block\": \"air\", \"height\": 1}], \"biome\":\"plains\"}");
        World nworld = arenaCreator.generator(new VoidChunkGenerator()).createWorld();
        if (nworld == null) {
            Bukkit.getLogger().warning("Something went wrong loading " + name + "!");
            return null;
        }
        nworld.setDifficulty((Difficulty) settings.get(ArenaSetting.WORLD_DIFFICULTY));
        
        // Create all NPCs
        ConfigUtils.schedule(20, () -> createNPCs());
        
        // Start spectator action bar task
        spectatorActionBar = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (MissileWarsPlayer mwp : spectators) {
                Player player = mwp.getMCPlayer();
                player.sendActionBar(ConfigUtils.toComponent("Type /spectate to stop spectating"));
            }
        }, 20, 2);
        Bukkit.getConsoleSender().sendMessage(ConfigUtils.toComponent("&aArena world " + name + " was loaded."));
        world = nworld;
        return world;
    }
    
    /**
     * Unloads the world. Only works if player count is 0.
     * Also cancels all tasks, including spectator action bar.
     * Also resets the arena if needed, and applies queued settings.
     * 
     * @param resetSync whether to synchronously reset the arena for shutdown
     * @return whether the world was loaded successfully
     */
    public boolean unloadWorld() {
        if (world == null) {
            return false;
        }
        
        if (world.getPlayerCount() > 0) {
            return false;
        }

        Bukkit.getConsoleSender().sendMessage(ConfigUtils.toComponent("&2Unloading arena world: " + name + "..."));
        
        // Unload world
        World _world = world;
        world = null;
        if (!Bukkit.unloadWorld(_world, false)) {
            Bukkit.getLogger().warning("Something went wrong unloading " + name + "!");
            return false;
        }
        Bukkit.getWorlds().remove(_world);
        
        // Cancel all tasks
        settings.flush();
        cancelGameTasks();
        ConfigUtils.cancelTask(spectatorActionBar);
        ConfigUtils.cancelTask(autoUnload);
        startTime = null;
        resetting = false;
        
        // Remove NPCs and holograms
        for (int id : npcs) {
            if (CitizensAPI.getNPCRegistry().getById(id) != null) {
                CitizensAPI.getNPCRegistry().getById(id).destroy();
            }
            
            // Delete the hologram associated with the id
            DHAPI.removeHologram("" + id);
        }
        npcs.clear();
        
        // Save voted maps to the arena settings
        if (isCustom() && getBooleanSetting(ArenaSetting.MAPS_EDITED)) {
            settings.setSelectedMaps(voteManager.getVotes().keySet());
        }
        
        // Delete world file
        File worldFolder = new File("mwarena_" + name);
        try {
            FileUtils.deleteDirectory(worldFolder);
        } catch (IOException | IllegalArgumentException e) {
            Bukkit.getLogger().warning("The stored world file couldn't be removed! Please remove manually.");
        }
        
        Bukkit.getConsoleSender().sendMessage(ConfigUtils.toComponent("&2Arena world " + name + " was unloaded."));
        return true;
    }
    
    /**
     * Creates all 7 NPCs in the lobby and their hologram names.s
     */
    private void createNPCs() {
        FileConfiguration schematicConfig = ConfigUtils.getConfigFile("maps.yml");
        Gravity gravity = new Gravity();
        gravity.setHasGravity(false);

        // Spawn team selection NPCs
        for (String team : new String[] {"red", "blue"}) {
            String upper = team.toUpperCase();
            String teamName = (team.equals("red") ? "§c§lRed" : "§9§lBlue") + " Team";
            Vector teamVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos." + team);
            Location teamLoc = new Location(world, teamVec.getX(), teamVec.getY(), teamVec.getZ(), 90, 0);
            NPC teamNPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.SHEEP, teamName);
            
            // Team queuing command
            CommandTrait enqueue = teamNPC.getOrAddTrait(CommandTrait.class);
            enqueue.addCommand(new CommandTrait.NPCCommandBuilder("umw enqueue" + team,
                    CommandTrait.Hand.BOTH).player(true));
            
            // Make sheep the same color as the team
            SheepTrait sheepTrait = teamNPC.getOrAddTrait(SheepTrait.class);
            sheepTrait.setColor(DyeColor.valueOf(upper));
            
            // Hologram to get queued placeholder
            Location holoLoc = teamLoc.clone().add(0, 1.8, 0);
            DHAPI.createHologram(teamNPC.getId() + "", holoLoc, true, List.of(teamName + " (%umw_" + team + "_queue%)"));
            
            // Add misc traits and spawn in
            teamNPC.data().setPersistent(NPC.Metadata.KEEP_CHUNK_LOADED, true);
            teamNPC.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
            teamNPC.data().setPersistent(NPC.Metadata.SILENT, true);
            teamNPC.addTrait(gravity);
            teamNPC.spawn(teamLoc);
            npcs.add(teamNPC.getId());
        }

        // Spawn bar NPC
        Vector barVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos.bar");
        Location barLoc = new Location(world, barVec.getX(), barVec.getY(), barVec.getZ(), -90, 0);
        NPC bartender = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "§2§lBartender");
        CommandTrait openBar = new CommandTrait();
        openBar.addCommand(new CommandTrait.NPCCommandBuilder("bossshop open bar %player%",
                CommandTrait.Hand.BOTH));
        bartender.addTrait(openBar);
        LookClose lookPlayerTrait = bartender.getOrAddTrait(LookClose.class);
        lookPlayerTrait.lookClose(true);
        VillagerProfession profession = bartender.getOrAddTrait(VillagerProfession.class);
        profession.setProfession(Villager.Profession.NITWIT);
        bartender.data().setPersistent(NPC.Metadata.SILENT, true);
        bartender.data().setPersistent(NPC.Metadata.KEEP_CHUNK_LOADED, true);
        bartender.addTrait(gravity); 
        world.getChunkAt(barLoc);
        bartender.spawn(barLoc);
        npcs.add(bartender.getId());

        //Spawn 4 deck selection NPCs
        for (DeckStorage deck : DeckStorage.values()) {
            String id = deck.toString().toLowerCase();
            Vector deckVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos." + id);
            Location deckLoc = new Location(world, deckVec.getX(), deckVec.getY(), deckVec.getZ(), -90, 0);
            NPC deckNPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, deck.getNPCName());
            
            // Add skin
            SkinTrait deckSkin = deckNPC.getOrAddTrait(SkinTrait.class);
            deckSkin.setSkinPersistent(id, schematicConfig.getString("lobby.npc-pos." + id + ".signature"),
                                           schematicConfig.getString("lobby.npc-pos." + id + ".value"));
            
            // Deck info command
            CommandTrait deckCommand = deckNPC.getOrAddTrait(CommandTrait.class);
            deckCommand.addCommand(new CommandTrait.NPCCommandBuilder("mw deck " + id, CommandTrait.Hand.BOTH).player(true));
            
            // Hologram name, to be able to use placeholders
            Location holoLoc = deckLoc.clone().add(0, 2.2, 0);
            DHAPI.createHologram(deckNPC.getId() + "", holoLoc, true, List.of("%umw_deck_npcname_" + id + "%"));
            
            // Add deck-specific equipment
            Equipment deckEquip = new Equipment();
            deckNPC.addTrait(deckEquip);
            deckEquip.set(EquipmentSlot.HAND, deck.getWeapon());
            deckEquip.set(EquipmentSlot.BOOTS, deck.getBoots());
            
            // Add misc traits, spawn
            deckNPC.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
            deckNPC.data().setPersistent(NPC.Metadata.KEEP_CHUNK_LOADED, true);
            deckNPC.addTrait(gravity);
            deckNPC.spawn(deckLoc);
            npcs.add(deckNPC.getId());
        }
    }
    
    public int getIntSetting(ArenaSetting setting) {
        return (int) settings.get(setting);
    }
    
    public boolean getBooleanSetting(ArenaSetting setting) {
        return (boolean) settings.get(setting);
    }
    
    public ArenaSettings getArenaSettings() {
        return settings;
    }
    
    /**
     * Safely set a setting. If the arena is running, the setting will be
     * queued to be set once the arena resets. Otherwise, the setting
     * change will be broadcasted to the arena.
     * 
     * @param setting
     * @param value
     * @param type
     * @return
     */
    public boolean setSetting(ArenaSetting setting, String value, String type) {
        boolean queue = running;
        String curValue = settings.get(setting) + "";
        if (!settings.set(setting, value, type, queue)) {
            return false;
        }
        
        if (!queue && world != null) {
            applySettingChange(setting, curValue, value);
        }
        return true;
    }
    
    /**
     * Announce to the arena that a setting has changed, and
     * apply settings that need to be applied
     * 
     * @param setting
     * @param oldValue
     * @param newValue
     */
    public void applySettingChange(ArenaSetting setting, String oldValue, String newValue) {
        // Actually change the difficulty if world difficulty changes
        if (setting == ArenaSetting.WORLD_DIFFICULTY) {
            world.setDifficulty(Difficulty.valueOf(newValue));
        }
        
        // Add formatting
        do {
            try {
                Integer.parseInt(oldValue);
                oldValue = "&b" + oldValue;
                newValue = "&b" + newValue;
                break;
            } catch (NumberFormatException e) {
                // Do nothing, continue
            }
            
            if (oldValue.equalsIgnoreCase("true")) {
                oldValue = "&aTrue";
                newValue = "&cFalse";
                break;
            }
            
            if (oldValue.equalsIgnoreCase("false")) {
                newValue = "&aTrue";
                oldValue = "&cFalse";
                break;
            }
            
            oldValue = "&d" + oldValue;
            newValue = "&d" + newValue;
        } while (false);
        
        Map<String, String> placeholders = Map.of(
            "%setting%", ConfigUtils.getEnumDisplayString(setting.toString()),
            "%oldvalue%", oldValue,
            "%newvalue%", newValue
        );
                
        for (MissileWarsPlayer mwPlayer : players.values()) {
            ConfigUtils.sendConfigMessage("settings.change", mwPlayer.getMCPlayer(), placeholders);
        }
    }
    
    /**
     * This may change a lot of things. Use with caution.
     * 
     * @param arenaSettings
     */
    public void setArenaSettings(ArenaSettings arenaSettings) {
        this.settings = arenaSettings;
    }
    
    public ArenaType getType() {
        return type;
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
     * Stops all async tasks from trackers and sets running to false
     */
    public void cancelGameTasks() {
        tasks.forEach(t -> t.cancel());
        tasks.clear();
        tracker.stopAll();
        leftPlayers.clear();
        running = false;
        waitingForTie = false;
    }
    
    public VoteManager getVoteManager() {
        return voteManager;
    }

    /**
     * @return the name of the Arena
     */
    public String getName() {
        return name;
    }

    /**
     * @return the world this Arena lies in
     */
    public World getWorld() {
        return world;
    }

    /**
     * @return the selected map name for this Arena
     */
    public String getMapName() {
        return mapName;
    }
    
    /**
     * @return Get the display string for this arena for placeholders
     */
    public String getDisplayGamemode() {
        return gamemode.getDisplayName();
    }

    /**
     * @return the current map type for this arena
     */
    public String getGamemode() {
        return gamemode.toString();
    }
    
    /**
     * @return if a countdown has started or the arena is already running
     */
    public boolean isStarted() {
        return startTime != null;
    }

    /**
     * @return whether a game is currently running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return whether the arena world is resetting
     */
    public boolean isResetting() {
        return resetting;
    }
    
    /**
     * @return if game is waiting for a tie
     */
    public boolean isWaitingForTie() {
        return waitingForTie;
    }
    
    /**
     * @return if the arena world is loaded
     */
    public boolean isOnline() {
        return world != null;
    }
    
    /**
     * @return an unmodifiable set of UUIDs of players in this arena
     */
    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players.keySet());
    }
    
    /**
     * @return A color-coded message of the status of the arena
     */
    public String getStatus() {
        String status = "&eIn Lobby";
        if (world == null) {
            status = "&cOffline";
        } else if (running) {
            status = "&aIn Game";
        } else if (resetting) {
            status = "&6Resetting";
        }
        
        return status;
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
    
    public boolean isCustom() {
        return !settings.get(ArenaSetting.OWNER_UUID).equals(MissileWarsPlugin.zeroUUID);
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
     * Get a unique number for this game session. If the game
     * isn't started, returns 0
     * 
     * @return
     */
    public int getGameSeed() {
        if (startTime == null) {
            return 0;
        }
        
        return startTime.getNano();
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
        if (getBooleanSetting(ArenaSetting.IS_INFINITE_TIME)) {
            return "§7Game ends in: §a∞";
        }
        
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
        if (!running) {
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
        return getIntSetting(ArenaSetting.CAPACITY);
    }
    
    /**
     * Check if the given map is available to play (i.e. lobby
     * meets the rank requirements)
     * 
     * @param map
     * @return
     */
    public boolean isAvailable(String map) {
        return rankMedian >= ArenaUtils.getRankRequirement(getGamemode(), map);
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
        // Make sure player not in parkour
        if (Parkour.getInstance().getParkourSessionManager().isPlayingParkourCourse(player)) {
            ConfigUtils.sendConfigMessage("leave-parkour", player);
            return false;
        }
        
        // Check for whitelist and blacklist
        UUID uuid = player.getUniqueId();
        boolean isOwner = uuid.equals(settings.get(ArenaSetting.OWNER_UUID));
        if (!isOwner && !player.hasPermission("umw.admin")) {
            if (getBooleanSetting(ArenaSetting.IS_PRIVATE) && !settings.isWhitelisted(uuid)) {
                ConfigUtils.sendConfigMessage("join-not-whitelisted", player);
                return false;
            }
            
            if (settings.isBlacklisted(uuid)) {
                ConfigUtils.sendConfigMessage("join-blacklisted", player);
                return false;
            }
        }
        
        // Make sure world is loaded. If it's not and the owner is attempting to join, load the world
        if (world == null) {
            if (isOwner) {
                ConfigUtils.sendConfigMessage("loading-world", player);
                if (loadWorld() == null) {
                    ConfigUtils.sendConfigMessage("world-load-failed", player);
                    return false;
                }
            } else {
                ConfigUtils.sendConfigMessage("arena-offline", player);
                return false;
            }
        }
        ConfigUtils.cancelTask(autoEnd);
        
        // Save inventory if player in world
        if (player.getWorld().getName().equals("world")) {
            InventoryUtils.saveInventory(player, true);
        }

        // Do stuff to the player
        player.teleport(getPlayerSpawn(player));
        InventoryUtils.clearInventory(player, true);
        ArenaUtils.updatePlayerBoots(player);
        player.setHealth(ArenaUtils.getMaxHealth(player));
        player.setFoodLevel(20);
        player.setRespawnLocation(getPlayerSpawn(player), true);
        player.setGameMode(GameMode.ADVENTURE);
        players.put(uuid, new MissileWarsPlayer(uuid));
        giveHeldItems(player);

        // Send notification messages
        ConfigUtils.sendConfigMessage("join-arena", player);
        for (MissileWarsPlayer mwPlayer : players.values()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-others", mwPlayer.getMCPlayer(), null, player);
        }

        for (Player worldPlayer : Bukkit.getWorld("world").getPlayers()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-lobby", worldPlayer, this, player);
        }
        
        if (isCustom()) {
            ConfigUtils.sendConfigMessage("joined-custom", player);
        }
        
        if (!running & getBooleanSetting(ArenaSetting.ONLY_JOIN_QUEUED_PLAYERS)) {
            ConfigUtils.sendConfigMessage("only-join-queued-players", player);
        }
        
        // Discord
        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        discordChannel.sendMessage(":arrow_backward: " + player.getName() + " left and joined arena " + this.getName()).queue();

        // Check for game start
        postJoin(player, asSpectator);
        checkForStart();
        return true;
    }
    
    // Post-join actions that may differ by arena
    protected void postJoin(Player player, boolean asSpectator) { 
        // Check for AFK or spectator to set as spectator
        Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        boolean isAfk = ess.getUser(player).isAfk();
        if (isAfk || asSpectator) {
            addSpectator(player.getUniqueId());
            if (isAfk) {
                ConfigUtils.sendConfigMessage("afk-spectator", player);
            }
            return;
        }
        
        // Auto-join team if setting turned on
        if (running && !player.hasPermission("umw.disableautoteam")) {
            int redSize = redTeam.getSize();
            int blueSize = blueTeam.getSize();
            boolean red = redSize == blueSize ? Math.random() > 0.5 : blueSize > redSize;
            enqueue(player.getUniqueId(), red ? TeamName.RED : TeamName.BLUE);
            return;
        }
       
        // Recalculate rank median if none of the above conditions apply
        calculateRankMedian();
    }
    
    // Give player necessary items
    protected void giveHeldItems(Player player) {
        List<String> items = new ArrayList<>(List.of("votemap", "to-lobby", "red", "blue", "deck", "spectate"));
        if (player.hasPermission("umw.staff")) {
            items.add("arena-settings");
        } else if (isCustom()) {
            if (player.getUniqueId().equals(settings.get(ArenaSetting.OWNER_UUID))) {
                items.add("arena-settings");
            } else {
                items.add("arena-settings-view-only");
            }
        }
        
        FileConfiguration itemConfig = ConfigUtils.getConfigFile("items.yml");
        for (String i : items) {
            String path = "held." + i;
            ItemStack item = InventoryUtils.createItem(path);
            ItemMeta meta = item.getItemMeta();
            if (i.equals("arena-settings")) {
                InventoryUtils.addGlow(meta);
            }
            InventoryUtils.setMetaString(meta, InventoryUtils.HELD_KEY, i);
            item.setItemMeta(meta);
            player.getInventory().setItem(itemConfig.getInt(path + ".slot"), item);
        }
    }
    
    /**
     * Checks if the game is ready to auto-start
     */
    public void checkForStart() {
        if (running || resetting || !getBooleanSetting(ArenaSetting.ENABLE_AUTO_START)) {
            return;
        }
        
        int minPlayers = 2; // Maybe make this configurable?
        if (getNumPlayers() >= minPlayers) {
            scheduleStart();
        }
    }
    
    /**
     * Equivalent to starting with the default timer
     */
    public void scheduleStart() {
        scheduleStart(getIntSetting(ArenaSetting.START_TIMER));
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
        return leaveGame(uuid, true);
    }

    /**
     * Warp player back to the waiting lobby
     *
     * @param uuid the UUID of player
     * @param announce whether to announce the leave message
     */
    private boolean leaveGame(UUID uuid, boolean announce) {
        MissileWarsPlayer toRemove = getPlayerInArena(uuid);
        Player player = Bukkit.getPlayer(uuid);

        if (redTeam == null || blueTeam == null) {
            removePlayer(uuid, true);
            return true;
        }

        if (redTeam.containsPlayer(uuid)) {
            redTeam.removePlayer(toRemove);
            if (announce) {
                for (MissileWarsPlayer mwPlayer : players.values()) {
                    ConfigUtils.sendConfigMessage("messages.leave-team-red", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
                }
            }
        } else if (blueTeam.containsPlayer(uuid)) {
            blueTeam.removePlayer(toRemove);
            if (announce) {
                for (MissileWarsPlayer mwPlayer : players.values()) {
                    ConfigUtils.sendConfigMessage("messages.leave-team-blue", mwPlayer.getMCPlayer(), null, toRemove.getMCPlayer());
                }
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
        if (!running) {
            return;
        }
        
        if (redTeam.getSize() == 0 || blueTeam.getSize() == 0) {
            return;
        }
        
        ConfigUtils.cancelTask(autoEnd);
    }

    /**
     * Checks if the game is empty, and ends game/cancels tasks if so
     */
    private void checkEmpty() {
        // If the arena is custom, start a 5 minute timer to unload it
        if (!getBooleanSetting(ArenaSetting.IS_ALWAYS_ONLINE) && world.getPlayerCount() == 0) {
            autoUnload = ConfigUtils.schedule(20 * 60 * 5, () -> unloadWorld());
        }
        
        // Check if we can cancel a game that's about to start
        if (getNumPlayers() < 2 && cancelStart()) {
            return;
        }
        
        // Check if we can automatically end a game
        if (running) {
            autoEnd();
        }
    }
    
    /**
     * Cancels the start of the game
     * 
     * @return if the game was about to start
     */
    public boolean cancelStart() {
        // If the arena is already running, or no start time has been set, then do nothing
        if (running || startTime == null) {
            return false;
        }
        
        cancelGameTasks();
        startTime = null;
        announceMessage("messages.lobby-countdown-cancelled", null);
        return true;
    }
    
    protected void autoEnd() {
        // Don't auto end if the setting is false
        // This check is here so that this setting could be overriden 
        // by other arenas
        if (!getBooleanSetting(ArenaSetting.END_IF_NO_PLAYERS)) {
            return;
        }
        
        if (redTeam.getSize() <= 0 && blueTeam.getSize() == 0) {
            endGame(null);
        } else if (redTeam.getSize() == 0) {
            announceMessage("messages.red-team-empty", null);
            autoEnd = ConfigUtils.schedule(60 * 20, () -> {
                if (running && redTeam.getSize() == 0) {
                    endGame(blueTeam);
                }
            });
            tasks.add(autoEnd);
        } else if (blueTeam.getSize() == 0) {
            announceMessage("messages.blue-team-empty", null);
            autoEnd = ConfigUtils.schedule(60 * 20, () -> {
                if (running && blueTeam.getSize() == 0) {
                    endGame(redTeam);
                }
            });
            tasks.add(autoEnd);
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
     * @param team use RED or BLUE
     * @param force whether to allow uneven team sizes
     */
    public void enqueue(UUID uuid, TeamName team, boolean force) {
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

        boolean isRed = team == TeamName.RED;
        if (!running) {
            Queue<MissileWarsPlayer> queue = isRed ? redQueue : blueQueue;
            Queue<MissileWarsPlayer> otherQueue = isRed ? blueQueue : redQueue;
            if (!queue.contains(player)) {
                otherQueue.remove(player);
                queue.add(player);
                ConfigUtils.sendConfigMessage("messages.queue-waiting-" + team, mcPlayer, this, null);
                removeSpectator(player);
            } else {
                queue.remove(player);
                ConfigUtils.sendConfigMessage("messages.queue-leave-" + team, mcPlayer, this, null);
            }
            
            return;
        }
        
        MissileWarsTeam joinTeam = isRed ? redTeam : blueTeam;
        MissileWarsTeam otherTeam = isRed ? blueTeam : redTeam;
        if (joinTeam.containsPlayer(uuid)) {
            return;
        }
        
        if (!force) {
            if (joinTeam.getSize() - otherTeam.getSize() >= 1 && !getBooleanSetting(ArenaSetting.ENABLE_UNFAIR_TEAMS)) {
                ConfigUtils.sendConfigMessage("queue-join-error", mcPlayer);
                return;
            }
            
            if (joinTeam.getSize() + otherTeam.getSize() >= getCapacity()) {
                ConfigUtils.sendConfigMessage("messages.queue-join-full", mcPlayer, this, null);
                return;
            }
        }

        removeSpectator(player);
        otherTeam.removePlayer(player);
        joinTeam.addPlayer(player);
        checkNotEmpty();
        announceMessage("messages.queue-join-" + team, player);
    }
    
    /**
     * Enqueue a player with a given UUID to a team
     * Do not override this function! Override the
     * 3 parameter one instead.
     *
     * @param uuid the Player's UUID
     * @param team use "red" or "blue"
     */
    public final void enqueue(UUID uuid, TeamName team) {
        enqueue(uuid, team, false);
    }
    
    /**
     * Remove the given player from the spectators list.
     *
     * @param player the player
     */
    public void removeSpectator(MissileWarsPlayer player) {
        removeSpectator(player, true);
    }

    /**
     * Remove the given player from the spectators list.
     *
     * @param player the player
     * @announce whether the spectator leave message should be announced
     */
    private void removeSpectator(MissileWarsPlayer player, boolean announce) {
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
        for (int secInCd = secCountdown; secInCd > 0; secInCd--) {
            int finalSecInCd = secInCd;
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (running) {
                    return;
                }
                
                if (finalSecInCd <= 5) {
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
     * Starts a game in the arena
     *
     * @return true if the game started. Otherwise false
     */
    public boolean start() {
        // Don't start if already started
        if (running) {
            return false;
        }

        // Select Map
        mapName = voteManager.getVotedMap(map -> isCustom() || isAvailable(map));

        // Generate map.
        announceMessage("messages.starting", null);
        return SchematicManager.spawnFAWESchematic(mapName, world, getGamemode(), () -> {
            // Result will only run if map loading is a success
            // Acquire red and blue spawns
            FileConfiguration mapConfig = ConfigUtils.getConfigFile("maps.yml");
            Vector blueSpawnVec = SchematicManager.getVector(mapConfig, "blue-spawn", getGamemode(), mapName);
            Location blueSpawn = new Location(world, blueSpawnVec.getX(), blueSpawnVec.getY(), blueSpawnVec.getZ());
            Vector redSpawnVec = SchematicManager.getVector(mapConfig, "red-spawn", getGamemode(), mapName);
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
                Player mcPlayer = player.getMCPlayer();
                mcPlayer.teleport(getPlayerSpawn(mcPlayer));
                mcPlayer.closeInventory();
            }
            
            // Register teams and set running state to true
            tasks.add(ConfigUtils.schedule(5, () -> {
                startTeams();
                
                // If nobody is on any team for some reason (game was manually started or
                // Only Join Queued Players is enabled and nobody queued), just set running
                // to true with nobody we don't care
                if (blueTeam.getSize() + redTeam.getSize() == 0) {
                    running = true;
                }
            }));
            
            // Tint task if any player is in the other team's base
            if (virtualBorder == null) {
                virtualBorder = Bukkit.createWorldBorder();
                virtualBorder.setSize(world.getWorldBorder().getSize());
                virtualBorder.setCenter(world.getWorldBorder().getCenter());
                virtualBorder.setWarningDistance(2048);
            }
            
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
        
        // Chaos timers
        if (getBooleanSetting(ArenaSetting.ENABLE_DECREASING_ITEM_TIMERS)) {
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
        }
        
        // Auto end game
        if (!getBooleanSetting(ArenaSetting.IS_INFINITE_TIME)) {
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
    }
    
    /**
     * Gamemode-specific setups
     */
    protected abstract void performGamemodeSetup();
    
    /**
     * Assigns players and starts the teams
     */
    protected void startTeams() {
        // Add all players except spectators to be assigned
        Set<MissileWarsPlayer> toAssign = new HashSet<>();
        for (MissileWarsPlayer player : players.values()) {
            if (!spectators.contains(player)) {
                toAssign.add(player);
            }
        }
        
        // Assign queued players. If a queue is larger than a team size put remaining
        // players into the front of the queue to be assigned first into random teams.
        // If unfair teams is enabled, set max queue size to be unreasonably large
        int capacity = getCapacity();
        int maxRedTeamSize = capacity / 2;
        int maxBlueTeamSize = maxRedTeamSize;
        if (capacity % 2 == 1) {
            maxBlueTeamSize++;
        }
        double maxQueue = getBooleanSetting(ArenaSetting.ENABLE_UNFAIR_TEAMS) ?
                Double.MAX_VALUE : 
                Math.ceil(toAssign.size() / 2.0);
        this.queueCount = getBooleanSetting(ArenaSetting.ONLY_JOIN_QUEUED_PLAYERS) ? 
                redQueue.size() + blueQueue.size() : 
                Math.min(capacity, toAssign.size());
        List<MissileWarsPlayer> queueExtra = new ArrayList<>();
        while (!blueQueue.isEmpty() || !redQueue.isEmpty()) {
            if (!redQueue.isEmpty()) {
                MissileWarsPlayer toAdd = redQueue.remove();
                toAssign.remove(toAdd);
                if (redTeam.getSize() < maxQueue) {
                    redTeam.addPlayer(toAdd);
                } else {
                    queueExtra.add(toAdd);
                }
            }
            if (!blueQueue.isEmpty()) {
                MissileWarsPlayer toAdd = blueQueue.remove();
                toAssign.remove(toAdd);
                if (blueTeam.getSize() < maxQueue) {
                    blueTeam.addPlayer(toAdd);
                } else {
                    queueExtra.add(toAdd);
                }
            }
            
            if (redTeam.getSize() + blueTeam.getSize() >= capacity) {
                break;
            }
        }

        // If non-queued players are allowed to join, add everyone else
        if (!getBooleanSetting(ArenaSetting.ONLY_JOIN_QUEUED_PLAYERS)) {
            List<MissileWarsPlayer> rest = new ArrayList<>(toAssign);
            Collections.shuffle(rest);
            queueExtra.addAll(rest);
            for (MissileWarsPlayer player : queueExtra) {
                if (blueTeam.getSize() <= redTeam.getSize()) {
                    if (blueTeam.getSize() >= maxBlueTeamSize) {
                        ConfigUtils.sendConfigMessage("queue-join-full", player.getMCPlayer());
                    } else {
                        blueTeam.addPlayer(player);
                    }
                } else {
                    if (redTeam.getSize() >= maxRedTeamSize) {
                        ConfigUtils.sendConfigMessage("queue-join-full", player.getMCPlayer());
                    } else {
                        redTeam.addPlayer(player);
                    }
                }
            }
        } else {
            List<MissileWarsPlayer> unqueued = new ArrayList<>();
            unqueued.addAll(redQueue);
            unqueued.addAll(blueQueue);
            unqueued.forEach(player -> ConfigUtils.sendConfigMessage("queue-join-full", player.getMCPlayer()));
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
        for (MissileWarsPlayer mwp : getInGamePlayers()) {
            mwp.initDeck(false, this, redTeam.containsPlayer(mwp.getMCPlayerId()));
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
            p.setWorldBorder(null);
        }
        
        // Schedule tie wait. If endGame gets called from somewhere else,
        // the scheduled task will ignore endGame since running will be false
        if (delay > 0) {
            waitingForTie = true;
            tasks.add(ConfigUtils.schedule(delay, () -> endGame(winningTeam)));
            return;
        }             

        // Cancel all tasks
        cancelGameTasks();
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

        // Send messages
        discordChannel.sendMessage(discordMessage).queue();
        
        // Calculate stats for players
        if (!isCustom()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> calculateStats(winningTeam));
        }

        // Remove all players after a short time, then reset the world a bit after
        int waitTime = plugin.getConfig().getInt("victory-wait-time") * 20;
        tasks.add(ConfigUtils.schedule(waitTime, () -> {
            removePlayers();
            resetWorld();
        }));
    }
    
    // Calculate and store all player stats from the game
    protected abstract void calculateStats(MissileWarsTeam winningTeam);
    
    /**
     * Sends all players back to the waiting lobby. This function used to 
     * send players to another arena, but this is no longer necessary because
     * world resets are noww handled by FAWE.
     * 
     * Additionally, any spectator that does not have umw.continuespectating
     * will be removed from being in spectator mode.
     */
    public void removePlayers() {
        for (MissileWarsPlayer mwPlayer : getInGamePlayers()) {
            leaveGame(mwPlayer.getMCPlayerId(), false);
        }
        
        for (MissileWarsPlayer mwPlayer : spectators) {
            Player player = mwPlayer.getMCPlayer();
            if (player.hasPermission("umw.continuespectating")) {
                player.teleport(getPlayerSpawn(player));
            } else {
                removeSpectator(mwPlayer, false);
            }
        }
    }

    /**
     * Reset the arena world, checking for an auto-start once its done
     */
    protected void resetWorld() {
        resetWorld(() -> checkForStart());
    }
    
    protected void resetWorld(Runnable callback) {
        plugin.log("Resetting arena " + name + "...");
        announceMessage("messages.arena-resetting", null);
        
        // Flush arena settings
        settings.flush().forEach((setting, values) -> {
            applySettingChange(setting, values.getLeft() + "", values.getRight() + "");
        });
        
        // Fill area with air
        int maxHeight = plugin.getConfig().getInt("max-height");
        int maxX = plugin.getConfig().getInt("barrier.center.x") - 1;
        tasks.add(Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SchematicManager.setAir(-250, -64, -250, maxX, maxHeight, 250, world);
            plugin.log("First arena clear finished");
            SchematicManager.setAir(-250, -64, -250, maxX, maxHeight, 250, world, false);
            resetting = false;
            plugin.log("Reset completed");
            announceMessage("messages.arena-reset-complete", null);
            
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.run());
            }
        }));

        voteManager.resetVotes();
        startTime = null;
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
            Location spawnLoc = new Location(world, spawnVec.getX(), spawnVec.getY(), spawnVec.getZ());
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
    
    /**
     * @return a list of players who are in red and blue teams
     */
    protected List<MissileWarsPlayer> getInGamePlayers() {
        if (redTeam == null || blueTeam == null) {
            return Collections.emptyList();
        }
        
        List<MissileWarsPlayer> ret = new ArrayList<>(redTeam.getMembers());
        ret.addAll(blueTeam.getMembers());
        return ret;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Arena other = (Arena) obj;
        return Objects.equals(name, other.name);
    }
}
