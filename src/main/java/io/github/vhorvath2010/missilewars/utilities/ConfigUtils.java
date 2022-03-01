package io.github.vhorvath2010.missilewars.utilities;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Utility Class for acquiring data from config files. */
public class ConfigUtils {

    /**
     * Method to get a config file from its yml name.
     *
     * @param dir the directory of the config file
     * @param configName the name of the config file
     * @return the config
     */
    public static FileConfiguration getConfigFile(String dir, String configName) {
        File file = new File(dir, configName);
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
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
            msg = msg.replaceAll("%umw_arena_players%", "" + arena.getNumPlayers());
            msg = msg.replaceAll("%umw_arena_cap%", "" + arena.getCapacity());
            msg = msg.replaceAll("%umw_team%", arena.getTeam(player.getUniqueId()));
            msg = msg.replaceAll("%umw_position%", "" + arena.getPositionInQueue(player.getUniqueId()));
            msg = msg.replaceAll("%umw_time%", "" + arena.getSecondsUntilStart());
            msg = msg.replaceAll("%umw_time_remaining%", "" + arena.getMinutesRemaining());
            // TODO: Implement placeholders for during game and end of game
        }

        // Set umw arena-less placeholders
        msg = msg.replaceAll("%umw_chaos_time%", "" + Arena.getChaosTime());
        FileConfiguration messageConfig = getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "messages.yml");
        msg = msg.replaceAll("umw_waiting", messageConfig.getString("placeholders.status.waiting"));
        msg = msg.replaceAll("umw_active", messageConfig.getString("placeholders.status.active"));
        msg = msg.replaceAll("umw_full", messageConfig.getString("placeholders.status.full"));
        msg = msg.replaceAll("umw_finished", messageConfig.getString("placeholders.status.finished"));
        if (focus != null) {
            msg = msg.replaceAll("%umw_focus%", focus.getDisplayName());
        }

        // Set PAPI placeholders and color
        String parsedMsg = PlaceholderAPI.setPlaceholders(player, msg);
        return ChatColor.translateAlternateColorCodes('&', parsedMsg);
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
    }

}
