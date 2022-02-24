package io.github.vhorvath2010.missilewars.utilities;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
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
     * Send a configurable message with placeholders set for player.
     *
     * @param path the path to the message in the messages.yml file
     * @param player the player to set placeholders with
     */
    public void sendConfigMessage(String path, Player player) {
        FileConfiguration messagesConfig = getConfigFile("messages.yml");
        String prefix = messagesConfig.getString("messages.prefix");
        for (String msg : messagesConfig.getStringList(path)) {
            String parsedMsg = prefix + PlaceholderAPI.setPlaceholders(player, msg);
            String coloredMsg = ChatColor.translateAlternateColorCodes('&', parsedMsg);
            player.sendMessage(coloredMsg);
        }
    }

}
