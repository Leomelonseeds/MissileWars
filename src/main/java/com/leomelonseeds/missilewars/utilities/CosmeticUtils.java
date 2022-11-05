package com.leomelonseeds.missilewars.utilities;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.HSVLike;
import net.md_5.bungee.api.ChatColor;

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
        String format;
        String result;
        if (killer == null) {
            format = getFormat("death-messages", dead);
            Set<String> possible = messages.getConfigurationSection("default.death").getKeys(false);
            result = getFromConfig(messages, format, "death.other");
            for (String s : possible) {
                if (damageCause.contains(s)) {
                    result = getFromConfig(messages, format, "death." + s);
                    break;
                }
            }
        } else {
            format = getFormat("death-messages", killer);
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
        // Rainbow if rainbow
        if (format.equals("rainbow")) {
            return toRainbow(ChatColor.translateAlternateColorCodes('&', result));
        }
        return ConfigUtils.toComponent(result);
    }
    
    /**
     * Get a portal break message depending on killer
     * 
     * @param killer
     * @param brokeTeam
     * @return
     */
    public static Component getPortalMessage(Player killer, String brokeTeam) {
        if (killer == null) {
            return ConfigUtils.toComponent(ConfigUtils.getConfigText("messages.portal-broke", null, null, null)
                    .replace("%team%", brokeTeam));
        }
        FileConfiguration messages = ConfigUtils.getConfigFile("death-messages.yml", "/cosmetics");
        String prefix = "&5&l[!] ";
        String format = getFormat("death-messages", killer);
        String result = getFromConfig(messages, format, "portal");
        result = result.replace("%killer%", ConfigUtils.getFocusName(killer));
        if (format.equals("rainbow")) {
            return Component.text(ChatColor.translateAlternateColorCodes('&', prefix))
                    .append(toRainbow(ChatColor.translateAlternateColorCodes('&', result)));
        }
        result = prefix + result;
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
    
    // Makes a text rainbow.
    private static TextComponent toRainbow(String input) {
        String stripped = ChatColor.stripColor(input);
        Builder result = Component.text();
        String[] characters = stripped.split("");
        int charsBeforeFlip = 21;
        double step = (double) 1 / charsBeforeFlip;
        for (int i = 0; i < characters.length; i++) {
            float hsv = (float) (step * i - Math.floor(step * i));
            result.append(Component.text().content(characters[i]).color(TextColor.color(HSVLike.hsvLike(hsv, 1, 1))));
        }
        
        return result.build();
    }
}
