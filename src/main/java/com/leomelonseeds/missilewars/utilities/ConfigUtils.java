package com.leomelonseeds.missilewars.utilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/** Utility Class for acquiring data from config files. */
public class ConfigUtils {

    // Map of open cached config files
    private static Map<String, FileConfiguration> configCache = new HashMap<>();

    /**
     * Method to get a config file from its yml name.
     *
     * @param dir the directory of the config file
     * @param configName the name of the config file
     * @return the config
     */
    public static FileConfiguration getConfigFile(String dir, String configName) {
        // Check for config file in cache
        if (configCache.containsKey(configName)) {
            return configCache.get(configName);
        }

        File file = new File(dir, configName);
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
            msg = msg.replaceAll("%umw_arena%", arena.getName());
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
        FileConfiguration messageConfig = getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "messages.yml");
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
        FileConfiguration messagesConfig = getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "messages.yml");
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
        FileConfiguration messagesConfig = getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "messages.yml");
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
        FileConfiguration messagesConfig = getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "messages.yml");
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
        FileConfiguration soundConfig = getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "sounds.yml");

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
        FileConfiguration soundConfig = getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "sounds.yml");

        if (!soundConfig.contains(path)) {
        	return;
        }

        Sound sound = Sound.valueOf(soundConfig.getString(path + ".sound"));
        float volume = (float) soundConfig.getDouble(path + ".volume");
        float pitch = (float) soundConfig.getDouble(path + ".pitch");

        player.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
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
        if (playerArena != null) {
            ChatColor teamColor = playerArena.getTeamColor(player.getUniqueId());
            if (teamColor != null) {
                return teamColor + ChatColor.stripColor(player.getPlayer().getDisplayName());
            }
        }
        return player.getPlayer().getDisplayName();
    }

    /**
     * Acquire specific numerical data for a given map.
     * @param mapType the gamemode for the map
     * @param mapName the name of the map
     * @param path the path to the data
     * @return the data at the path for the given math, or default data if it does not exist
     */
    public static double getMapNumber(String mapType, String mapName, String path) {
        FileConfiguration mapsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "maps.yml");
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
        FileConfiguration mapsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "maps.yml");
        if (mapsConfig.contains(mapType + "." + mapName + "." + path)) {
            return ChatColor.translateAlternateColorCodes('&',
                    mapsConfig.getString(mapType + "." + mapName + "." + path));
        } else {
            return ChatColor.translateAlternateColorCodes('&',
                    mapsConfig.getString(mapType + ".default-map." + path));
        }
    }

}
