package com.leomelonseeds.missilewars.utilities;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.Passive;
import com.leomelonseeds.missilewars.decks.Passive.Stat;
import com.leomelonseeds.missilewars.decks.Passive.Type;

import me.clip.placeholderapi.PlaceholderAPI;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.exception.NPCLoadException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

/** Utility Class for acquiring data from config files, and other useful functions. */
public class ConfigUtils {

    // Map of open cached config files
    private static Map<String, FileConfiguration> configCache = new HashMap<>();
    
    /**
     * Get config file from default folder
     * 
     * @param configName
     * @return
     */
    public static FileConfiguration getConfigFile(String configName, String directory) {
        // Check for config file in cache
        if (configCache.containsKey(configName)) {
            return configCache.get(configName);
        }

        File file = new File(MissileWarsPlugin.getPlugin().getDataFolder().toString() + directory, configName);
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        configCache.put(configName, config);
        return config;
    }
    
    public static FileConfiguration getConfigFile(String configName) {
        return getConfigFile(configName, "");
    }
    
    /**
     * Reloads main config and clears cache of all configs.
     * Does not reload the default.json
     */
    public static void reloadConfigs() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        configCache.clear();
        plugin.reloadConfig();
        plugin.getDeckManager().reload();
    }
    
    /**
     * Reload all NPCs to make them appear again
     */
    public static void reloadCitizens() {
        try {
            ((Citizens) CitizensAPI.getPlugin()).reload();
        } catch (NPCLoadException e) {
            Bukkit.getLogger().log(Level.WARNING, "Citizens couldn't be reloaded.");
        }
    }

    /**
     * Set placeholders onto a message.
     *
     * @param msg the message to parse
     * @param player the player to send the message to and set placeholder using
     * @param arena the arena to associate the placeholders with
     * @param focus a separate player that is the focus of the message, but not receiving it
     */
    private static String setPlaceholders(String msg, Player player, Arena arena, Player focus) {
        // Set umw arena placeholders
        if (arena != null) {
            msg = msg.replaceAll("%umw_arena%", StringUtils.capitalize(arena.getName()));
            msg = msg.replaceAll("%umw_arena_players%", "" + arena.getTotalPlayers());
            msg = msg.replaceAll("%umw_arena_active%", "" + arena.getNumPlayers());
            msg = msg.replaceAll("%umw_arena_cap%", "" + arena.getCapacity());
            msg = msg.replaceAll("%umw_time%", "" + arena.getSecondsUntilStart());
            msg = msg.replaceAll("%umw_time_remaining%", "" + arena.getTimeRemaining());
            String status = "§6In Lobby";
            if (arena.isRunning()) {
                status = "§aIn Game";
            } else if (arena.isResetting()) {
                status = "§cResetting";
            }
            msg = msg.replaceAll("%umw_arena_status%", status);
            if (player != null) {
                msg = msg.replaceAll("%umw_position%", "" + arena.getPositionInQueue(player.getUniqueId()));
                if (msg.contains("%umw_team%")) {
                    TeamName team = arena.getTeam(player.getUniqueId());
                    if (team != TeamName.NONE) {
                        msg = msg.replaceAll("%umw_team%", team.getColor() + team + "§r");
                    }
                }
            }
        }

        // Set umw arena-less placeholders
        if (focus != null) {
            msg = msg.replaceAll("%umw_focus%", getFocusName(focus));
        }

        // Set PAPI placeholders and color
        if (player != null) {
            msg = PlaceholderAPI.setPlaceholders(player, msg);
        }
        return msg;
    }

    /**
     * Get a parsed String from the messages.yml file.
     *
     * @param path the path to the String
     * @param player the player to set placeholders with
     * @param arena the arena to associate the placeholders with
     * @param focus a separate player that is the focus of the message, but not receiving it
     * @return the parsed String
     */
    public static String getConfigText(String path, Player player, Arena arena, Player focus) {
        FileConfiguration messagesConfig = getConfigFile("messages.yml");
        return setPlaceholders(messagesConfig.getString(path), player, arena, focus);
    }

    /**
     * Get a list of parsed Strings from the messages.yml file.
     *
     * @param path the path to the String list
     * @param player the player to set placeholders with
     * @param arena the arena to associate placeholders with
     * @param focus a separate player that is the focus of the message, but not receiving it
     * @return the list of parsed Strings
     */
    public static List<String> getConfigTextList(String path, Player player, Arena arena, Player focus) {
        List<String> list = new ArrayList<>();
        FileConfiguration messagesConfig = getConfigFile("messages.yml");
        for (String msg : messagesConfig.getStringList(path)) {
            list.add(setPlaceholders(msg, player, arena, focus));
        }
        return list;
    }
    
    /**
     * Shorthand for sending a chat message without arena and focus context.
     * The chat message does NOT need to include "messages."
     *      
     * @param path
     * @param player
     */
    public static void sendConfigMessage(String path, Player player) {
        sendConfigMessage("messages." + path, player, null, null);
    }

    /**
     * Send a configurable message with placeholders set for player.
     * The path should include "messages."
     *
     * @param path the path to the message in the messages.yml file
     * @param player the player to send the message to and set placeholder using
     * @param arena the arena to associate the placeholders with
     * @param focus a separate player that is the focus of the message, but not receiving it
     */
    public static void sendConfigMessage(String path, Player player, Arena arena, Player focus) {
        FileConfiguration messagesConfig = getConfigFile("messages.yml");
        String prefix = messagesConfig.getString("messages.prefix");

        // Check for multi line message
        if (!messagesConfig.getStringList(path).isEmpty()) {
            for (String msg : messagesConfig.getStringList(path)) {
                player.sendMessage(toComponent(setPlaceholders(prefix + msg, player, arena, focus)));
            }
        }

        // Send single line message
        else {
            String msg = messagesConfig.getString(path);
            player.sendMessage(toComponent(setPlaceholders(prefix + msg, player, arena, focus)));
        }

        // Check for associated sound
        String soundPath = path.replace("messages.", "");
        sendConfigSound(soundPath, player);
    }

    /**
     * Send a sound to the player.
     *
     * @param path the key of the sound in the sounds.yml file
     * @param player the player to send sound to
     */
    public static void sendConfigSound(String path, Player player) {
        FileConfiguration soundConfig = getConfigFile("sounds.yml");

        if (!soundConfig.contains(path)) {
        	return;
        }

        Sound sound = Sound.valueOf(soundConfig.getString(path + ".sound"));
        float volume = (float) soundConfig.getDouble(path + ".volume");
        float pitch = (float) soundConfig.getDouble(path + ".pitch");

        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }

    /**
     * Send a sound to a location! Mainly
     * useful for utility items.
     *
     * @param path the key of the sound in the sounds.yml file
     * @param player the player to send sound to
     * @param location the location to send the sound to
     */
    public static void sendConfigSound(String path, Location location) {
        FileConfiguration soundConfig = getConfigFile("sounds.yml");

        if (!soundConfig.contains(path)) {
        	return;
        }

        Sound sound = Sound.valueOf(soundConfig.getString(path + ".sound"));
        float volume = (float) soundConfig.getDouble(path + ".volume");
        float pitch = (float) soundConfig.getDouble(path + ".pitch");

        location.getWorld().playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }
    
    /**
     * Send the team a title at a given path. Path should not include "messages." or "titles."
     *
     * @param path the path
     */
    public static void sendTitle(String path, Player player) {
        // Find titles and subtitles from config
        String title = getConfigText("titles." + path + ".title", null, null, null);
        List<String> subtitles = getConfigTextList("titles." + path + ".subtitle", null,
                null, null);
        String subtitle;
        if (!subtitles.isEmpty()) {
            subtitle = subtitles.get(new Random().nextInt(subtitles.size()));
        } else {
            subtitle = getConfigText("titles." + path + ".subtitle", null, null,
                    null);
        }
        
        // Janky way of including team-based placeholders
        Arena a = MissileWarsPlugin.getPlugin().getArenaManager().getArena(player.getUniqueId());
        if (a != null) {
            if (a.getTeam(player.getUniqueId()) == TeamName.RED) {
                if (subtitle.contains("umw_red")) {
                    subtitle = subtitle.replace("red", "blue");
                } else if (subtitle.contains("umw_blue")) {
                    subtitle = subtitle.replace("blue", "red");
                }
            }
            subtitle = PlaceholderAPI.setPlaceholders(player, subtitle);
        }

        ConfigurationSection sec = getConfigFile("messages.yml").getConfigurationSection("titles." + path);
        int[] durs = {500, 1000, 1000}; // In milliseconds
        String[] sdurs = {"fadein", "length", "fadeout"};
        for (int i = 0; i < 3; i++) {
            if (sec.contains(sdurs[i])) {
                durs[i] = sec.getInt(sdurs[i]) * 50;
            }
        }
        
        
        Title.Times times = Title.Times.times(Duration.ofMillis(durs[0]), Duration.ofMillis(durs[1]), Duration.ofMillis(durs[2]));
        Title finalTitle = Title.title(toComponent(title), toComponent(subtitle), times);

        player.showTitle(finalTitle);
        sendConfigSound(path, player);
    }

    /**
     * Gets world spawn location w/ yaw and pitch
     *
     * @return Location
     */
    public static Location getSpawnLocation() {
    	FileConfiguration config = MissileWarsPlugin.getPlugin().getConfig();

    	double x = config.getDouble("spawn.x");
    	double y = config.getDouble("spawn.y");
    	double z = config.getDouble("spawn.z");
    	float yaw = (float) config.getDouble("spawn.yaw");
    	float pitch = (float) config.getDouble("spawn.pitch");

    	return new Location(Bukkit.getWorld("world"), x, y, z, yaw, pitch);
    }

    /**
     * Gets focus name of player
     *
     * @param player
     * @return player name stripped of color and applied of team
     */
    public static String getFocusName(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        Arena playerArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(uuid);
        String displayName = player.isOnline() ? toPlain(Bukkit.getPlayer(uuid).displayName()) : player.getName();
        if (playerArena != null) {
            TeamName team = playerArena.getTeam(uuid);
            if (team != TeamName.NONE) {
                return team.getColor() + removeColors(displayName);
            }
        }
        return displayName;
    }

    /**
     * Acquire specific numerical data for a given map.
     * @param mapType the gamemode for the map
     * @param mapName the name of the map
     * @param path the path to the data
     * @return the data at the path for the given math, or default data if it does not exist
     */
    public static double getMapNumber(String mapType, String mapName, String path) {
        FileConfiguration mapsConfig = ConfigUtils.getConfigFile("maps.yml");
        if (mapsConfig.contains(mapType + "." + mapName + "." + path)) {
            return mapsConfig.getDouble(mapType + "." + mapName + "." + path);
        } else {
            return mapsConfig.getDouble(mapType + ".default-map." + path);
        }
    }

    /**
     * Acquire specific text data for a given map.
     * @param mapType the gamemode for the map
     * @param mapName the name of the map
     * @param path the path to the data
     * @return the data at the path for the given math, or default data if it does not exist
     */
    public static String getMapText(String mapType, String mapName, String path) {
        FileConfiguration mapsConfig = ConfigUtils.getConfigFile("maps.yml");
        if (mapsConfig.contains(mapType + "." + mapName + "." + path)) {
            return convertAmps(mapsConfig.getString(mapType + "." + mapName + "." + path));
        } else {
            return convertAmps(mapsConfig.getString(mapType + ".default-map." + path));
        }
    }
    
    /**
     * Acquire data for an item
     * 
     * @param item
     * @param level
     * @return
     */
    public static Object getItemValue(String item, int level, String get) {
        FileConfiguration itemsConfig = ConfigUtils.getConfigFile("items.yml");
        
        if (itemsConfig.contains(item + "." + level + "." + get)) {
            return itemsConfig.get(item + "." + level + "." + get);
        }
        
        if (itemsConfig.contains(item + "." + get)) {
            return itemsConfig.get(item + "." + get);
        }
        
        if (get.equals("max") || get.equals("cooldown")) {
            return MissileWarsPlugin.getPlugin().getConfig().getInt("default-" + get);
        }
        
        return null;
    }
    
    /**
     * Acquire data for an ability
     * 
     * @param abilityPath
     * @param stat
     * @return
     */
    public static double getAbilityStat(Passive ability, int level, Stat stat) {
        if (ability == null) {
            return 0;
        }
        
        String abilityPath;
        Passive.Type type = ability.getType();
        if (type == Type.GPASSIVE) {
            abilityPath = type + "." + ability;
        } else {
            abilityPath = ability.getDeck() + "." + type + "." + ability;
        }

        Object o = getItemValue(abilityPath, level, stat.toString());
        if (o == null) {
            return 0;
        }
        
        return Double.valueOf(o + "");
    }
    
    /**
     * Remove color codes from a string (stolen from md5 chatcolor class)
     * 
     * @param s
     * @return
     */
    public static String removeColors(String s) {
        Pattern strip = Pattern.compile("(?i)(§|&)[0-9A-FK-ORX]");
        return strip.matcher(s).replaceAll("");
    }
    
    /**
     *  Converted all ampersands in string the chatcolor character
     * 
     * @param s
     * @return
     */
    public static String convertAmps(String s) {
        return s.replaceAll("&", "§");
    }
    
    /**
     * Get a line, translate it to a component.
     * 
     * @param line
     * @return
     */
    public static Component toComponent(String line) {
        return LegacyComponentSerializer.legacySection().deserialize(convertAmps(line)).decoration(TextDecoration.ITALIC, false);
    }
    
    /**
     * Get lines to translate to components
     * 
     * @param line
     * @return
     */
    public static List<Component> toComponent(List<String> lines) {
        List<Component> result = new ArrayList<>();
        for (String s : lines) {
            result.add(toComponent(s));
        }
        return result;
    }

    /**
     * Component to plain text, keeping section color codes!
     * 
     * @param component
     * @return
     */
    public static String toPlain(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
