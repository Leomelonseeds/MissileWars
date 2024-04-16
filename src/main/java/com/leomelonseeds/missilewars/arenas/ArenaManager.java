package com.leomelonseeds.missilewars.arenas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.SchematicManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

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
    
    private final static List<String> specialArenas = new ArrayList<>(Arrays.asList(new String[] {"tutorial", "training"}));

    private final MissileWarsPlugin plugin;

    /** A list of all loaded arenas. */
    private List<Arena> loadedArenas;

    /** Default constructor */
    public ArenaManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        loadedArenas = new ArrayList<>();
    }

    /** Load arenas from data file */
    @SuppressWarnings("unchecked")
    public void loadArenas() {
        File arenaFile = new File(plugin.getDataFolder(), "arenas.yml");

        // Acquire arenas from data file
        if (arenaFile.exists()) {
            FileConfiguration arenaConfig = new YamlConfiguration();
            try {
                arenaConfig.load(arenaFile);
                if (arenaConfig.contains("arenas")) {
                    loadedArenas = (List<Arena>) arenaConfig.get("arenas");
                }
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }
        }

        // Load worlds for arenas
        if (loadedArenas == null) return;
        for (Arena arena : loadedArenas) {
            Bukkit.getConsoleSender().sendMessage(ConfigUtils.toComponent("§aLoading arena: " + arena.getName() + "..."));
            arena.loadWorldFromDisk(false);
        }

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
        for (Arena arena : loadedArenas) {
            arena.stopTrackers();
            Bukkit.unloadWorld(arena.getWorld(), false);
        }

        // Save Arenas to file
        File arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
        FileConfiguration arenaConfig = new YamlConfiguration();
        arenaConfig.set("arenas", loadedArenas);
        try {
            arenaConfig.save(arenaFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get an Arena by name.
     *
     * @param name the name of the Arena
     * @return the Arena, or null if it doesn't exist
     */
    public Arena getArena(String name) {
        for (Arena arena : loadedArenas) {
            if (arena.getName().equalsIgnoreCase(name)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Completely remove an Arena by name.
     *
     * @param name the name of the Arena
     * @return true if the arena was successfully deleted
     */
    public Boolean removeArena(Arena arena) {
        World arenaWorld = arena.getWorld();
        Logger logger = Bukkit.getLogger();
        if (!arenaWorld.getPlayers().isEmpty()) {
            logger.log(Level.WARNING, "An arena with players in it cannot be deleted");
            return false;
        }
        
        // Remove citizens
        for (int id : arena.getNPCs()) {
            if (CitizensAPI.getNPCRegistry().getById(id) != null) {
                CitizensAPI.getNPCRegistry().getById(id).destroy();
                logger.log(Level.INFO, "Citizen with ID " + id + " deleted.");
            }
        }
        CitizensAPI.getNPCRegistry().saveToStore();
        Bukkit.unloadWorld(arenaWorld, false);
        Bukkit.getWorlds().remove(arenaWorld);
        File worldFolder = new File("mwarena_" + arena.getName());
        try {
            FileUtils.deleteDirectory(worldFolder);
        } catch (IOException e) {
            logger.log(Level.WARNING, "The world file couldn't be removed! Please remove manually.");
        }
        loadedArenas.remove(arena);
        // Remove arena from file
        File arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
        FileConfiguration arenaConfig = new YamlConfiguration();
        arenaConfig.set("arenas", loadedArenas);
        try {
            arenaConfig.save(arenaFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Arena file couldn't be saved!");
        }
        return true;
    }

    /**
     * Deletes and re-creates all arenas to implement new settings
     */
    public void performArenaUpgrade() {
        Logger logger = Bukkit.getLogger();
        // If players are in arenas, don't do it
        if (Bukkit.getOnlinePlayers().size() != Bukkit.getWorld("world").getPlayerCount()) {
            logger.log(Level.WARNING, "Some players are in arenas!");
            return;
        }
        logger.log(Level.INFO, "Performing arena upgrades. This might take a while!");
        for (Arena arena : new ArrayList<>(getLoadedArenas())) {
            String[] args = arena.getName().split("-");
            String rawname = args[args.length - 1];
            int capacity = arena.getCapacity();
            String gamemode = arena.getGamemode();
            removeArena(arena);
            createArena(rawname, specialArenas.contains(rawname) ? rawname : gamemode, capacity);
        }
    }


    /**
     * Get an Arena by index.
     *
     * @param index the index of the Arena
     * @return the Arena, or null if it doesn't exist
     */
    public Arena getArena(int index, String gamemode) {
        if (index < 0 || index >= getLoadedArenas(gamemode).size()) {
            return null;
        }
        return getLoadedArenas(gamemode).get(index);
    }

    /**
     * Get the Arena that a player with a given UUID is in.
     *
     * @param id the UUID of the player
     * @return the Arena that the player is in, or null
     */
    public Arena getArena(UUID id) {
        for (Arena arena : loadedArenas) {
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
        for (Arena arena : loadedArenas) {
            if (arena.getWorld() == null) {
                continue;
            }
            
            if (arena.getWorld().equals(world)) {
                return arena;
            }
        }
        return null;
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
        Vector minLobby = SchematicManager.getVector(schematicConfig, "lobby." + team + "-lobby-region.min", null, null);
        Vector maxLobby = SchematicManager.getVector(schematicConfig, "lobby." + team + "-lobby-region.max", null, null);
        ProtectedRegion lobbyRegion = new ProtectedCuboidRegion(arena.getName() + "-" + team + "-lobby",
                BlockVector3.at(minLobby.getX(), minLobby.getY(), minLobby.getZ()), BlockVector3.at(maxLobby.getX(),
                maxLobby.getY(), maxLobby.getZ()));

        // Adds flags
        Set<String> enterCommands = new HashSet<>();
        enterCommands.add("/kit " + team + "waitinglobby %username%");
        lobbyRegion.setFlag(net.goldtreeservers.worldguardextraflags.flags.Flags.CONSOLE_COMMAND_ON_ENTRY, enterCommands);
        Set<PotionEffect> effects = new HashSet<>();
        effects.add(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 99999999, 5));
        effects.add(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999999, 5));
        lobbyRegion.setFlag(net.goldtreeservers.worldguardextraflags.flags.Flags.GIVE_EFFECTS, effects);
        lobbyRegion.setFlag(Flags.INVINCIBILITY, StateFlag.State.DENY);
        lobbyRegion.setFlag(Flags.PVP, StateFlag.State.ALLOW);
        Set<String> leaveCommands = new HashSet<>();
        leaveCommands.add("/umw clear %username%");
        lobbyRegion.setFlag(net.goldtreeservers.worldguardextraflags.flags.Flags.CONSOLE_COMMAND_ON_EXIT, leaveCommands);
        try {
            lobbyRegion.setParent(parent);
        } catch (ProtectedRegion.CircularInheritanceException e) {
            e.printStackTrace();
        }
        wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(arena.getWorld())).addRegion(lobbyRegion);
    }

    /**
     * Create a new Arena given a name with default player capacity.
     *
     * @param name the name of the Arena
     * @param creator the creator of the world
     * @return true if the Arena was created, otherwise false
     */
    public boolean createArena(String tempname, String gamemode, int capacity) {

    	Logger logger = Bukkit.getLogger();
    	
    	String n = gamemode + "-" + tempname;
    	if (specialArenas.contains(gamemode)) {
    	    n = gamemode;
    	}
    	String name = n;

        // Ensure arena world doesn't exist
        if (Bukkit.getWorld("mwarena_" + name) != null) {
            logger.log(Level.WARNING, "An arena with the name " + name + " already exists!");
            return false;
        }

        // Register arena
        Arena arena;
        switch (gamemode) {
        case "classic":
            arena = new ClassicArena(name, capacity);
            break;
        case "tourney":
            arena = new TourneyArena(name, capacity);
            break;
        case "training":
            arena = new TrainingArena();
            break;
        case "tutorial":
            arena = new TutorialArena();
            break;
        default:
            logger.log(Level.WARNING, "Invalid arena type!");
            return false;
        }

        FileConfiguration schematicConfig = ConfigUtils.getConfigFile("maps.yml");

        // Create Arena world
        logger.log(Level.INFO, "Generating arena world for " + name);
        WorldCreator arenaCreator = new WorldCreator("mwarena_" + name);
        arenaCreator.type(WorldType.FLAT);
        arenaCreator.generator(new ChunkGenerator() {});
        World arenaWorld = arenaCreator.createWorld();
        assert arenaWorld != null;
        arenaWorld.setAutoSave(false);
        arenaWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        arenaWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        arenaWorld.setGameRule(GameRule.DO_TILE_DROPS, false);
        arenaWorld.setGameRule(GameRule.DO_MOB_LOOT, false);
        arenaWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        arenaWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
        arenaWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        arenaWorld.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        arenaWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        arenaWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
        arenaWorld.setGameRule(GameRule.DO_FIRE_TICK, false);
        arenaWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, 20);
        arenaWorld.setDifficulty(Difficulty.EASY);
        WorldBorder border = arenaWorld.getWorldBorder();
        border.setCenter(plugin.getConfig().getInt("worldborder.center.x"),
                plugin.getConfig().getInt("worldborder.center.z"));
        border.setSize(plugin.getConfig().getInt("worldborder.radius") * 2);
        arenaWorld.setTime(6000);
        logger.log(Level.INFO, "Arena world generated!");

        // Create Arena lobby
        logger.log(Level.INFO, "Generating lobby...");
        if (!SchematicManager.spawnFAWESchematic("lobby", arenaWorld, null, result -> {

            logger.log(Level.INFO, "Lobby generated!");
            Gravity gravity = new Gravity();
            gravity.gravitate(true);

            // Spawn red NPC
            for (String team : new String[] {"red", "blue"}) {
                String upper = team.toUpperCase();
                Vector teamVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos." + team, null, null);
                Location teamLoc = new Location(arenaWorld, teamVec.getX(), teamVec.getY(), teamVec.getZ(), 90, 0);
                NPC teamNPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.SHEEP, 
                        (team.equals("red") ? "§c§lRed" : "§9§lBlue") + " Team");
                CommandTrait enqueue = new CommandTrait();
                enqueue.addCommand(new CommandTrait.NPCCommandBuilder("umw enqueue" + team,
                        CommandTrait.Hand.BOTH).player(true));
                teamNPC.addTrait(enqueue);
                SheepTrait sheepTrait = teamNPC.getOrAddTrait(SheepTrait.class);
                sheepTrait.setColor(DyeColor.valueOf(upper));
                teamNPC.data().setPersistent(NPC.Metadata.SILENT, true);
                teamNPC.addTrait(gravity);
                arenaWorld.loadChunk(teamLoc.getChunk());
                teamNPC.spawn(teamLoc);
                arena.addNPC(teamNPC.getId());
                logger.log(Level.INFO, upper + " NPC with UUID " + teamNPC.getUniqueId() + " spawned."); 
            }

            // Spawn bar NPC
            Vector barVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos.bar", null, null);
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
            bartender.addTrait(gravity); 
            arenaWorld.loadChunk(barLoc.getChunk());
            bartender.spawn(barLoc);
            arena.addNPC(bartender.getId());
            logger.log(Level.INFO, "Bartender NPC with UUID " + bartender.getUniqueId() + " spawned.");

            //Spawn 4 deck selection NPCs
            for (DeckStorage deck : DeckStorage.values()) {
                String id = deck.toString().toLowerCase();
                Vector deckVec = SchematicManager.getVector(schematicConfig, "lobby.npc-pos." + id, null, null);
                Location deckLoc = new Location(arenaWorld, deckVec.getX(), deckVec.getY(), deckVec.getZ(), -90, 0);
                deckLoc.setYaw(-90);
                NPC deckNPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, deck.getNPCName());
                SkinTrait deckSkin = deckNPC.getOrAddTrait(SkinTrait.class);
                deckSkin.setSkinPersistent(id, schematicConfig.getString("lobby.npc-pos." + id + ".signature"),
                                               schematicConfig.getString("lobby.npc-pos." + id + ".value"));
                CommandTrait deckCommand = new CommandTrait();
                deckCommand.addCommand(new CommandTrait.NPCCommandBuilder("mw deck " + id, CommandTrait.Hand.BOTH).player(true));
                deckNPC.addTrait(deckCommand);
                deckNPC.addTrait(gravity);
                Equipment deckEquip = new Equipment();
                deckNPC.addTrait(deckEquip);
                deckEquip.set(EquipmentSlot.HAND, deck.getWeapon());
                deckEquip.set(EquipmentSlot.BOOTS, deck.getBoots());
                arenaWorld.loadChunk(deckLoc.getChunk());
                deckNPC.spawn(deckLoc);
                arena.addNPC(deckNPC.getId());
                logger.log(Level.INFO, deck.toString() + " NPC with UUID " + deckNPC.getUniqueId() + " spawned.");
            }

            CitizensAPI.getNPCRegistry().saveToStore();

            // Spawn barrier wall
            FileConfiguration settings = plugin.getConfig();
            int length = settings.getInt("barrier.length");
            int x = settings.getInt("barrier.center.x");
            int zCenter = settings.getInt("barrier.center.z");
            for (int y = 0; y <= 320; ++y) {
                for (int z = zCenter - length / 2; z < zCenter + length / 2; z++) {
                    arenaWorld.getBlockAt(x, y, z).setType(Material.BARRIER);
                }
            }

            loadedArenas.add(arena);
            
            // Setup regions
            WorldGuard wg = WorldGuard.getInstance();
            RegionManager manager = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(arenaWorld));
            
            GlobalProtectedRegion globalRegion = new GlobalProtectedRegion("__global__");
            globalRegion.setFlag(Flags.CHEST_ACCESS, State.DENY);
            globalRegion.setPriority(10);
            manager.addRegion(globalRegion);
            
            Vector minLobby = SchematicManager.getVector(schematicConfig, "lobby.main-region.min", null, null);
            Vector maxLobby = SchematicManager.getVector(schematicConfig, "lobby.main-region.max", null, null);
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

            logger.log(Level.INFO, "Arena " + name + " generated. World will save in 10 seconds.");

            // Wait to ensure schematic is spawned
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                arenaWorld.save();
                logger.log(Level.INFO, "Saving new arena " + name);
                logger.log(Level.INFO, "Arena " + name + " locked and loaded.");
            }, 200);

        })) {
            logger.log(Level.SEVERE, "Couldn't generate lobby! Schematic files present?");
            return false;
        }

        return true;
    }

    /**
     * Get the {@link MissileWarsPlayer} representing the player with the given UUID
     *
     * @param id the player's UUID
     * @return the {@link MissileWarsPlayer} representing the player with the given UUID if it exists
     */
    public MissileWarsPlayer getPlayer(UUID id) {
        Arena arena = getArena(id);
        if (arena != null) {
            return arena.getPlayerInArena(id);
        }
        return null;
    }
    
    public List<Arena> getLoadedArenas(String gamemode) {
        return getLoadedArenas(gamemode, Arena.byCapacity);
    }

    /**
     * Gets a list of the loaded arenas, by gamemode
     *
     * @return The list of loaded arenas
     */
    public List<Arena> getLoadedArenas(String gamemode, Comparator<Arena> sortingType) {
        List<Arena> sortedArenas = new ArrayList<>();
        for (Arena a : loadedArenas) {
            if (a.getGamemode().equals(gamemode) && !specialArenas.contains(a.getName())) {
                sortedArenas.add(a);
            }
        }
        sortedArenas.sort(Collections.reverseOrder(sortingType));
        return sortedArenas;
    }
    
    /**
     * Get total number of players playing a gamemode
     * 
     * @param gamemode
     * @return
     */
    public int getPlayers(String gamemode) {
        int count = 0;
        if (gamemode.equals("training")) {
            for (Arena arena : loadedArenas) {
                if (arena instanceof TrainingArena) {
                    return arena.getTotalPlayers();
                }
            }
            return 0;
        }

        if (gamemode.equals("tutorial")) {
            for (Arena arena : loadedArenas) {
                if (arena instanceof TutorialArena) {
                    return arena.getTotalPlayers();
                }
            }
            return 0;
        }
        
        for (Arena a : getLoadedArenas(gamemode)) {
            count += a.getTotalPlayers();
        }
        return count;
    }


    /**
     * Gets a list of the loaded arenas, sorted by highest capacity then by name
     *
     * @return The list of loaded arenas
     */
    public List<Arena> getLoadedArenas() {
        List<Arena> sortedArenas = loadedArenas;
        sortedArenas.sort(Collections.reverseOrder(Arena.byCapacity).thenComparing(Arena.byName));
        return sortedArenas;
    }
}
