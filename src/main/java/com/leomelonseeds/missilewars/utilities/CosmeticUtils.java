package com.leomelonseeds.missilewars.utilities;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

public class CosmeticUtils {
    
    /**
     * Get a death message depending on a player and a killer
     * 
     * @param player
     * @param killer
     * @return
     */
    public static Component getDeathMessage(Player player, Player killer) {
        FileConfiguration messages = ConfigUtils.getConfigFile("death-messages.yml", "/cosmetics");
        String damageCause = player.getLastDamageCause().getCause().toString();
        String format = "default";
        if (killer == null) {
        }
    }
    
    private static String getFromDeathConfig(String format, String path) {
        return null;
    }

}
