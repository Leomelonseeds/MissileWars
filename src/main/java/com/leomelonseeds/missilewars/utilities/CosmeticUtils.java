package com.leomelonseeds.missilewars.utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer.Stat;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.utilities.db.SQLManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.HSVLike;

/** Various statistic and cosmetic related methods */
public class CosmeticUtils {
    
    /**
     * Get the item stack for a cosmetic item
     * 
     * @return
     */
    public static List<ItemStack> getCosmeticItems(Player player, String cosmetic) {
        JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        FileConfiguration cosmetics = ConfigUtils.getConfigFile(cosmetic + ".yml", "/cosmetics");
        FileConfiguration messages = ConfigUtils.getConfigFile("messages.yml");
        List<ItemStack> result = new ArrayList<>();
        for (String s : cosmetics.getKeys(false)) {
            // Get item
            ConfigurationSection section = cosmetics.getConfigurationSection(s + ".menu-item");
            ItemStack item = new ItemStack(Material.getMaterial(section.getString("item")));
            ItemMeta meta = item.getItemMeta();
            
            // Display name + lore
            meta.displayName(ConfigUtils.toComponent(section.getString("name")));
            List<String> lore = section.getStringList("lore");
            
            // Add extra lore
            String toUse = "locked";
            if (json.getString(cosmetic).equals(s)) {
                toUse = "selected";
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else if (hasPermission(player, cosmetic, s)) {
                toUse = "not-selected";
            }
            lore.addAll(messages.getStringList("inventories.cosmetics." + toUse));
            
            // Add meta to store name of cosmetic item
            meta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "name"),
                    PersistentDataType.STRING, s);
            
            // Finally create item
            meta.lore(ConfigUtils.toComponent(lore));
            item.setItemMeta(meta);
            result.add(item);
        }
        return result;
    }
    
    /**
     * Get a death message depending on a player and a killer
     * 
     * @param player
     * @param killer
     * @return
     */
    public static Component getDeathMessage(Player dead, Player killer) {
        FileConfiguration messages = ConfigUtils.getConfigFile("death-messages.yml", "/cosmetics");
        String damageCause = "";
        if (dead.getLastDamageCause() != null) {
            damageCause = dead.getLastDamageCause().getCause().toString().toLowerCase();
        }
        
        String format;
        String result;
        if (killer == null || !killer.isOnline()) {
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
                if (!damageCause.contains(s)) {
                    continue;
                }
                
                result = getFromConfig(messages, format, "kill." + s);
                
                // Add kill count if flex
                if (format.equals("flex")) {
                    UUID killerID = killer.getUniqueId();
                    Arena arena = ArenaUtils.getArena(killer);
                    if (arena == null) {
                        break;
                    }
                    
                    SQLManager sql = MissileWarsPlugin.getPlugin().getSQL();
                    TeamName teamKiller = arena.getTeam(killerID);
                    TeamName teamDead = arena.getTeam(dead.getUniqueId());
                    if (teamKiller != teamDead) {
                        int kills = sql.getStatSync(killerID, "kills", "overall") + 
                                arena.getPlayerInArena(killerID).getStat(Stat.KILLS);
                        result += "&7's kill &e#" + kills;
                    } else {
                        result = getFromConfig(messages, "default", "kill." + s);
                    }
                }
                break;
            }
            
            result = result.replace("%killer%", ConfigUtils.getFocusName(killer));
            
            // Add item name if one of these death causes
            if (!dead.equals(killer)) {
                String[] itemCauses = {"void", "attack", "fall", "magic", "projectile"};
                for (String cause : itemCauses) {
                    if (!damageCause.contains(cause)) {
                        continue;
                    }
                    
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
        
        result = result.replace("%dead%", ConfigUtils.getFocusName(dead));
        
        // Rainbow if rainbow
        if (format.equals("rainbow")) {
            return toRainbow(ConfigUtils.convertAmps(result));
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
    public static Component getPortalMessage(Player killer, TeamName brokeTeam) {
        String teamcode = brokeTeam == TeamName.RED ? "&c" : "&9";
        if (killer == null || !killer.isOnline()) {
            return ConfigUtils.toComponent(ConfigUtils.getConfigText("messages.portal-broke", null, null, null)
                    .replace("%team%", teamcode + brokeTeam));
        }
        FileConfiguration messages = ConfigUtils.getConfigFile("death-messages.yml", "/cosmetics");
        String prefix = "&5&l[!] &r";
        String format = getFormat("death-messages", killer);
        String result = getFromConfig(messages, format, "portal");

        // Portal count if flex
        if (format.equals("flex")) {
            UUID killerID = killer.getUniqueId();
            Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(killerID);
            SQLManager sql = MissileWarsPlugin.getPlugin().getSQL();
            if (arena != null) {
                TeamName teamKiller = arena.getTeam(killerID);
                if (teamKiller != brokeTeam) {
                    int portals = sql.getStatSync(killerID, "portals", "classic") + 
                            arena.getPlayerInArena(killerID).getStat(Stat.PORTALS);
                    result += "&7's portal break &e#" + portals;
                } else {
                    result = getFromConfig(messages, "default", "portal");
                }
            }
        }
        
        result = result.replace("%killer%", ConfigUtils.getFocusName(killer));
        result = result.replace("%team%", teamcode + brokeTeam);
        
        // Add rainbow if rainbow
        if (format.equals("rainbow")) {
            return ConfigUtils.toComponent(prefix).append(toRainbow(result));
        }
        result = prefix + result;
        return ConfigUtils.toComponent(result);
    }
    
    /**
     * Checks if player has permission for a specific cosmetic item. Returns true
     * if the cosmetic item is the default one
     * 
     * @param player
     * @param cosmetic
     * @param name
     * @return
     */
    public static boolean hasPermission(Player player, String cosmetic, String name) {
        if (name.equals("default")) {
            return true;
        }
        return player.hasPermission("umw." + cosmetic + "." + name);
    }
    
    // Makes a text rainbow.
    public static TextComponent toRainbow(String input) {
        return toRainbow(input, 21);
    }
    
    // Makes a text rainbow.
    public static TextComponent toRainbow(String input, int charsBeforeFlip) {
        String stripped = ConfigUtils.removeColors(input);
        Builder result = Component.text();
        String[] characters = stripped.split("");
        double step = (double) 1 / charsBeforeFlip;
        for (int i = 0; i < characters.length; i++) {
            float hsv = (float) (step * i - Math.floor(step * i));
            result.append(Component.text().content(characters[i]).color(TextColor.color(HSVLike.hsvLike(hsv, 1, 1))));
        }
        
        return result.build();
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
