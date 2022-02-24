package io.github.vhorvath2010.missilewars.utilities;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

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

}
