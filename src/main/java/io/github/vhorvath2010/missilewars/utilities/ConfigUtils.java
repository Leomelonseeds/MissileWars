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

/** Utility Class for acquiring data from config files. */
public class ConfigUtils {

    /**
     * Method to get a config file from its yml name.
     *
     * @param name the name of the file
     * @return the config
     */
    public static FileConfiguration getConfigFile(String name) {
        File file = new File(MissileWarsPlugin.getPlugin().getDataFolder(), name);
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
     * @param player the player to set placeholders with
     * @param arena the arena to associate the placeholders with
     */
    private static String setPlaceholders(String msg, Player player, Arena arena) {
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
        FileConfiguration messageConfig = getConfigFile("messages.yml");
        msg = msg.replaceAll("umw_waiting", messageConfig.getString("placeholders.status.waiting"));
        msg = msg.replaceAll("umw_active", messageConfig.getString("placeholders.status.active"));
        msg = msg.replaceAll("umw_full", messageConfig.getString("placeholders.status.full"));
        msg = msg.replaceAll("umw_finished", messageConfig.getString("placeholders.status.finished"));

        // Set PAPI placeholders and color
        String parsedMsg = PlaceholderAPI.setPlaceholders(player, msg);
        return ChatColor.translateAlternateColorCodes('&', parsedMsg);
    }

    /**
     * Send a configurable message with placeholders set for player.
     *
     * @param path the path to the message in the messages.yml file
     * @param player the player to set placeholders with
     * @param arena the arena to associate the placeholders with
     */
    public static void sendConfigMessage(String path, Player player, Arena arena) {
        FileConfiguration messagesConfig = getConfigFile("messages.yml");
        String prefix = messagesConfig.getString("messages.prefix");

        // Check for multi line message
        if (!messagesConfig.getStringList(path).isEmpty()) {
            for (String msg : messagesConfig.getStringList(path)) {
                player.sendMessage(setPlaceholders(prefix + msg, player, arena));
            }
        }

        // Send single line message
        else {
            String msg = messagesConfig.getString(path);
            player.sendMessage(setPlaceholders(prefix + msg, player, arena));
        }
    }

}
