package com.leomelonseeds.missilewars.utilities;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

/** Utility Class for acquiring data from config files. */
public class ConfigUtils {

    // Map of open cached config files
    private static Map<String, FileConfiguration> configCache = new HashMap<>();
    
    /**
     * Get config file from default folder
     * 
     * @param configName
     * @return
     */
    public static FileConfiguration getConfigFile(String configName) {
        // Check for config file in cache
        if (configCache.containsKey(configName)) {
            return configCache.get(configName);
        }

        File file = new File(MissileWarsPlugin.getPlugin().getDataFolder().toString(), configName);
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        configCache.put(configName, config);
        return config;
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
            String status = ChatColor.GOLD + "In Lobby";
            if (arena.isRunning()) {
                status = ChatColor.GREEN + "In Game";
            } else if (arena.isResetting()) {
                status = ChatColor.RED + "Resetting";
            }
            msg = msg.replaceAll("%umw_arena_status%", status);
            if (player != null) {
                msg = msg.replaceAll("%umw_team%", arena.getTeam(player.getUniqueId()));
                msg = msg.replaceAll("%umw_position%", "" + arena.getPositionInQueue(player.getUniqueId()));
            }
            // TODO: Implement placeholders for during game and end of game
        }

        // Set umw arena-less placeholders
        msg = msg.replaceAll("%umw_chaos_time%", "" + Arena.getChaosTime() / 60);
        FileConfiguration messageConfig = getConfigFile("messages.yml");
        msg = msg.replaceAll("umw_waiting", messageConfig.getString("placeholders.status.waiting"));
        msg = msg.replaceAll("umw_active", messageConfig.getString("placeholders.status.active"));
        msg = msg.replaceAll("umw_full", messageConfig.getString("placeholders.status.full"));
        msg = msg.replaceAll("umw_finished", messageConfig.getString("placeholders.status.finished"));
        if (focus != null) {
            msg = msg.replaceAll("%umw_focus%", getFocusName(focus));
        }

        // Set PAPI placeholders and color
        if (player != null) {
            msg = PlaceholderAPI.setPlaceholders(player, msg);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
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
     * Send a configurable message with placeholders set for player.
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
                player.sendMessage(setPlaceholders(prefix + msg, player, arena, focus));
            }
        }

        // Send single line message
        else {
            String msg = messagesConfig.getString(path);
            player.sendMessage(setPlaceholders(prefix + msg, player, arena, focus));
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
     * Send a sound to the player, with location! Mainly
     * useful for utility items.
     *
     * @param path the key of the sound in the sounds.yml file
     * @param player the player to send sound to
     * @param location the location to send the sound to
     */
    public static void sendConfigSound(String path, Player player, Location location) {
        FileConfiguration soundConfig = getConfigFile("sounds.yml");

        if (!soundConfig.contains(path)) {
        	return;
        }

        Sound sound = Sound.valueOf(soundConfig.getString(path + ".sound"));
        float volume = (float) soundConfig.getDouble(path + ".volume");
        float pitch = (float) soundConfig.getDouble(path + ".pitch");

        player.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }
    
    /**
     * Send the team a title at a given path.
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
            if (a.getTeam(player.getUniqueId()).contains("red")) {
                if (subtitle.contains("umw_red")) {
                    subtitle = subtitle.replace("red", "blue");
                } else if (subtitle.contains("umw_blue")) {
                    subtitle = subtitle.replace("blue", "red");
                }
            }
            subtitle = PlaceholderAPI.setPlaceholders(player, subtitle);
        }

        int length = Integer.parseInt(getConfigText("titles." + path + ".length", null, null, null));
        
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(length * 50), Duration.ofMillis(1000));
        Title finalTitle = Title.title(Component.text(title), Component.text(subtitle), times);

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
        Arena playerArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(player.getUniqueId());
        String displayName = MissileWarsPlugin.getPlugin().getSQL().getPlayerNick(player.getUniqueId());
        if (playerArena != null) {
            ChatColor teamColor = playerArena.getTeamColor(player.getUniqueId());
            if (teamColor != null) {
                return teamColor + ChatColor.stripColor(displayName);
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
            return ChatColor.translateAlternateColorCodes('&',
                    mapsConfig.getString(mapType + "." + mapName + "." + path));
        } else {
            return ChatColor.translateAlternateColorCodes('&',
                    mapsConfig.getString(mapType + ".default-map." + path));
        }
    }
    
    
    /**
     * Check if given location is in given arena with given team
     * 
     * @param arena
     * @param location
     * @param team
     * @return
     */
    public static boolean inShield(Arena arena, Location location, String team) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String teamName = ChatColor.stripColor(team).toLowerCase();
        int x1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.x1");
        int x2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.x2");
        int y1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.y1");
        int y2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.y2");
        int z1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.z1");
        int z2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.z2");
        if (x1 <= x && x <= x2 && y1 <= y && y <= y2 && z1 <= z && z <= z2) {
            return true;
        }
        return false;
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
        } else if (itemsConfig.contains(item + "." + get)) {
            return itemsConfig.get(item + "." + get);
        } else {
            return null;
        }
    }
    
    /**
     * Acquire data for an ability
     * 
     * @param abilityPath
     * @param stat
     * @return
     */
    public static double getAbilityStat(String abilityPath, int level, String stat) {
        Object o = getItemValue(abilityPath, level, stat);
        if (o == null) {
            return 0;
        } else {
            return Double.valueOf(o + "");
        }
    }
    
    /**
     * Get a line, translate it to a component.
     * 
     * @param line
     * @return
     */
    public static Component toComponent(String line) {
        return Component.text(ChatColor.translateAlternateColorCodes('&', line));
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
     * Component to plain text!
     * 
     * @param component
     * @return
     */
    public static String toPlain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
