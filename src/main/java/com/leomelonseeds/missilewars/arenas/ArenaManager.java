package com.leomelonseeds.missilewars.arenas;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import eu.decentsoftware.holograms.api.DHAPI;
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

/** Class to manager all Missile Wars arenas. */
public class ArenaManager {
    
    public static String storageDirectory;

    private final MissileWarsPlugin plugin;
    private Map<String, Arena> loadedArenas; // Use maps to fetch arenas easily
    private Map<UUID, Arena> customArenas;
    private Map<ArenaType, TreeSet<Arena>> gamemodeArenas;

    public ArenaManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        this.loadedArenas = new HashMap<>();
        this.customArenas = new HashMap<>();
        this.gamemodeArenas = new HashMap<>();
        for (ArenaType type : ArenaType.values()) {
            Comparator<Arena> firstComparator = type == ArenaType.CUSTOM ? Arena.byPriority : Arena.byCapacity;
            Comparator<Arena> fullComparator = Collections.reverseOrder(firstComparator).thenComparing(Arena.byName);
            gamemodeArenas.put(type, new TreeSet<>(fullComparator));
        }
        
        storageDirectory = plugin.getDataFolder().toString() + "/arenaworlds";
    }

    /** Load arenas from data file */
    public void loadArenas() {
        File arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
        FileConfiguration arenaConfig = new YamlConfiguration();
        try {
            arenaConfig.load(arenaFile);
            ConfigurationSection arenas = arenaConfig.getConfigurationSection("arenas");
            for (String key : arenas.getKeys(false)) {
                Arena arena = (Arena) arenas.get(key);
                if (arena.loadWorld() == null) {
                    continue;
                }
                
                loadedArenas.put(arena.getName(), arena);
                gamemodeArenas.get(arena.getType()).add(arena);
                if (arena.isCustom()) {
                    customArenas.put((UUID) arena.getArenaSettings().get(ArenaSetting.OWNER_UUID), arena);
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            Bukkit.getLogger().severe("Could not load arenas from file!");
            e.printStackTrace();
            return;
        }

        // Reload citizens to make sure NPCs are there
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), 
                () -> ConfigUtils.reloadCitizens(), 10);
    }

    /** Clean up and save arenas on server shutdown */
    public void saveArenas() {
        // Remove all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(ConfigUtils.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }

        // Unload each Arena
        for (Arena arena : loadedArenas.values()) {
            arena.unloadWorld();
        }

        saveArenasToFile();
    }

    /**
     * Get an Arena by name.
     *
     * @param name the name of the Arena
     * @return the Arena, or null if it doesn't exist
     */
    public Arena getArena(String name) {
        return loadedArenas.get(name);
    }

    /**
     * Completely remove an Arena by name.
     *
     * @param name the name of the Arena
     * @return true if the arena was successfully deleted
     */
    public boolean removeArena(Arena arena) {
        World arenaWorld = arena.getWorld();
        Logger logger = Bukkit.getLogger();
        logger.info("Removing arena " + arena.getName() + "...");
        if (!arenaWorld.getPlayers().isEmpty()) {
            logger.warning("An arena with players in it cannot be deleted");
            return false;
        }
        
        // Remove citizens
        for (int id : arena.getNPCs()) {
            if (CitizensAPI.getNPCRegistry().getById(id) != null) {
                CitizensAPI.getNPCRegistry().getById(id).destroy();
                logger.info("Citizen with ID " + id + " deleted.");
            }
            
            // Delete the hologram associated with the id
            DHAPI.removeHologram("" + id);
        }
        
        CitizensAPI.getNPCRegistry().saveToStore();
        arena.unloadWorld();
        File storedFolder = new File(storageDirectory, "mwarena_" + arena.getName());
        try {
            FileUtils.deleteDirectory(storedFolder);
        } catch (IOException | IllegalArgumentException e) {
            logger.warning("The stored world file couldn't be removed! Please remove manually.");
        }
        
        // Remove references to the arena
        loadedArenas.remove(arena.getName());
        gamemodeArenas.get(arena.getType()).remove(arena);
        customArenas.remove(arena.getArenaSettings().get(ArenaSetting.OWNER_UUID));
        
        saveArenasToFile();
        return true;
    }

    /**
     * Deletes and re-creates all arenas to implement new settings/schematics
     */
    public void performArenaUpgrade() {
        Logger logger = Bukkit.getLogger();
        // If players are in arenas, don't do it
        if (Bukkit.getOnlinePlayers().size() != Bukkit.getWorld("world").getPlayerCount()) {
            logger.warning("Some players are in arenas!");
            return;
        }
        logger.info("Performing arena upgrades. This might take a while!");
        for (Arena arena : new ArrayList<>(loadedArenas.values())) {
            String[] args = arena.getName().split("-");
            String rawname = args[args.length - 1];
            removeArena(arena);
            Arena newArena = createArena(rawname, arena.getType(), arena.getCapacity());
            newArena.setArenaSettings(new ArenaSettings(arena.getArenaSettings()));
        }
    }
    
    /**
     * Save currently loaded arenas to file
     */
    private void saveArenasToFile() {
        File arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
        FileConfiguration arenaConfig = new YamlConfiguration();
        arenaConfig.set("arenas", loadedArenas);
        try {
            arenaConfig.save(arenaFile);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Arena file couldn't be saved!");
            e.printStackTrace();
        }
    }

    /**
     * Get the Arena that a player with a given UUID is in.
     * Use lightly, scales with amount of arenas.
     *
     * @param id the UUID of the player
     * @return the Arena that the player is in, or null
     */
    public Arena getArena(UUID id) {
        for (Arena arena : loadedArenas.values()) {
            if (arena.isInArena(id)) {
                return arena;
            }
        }
        return null;
    }
    
    /**
     * Get an arena by world
     * 
     * @param world
     * @return
     */
    public Arena getArena(World world) {
        String worldName = world.getName();
        if (!worldName.contains("mwarena_")) {
            return null;
        }
        
        // Safely assume no world is just called "mwarena_"
        return getArena(worldName.substring(8));
    }

    /**
     * Create the waiting lobby region for a specific team.
     *
     * @param team the team to create the waiting lobby for
     * @param arena the arena to create the lobby for
     * @param parent the general lobby area
     */
    private void createWaitingLobby(String team, Arena arena, ProtectedRegion parent) {
        // Setup region
        FileConfiguration schematicConfig = ConfigUtils.getConfigFile("maps.yml");
        WorldGuard wg = WorldGuard.getInstance();
        Vector minLobby = SchematicManager.getVector(schematicConfig, "lobby." + team + "-lobby-region.min");
        Vector maxLobby = SchematicManager.getVector(schematicConfig, "lobby." + team + "-lobby-region.max");
        ProtectedRegion lobbyRegion = new ProtectedCuboidRegion(arena.getName() + "-" + team + "-lobby",
                BlockVector3.at(minLobby.getX(), minLobby.getY(), minLobby.getZ()), BlockVector3.at(maxLobby.getX(),
                maxLobby.getY(), maxLobby.getZ()));

        // Adds flags
        Set<String> enterCommands = new HashSet<>();
        enterCommands.add("/kit " + team + "waitinglobby %username%");
        lobbyRegion.setFlag(net.goldtreeservers.worldguardextraflags.flags.Flags.CONSOLE_COMMAND_ON_ENTRY, enterCommands);
        Set<PotionEffect> effects = new HashSet<>();
        effects.add(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4));
        effects.add(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0));
        effects.add(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        lobbyRegion.setFlag(net.goldtreeservers.worldguardextraflags.flags.Flags.GIVE_EFFECTS, effects);
        lobbyRegion.setFlag(Flags.INVINCIBILITY, StateFlag.State.DENY);
        lobbyRegion.setFlag(Flags.PVP, StateFlag.State.ALLOW);
        Set<String> leaveCommands = new HashSet<>();
        leaveCommands.add("/umw clear %username% waitinglobby");
        lobbyRegion.setFlag(net.goldtreeservers.worldguardextraflags.flags.Flags.CONSOLE_COMMAND_ON_EXIT, leaveCommands);
        try {
            lobbyRegion.setParent(parent);
        } catch (ProtectedRegion.CircularInheritanceException e) {
            e.printStackTrace();
        }
        wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(arena.getWorld())).addRegion(lobbyRegion);
    }
    
    /**
     * CREATE CUSTOM ARENA
     * 
     * @param creator
     * @return
     */
    public Arena createCustomArena(Player creator) {
        return createCustomArena(creator.getName(), creator.getUniqueId());
    }
    
    public Arena createCustomArena(String name, UUID uuid) {
        Arena arena = createArena(name, ArenaType.CUSTOM, 2);
        if (arena == null) {
            return null;
        }
        
        ArenaSettings settings = arena.getArenaSettings();
        settings.set(ArenaSetting.OWNER_NAME, name);
        settings.set(ArenaSetting.OWNER_UUID, uuid);
        settings.set(ArenaSetting.IS_PRIVATE, true);
        customArenas.put(uuid, arena);
        return arena;
    }
    
    /**
     * Create and save a new arena
     * 
     * @param tempname
     * @param gamemode
     * @param capacity
     * @return
     */
    public Arena createArena(String tempname, ArenaType type, int capacity) {
    	Logger logger = Bukkit.getLogger();
    	String gamemode = type.toString().toLowerCase();
    	boolean isCustom = type == ArenaType.CUSTOM;
    	String name;
    	if (type.isSpecial()) {
    	    name = gamemode;
    	} else if (isCustom) {
    	    name = "custom-" + tempname;
    	} else {
    	    name = gamemode + "-" + tempname;
    	}

        // Ensure arena world doesn't exist
        if (loadedArenas.containsKey(name)) {
            logger.warning("An arena with the name " + name + " already exists!");
            return null;
        }

        // Register arena
        Arena arena;
        switch (type) {
        case CLASSIC:
        case CUSTOM:
            arena = new ClassicArena(name, capacity, type);
            break;
        case TOURNEY:
            arena = new TourneyArena(name, capacity);
            break;
        case TRAINING:
            arena = new TrainingArena();
            arena.getArenaSettings().getSelectedMaps().add("default-map");
            break;
        case TUTORIAL:
            arena = new TutorialArena();
            arena.getArenaSettings().getSelectedMaps().add("default-map");
            break;
        default:
            logger.warning("Arena type not accounted for? That simply isn't possible wtf");
            return null;
        }

        FileConfiguration schematicConfig = ConfigUtils.getConfigFile("maps.yml");

        // Create Arena world
        World arenaWorld = arena.loadWorld();
        if (arenaWorld == null) {
            logger.warning("Something went wrong generating the world for new arena " + name);
            return null;
        }
        
        arenaWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        arenaWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        arenaWorld.setGameRule(GameRule.DO_TILE_DROPS, false);
        arenaWorld.setGameRule(GameRule.DO_MOB_LOOT, false);
        arenaWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        arenaWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
        arenaWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        arenaWorld.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        arenaWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        arenaWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, type == ArenaType.TUTORIAL);
        arenaWorld.setGameRule(GameRule.DO_FIRE_TICK, false);
        arenaWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, 20);
        arenaWorld.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        WorldBorder border = arenaWorld.getWorldBorder();
        border.setCenter(plugin.getConfig().getInt("worldborder.center.x"),
                plugin.getConfig().getInt("worldborder.center.z"));
        border.setSize(plugin.getConfig().getInt("worldborder.radius") * 2);
        arenaWorld.setTime(6000);
        logger.info("Arena world generated!");

        // Create Arena lobby
        logger.info("Generating lobby...");
        if (!SchematicManager.spawnFAWESchematic("lobby", arenaWorld, null, result -> {
            logger.info("Lobby generated!");
            Gravity gravity = new Gravity();
            gravity.setHasGravity(false);

            // Spawn team selection NPCs
            for (String team : new String[] {"red", "blue"}) {
                String upper = team.toUpperCase();
                String teamName = (team.equals("red") ? "§c§lRed" : "§9§lBlue") + " Team";
                Vector teamVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos." + team);
                Location teamLoc = new Location(arenaWorld, teamVec.getX(), teamVec.getY(), teamVec.getZ(), 90, 0);
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
                arena.addNPC(teamNPC.getId());
                logger.info(upper + " NPC with UUID " + teamNPC.getUniqueId() + " spawned.");
            }

            // Spawn bar NPC
            Vector barVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos.bar");
            Location barLoc = new Location(arenaWorld, barVec.getX(), barVec.getY(), barVec.getZ(), -90, 0);
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
            arenaWorld.getChunkAt(barLoc);
            bartender.spawn(barLoc);
            arena.addNPC(bartender.getId());
            logger.info("Bartender NPC with UUID " + bartender.getUniqueId() + " spawned.");

            //Spawn 4 deck selection NPCs
            for (DeckStorage deck : DeckStorage.values()) {
                String id = deck.toString().toLowerCase();
                Vector deckVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos." + id);
                Location deckLoc = new Location(arenaWorld, deckVec.getX(), deckVec.getY(), deckVec.getZ(), -90, 0);
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
                arena.addNPC(deckNPC.getId());
                logger.info(deck.toString() + " NPC with UUID " + deckNPC.getUniqueId() + " spawned.");
            }

            CitizensAPI.getNPCRegistry().saveToStore();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Spawn barrier wall
                FileConfiguration settings = plugin.getConfig();
                int length = settings.getInt("barrier.length");
                int x = settings.getInt("barrier.center.x");
                int zCenter = settings.getInt("barrier.center.z");
                SchematicManager.setBlock(x, 0, zCenter - length / 2, x, 320, zCenter + length / 2, arenaWorld, BlockTypes.BARRIER, false);
                
                // Setup regions
                WorldGuard wg = WorldGuard.getInstance();
                RegionManager manager = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(arenaWorld));
                
                GlobalProtectedRegion globalRegion = new GlobalProtectedRegion("__global__");
                globalRegion.setFlag(Flags.CHEST_ACCESS, State.DENY);
                Set<PotionEffect> effects = new HashSet<>();
                effects.add(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
                globalRegion.setFlag(net.goldtreeservers.worldguardextraflags.flags.Flags.GIVE_EFFECTS, effects);
                globalRegion.setPriority(10);
                manager.addRegion(globalRegion);
                
                Vector minLobby = SchematicManager.getVector(schematicConfig, "lobby.main-region.min");
                Vector maxLobby = SchematicManager.getVector(schematicConfig, "lobby.main-region.max");
                ProtectedRegion lobbyRegion = new ProtectedCuboidRegion(name + "-lobby", BlockVector3.at(minLobby.getX(),
                        minLobby.getY(), minLobby.getZ()), BlockVector3.at(maxLobby.getX(), maxLobby.getY(), maxLobby.getZ()));
                lobbyRegion.setFlag(Flags.INVINCIBILITY, StateFlag.State.ALLOW);
                lobbyRegion.setFlag(Flags.PVP, StateFlag.State.DENY);
                lobbyRegion.setFlag(Flags.TNT, StateFlag.State.DENY);
                lobbyRegion.setFlag(Flags.HUNGER_DRAIN, StateFlag.State.DENY);
                lobbyRegion.setFlag(Flags.ITEM_DROP, StateFlag.State.DENY);
                lobbyRegion.setFlag(Flags.DENY_MESSAGE, "");
                manager.addRegion(lobbyRegion);
                createWaitingLobby("red", arena, lobbyRegion);
                createWaitingLobby("blue", arena, lobbyRegion);
            });
            
            // Save arenas in loaded arenas list
            loadedArenas.put(name, arena);
            gamemodeArenas.get(arena.getType()).add(arena);

            logger.info("Arena " + name + " generated. World will save in 5 seconds.");

            // Wait to ensure schematic is spawned
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                logger.info("Saving new arena " + name);
                arenaWorld.save();
                String worldName = "mwarena_" + name;
                File worldFolder = new File(worldName);
                File stored = new File(storageDirectory, worldName);
                FileFilter filter = FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter("session.lock"));
                try {
                    FileUtils.copyDirectory(worldFolder, stored, filter);
                } catch (IOException e) {
                    logger.warning("Couldn't copy world directory for " + worldName + ". Do it manually?");
                }
                saveArenasToFile();
                logger.info("Arena " + name + " locked and loaded.");
            }, 100);

        })) {
            logger.severe("Couldn't generate lobby! Schematic files present?");
            return null;
        }

        return arena;
    }

    /**
     * Get the {@link MissileWarsPlayer} representing the player with the given UUID
     *
     * @param id the player's UUID
     * @return the {@link MissileWarsPlayer} representing the player with the given UUID if it exists
     */
    public MissileWarsPlayer getPlayer(UUID id) {
        Arena arena = getArena(id);
        if (arena == null) {
            return null;
        }
        
        return arena.getPlayerInArena(id);
    }
    
    /**
     * Get the loaded arenas of a specific arena type. Arenas are sorted
     * by default by capacity in descending order, then by name
     * 
     * @param type
     * @return
     */
    public List<Arena> getLoadedArenas(ArenaType type) {
        return new ArrayList<>(gamemodeArenas.get(type));
    }

    /**
     * Gets a list of the loaded arenas by type with a supplied comparator.
     * The comparator is by default in ascending order.
     *
     * @return The list of loaded arenas
     */
    public List<Arena> getLoadedArenas(ArenaType type, Comparator<Arena> sortingType) {
        List<Arena> arenas = new ArrayList<>(gamemodeArenas.get(type));
        arenas.sort(sortingType.thenComparing(Arena.byName));
        return arenas;
    }
    
    /**
     * Get total number of players playing a gamemode
     * 
     * @param gamemode
     * @return
     */
    public int getPlayers(ArenaType type) {
        return gamemodeArenas.get(type).stream().mapToInt(a -> a.getTotalPlayers()).sum();
    }
    
    public Arena getCustomArena(Player player) {
        return customArenas.get(player.getUniqueId());
    }
}
