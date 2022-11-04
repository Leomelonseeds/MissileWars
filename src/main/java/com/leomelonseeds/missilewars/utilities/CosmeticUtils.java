package com.leomelonseeds.missilewars.utilities;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

import net.kyori.adventure.text.Component;

/** Various statistic and cosmetic related methods */
public class CosmeticUtils {
    
    /**
     * Get a death message depending on a player and a killer
     * 
     * @param player
     * @param killer
     * @return
     */
    public static Component getDeathMessage(Player dead, Player killer) {
        FileConfiguration messages = ConfigUtils.getConfigFile("death-messages.yml", "/cosmetics");
        String damageCause = dead.getLastDamageCause().getCause().toString().toLowerCase();
        String result;
        if (killer == null) {
            String format = getFormat("death-messages", dead);
            Set<String> possible = messages.getConfigurationSection("default.death").getKeys(false);
            result = getFromConfig(messages, format, "death.other");
            for (String s : possible) {
                if (damageCause.contains(s)) {
                    result = getFromConfig(messages, format, "death." + s);
                    break;
                }
            }
        } else {
            String format = getFormat("death-messages", killer);
            Set<String> possible = messages.getConfigurationSection("default.kill").getKeys(false);
            result = getFromConfig(messages, format, "kill.other");
            for (String s : possible) {
                if (damageCause.contains(s)) {
                    result = getFromConfig(messages, format, "kill." + s);
                    break;
                }
            }
            result = result.replace("%killer%", ConfigUtils.getFocusName(killer));
            
            // Add item name if one of these death causes
            if (!dead.equals(killer)) {
                String[] itemCauses = {"void", "attack", "fall", "magic", "projectile"};
                for (String cause : itemCauses) {
                    if (damageCause.contains(cause)) {
                        ItemStack item = killer.getInventory().getItemInMainHand();
                        if (item.getType() != Material.AIR && item.hasItemMeta()) {
                            String name = ConfigUtils.toPlain(item.displayName());
                            String using = getFromConfig(messages, format, "weapon");
                            result += using.replace("%item%", name.replaceAll("\\[|\\]", ""));
                        }
                        break;
                    }
                }
            }
        }
        result = result.replace("%dead%", ConfigUtils.getFocusName(dead));
        return ConfigUtils.toComponent(result);
    }
    
    // Get from config or return default if not found
    private static String getFromConfig(FileConfiguration config, String format, String path) {
        if (config.contains(format + "." + path)) {
            return config.getString(format + "." + path);
        } else {
            return config.getString("default." + path);
        }
    }

    // Get the player's selected cosmetic
    private static String getFormat(String cosmetic, Player player) {
        JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        return json.getString(cosmetic);
    }
}
