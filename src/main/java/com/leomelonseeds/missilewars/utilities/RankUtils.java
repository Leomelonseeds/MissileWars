package com.leomelonseeds.missilewars.utilities;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

import net.md_5.bungee.api.ChatColor;

public class RankUtils {

    /**
     * Function for converting rank level to exp required for next level
     * Changing this function changes the whole rank system
     *
     * @param x
     * @return exp required to get to level x+1
     */
    private static int f(int x) {
        if (x < 0) {
            return 0;
        }
        return 500 * x * x + 250;
    }

    /**
     * Gets rank level from an exp value
     *
     * @param exp
     * @return the rank level
     */
    public static int getRankLevel(int exp) {

        int total = 0;
        for (int i = 0; i <= 10; i++) {
            total = total + f(i);
            if (total > exp) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Get total exp required to reach given level
     *
     * @param rank
     * @return int
     */
    public static int getTotalToLevel(int rank) {
        int total = 0;
        for (int i = rank - 1; i >= 0; i--) {
            total = total + f(i);
        }
        return total;
    }

    /**
     * Gets the exp required to reach the next level
     *
     * @param rank
     * @return the exp required for the next level
     */
    public static int getNextExp(int exp) {
        int current = getRankLevel(exp);
        return f(current);
    }

    /**
     * Gets current exp in the level
     *
     * @param exp
     * @return current amount of exp
     */
    public static int getCurrentExp(int exp) {
        int current = getRankLevel(exp);
        return exp - getTotalToLevel(current);
    }

    /**
     * Gets progress for player
     *
     * @param exp
     * @return Progress in percentage
     */
    public static double getExpProgress(int exp) {
        return (double) getCurrentExp(exp) / getNextExp(exp);
    }

    /**
     * Get a cool progress bar for player exp progress
     *
     * @param exp
     * @param size
     * @return A nice, formatted progress bar
     */
    public static String getProgressBar(int exp, int size) {
        String progress = "";
        for (int i = 0; i < size; i++) {
            progress = progress + "|";
        }
        int index = (int) (size * getExpProgress(exp));
        progress = ChatColor.LIGHT_PURPLE + progress.substring(0, index) +
                ChatColor.GRAY + progress.substring(index, size);
        return progress;
    }

    /**
     * Get rank name for certain exp value
     *
     * @param exp
     * @return Rank name of rank
     */
    public static String getRankName(int exp) {
        int rank = getRankLevel(exp);
        FileConfiguration rankConfig = ConfigUtils.getConfigFile("ranks.yml");
        String rankName = rankConfig.getString("ranks." + rank + ".name");
        return ChatColor.translateAlternateColorCodes('&', rankName);
    }

    /**
     * Get rank symbol for certain exp value
     *
     * @param exp
     * @return Rank symbol of rank
     */
    public static String getRankSymbol(int exp) {
        int rank = getRankLevel(exp);
        FileConfiguration rankConfig = ConfigUtils.getConfigFile("ranks.yml");
        String rankColor = rankConfig.getString("ranks." + rank + ".color");
        String rankSymbol = rankConfig.getString("ranks." + rank + ".symbol");
        return ChatColor.translateAlternateColorCodes('&', rankColor + rankSymbol);
    }

    /**
     * Sets player current experience level
     *
     * @param player
     * @param exp
     */
    public static void setPlayerExpBar(Player player) {
        MissileWarsPlugin.getPlugin().getSQL().getExp(player.getUniqueId(), result -> {
            int exp = (int) result;
            int rank = getRankLevel(exp);
            double progress = getExpProgress(exp);
            player.setLevel(rank);
            player.setExp((float) progress);

        });
    }

    /**
     * Gets the name to display for a player on the leaderboard
     *
     * @param player
     * @return the leaderboard player name
     */
    public static String getLeaderboardPlayer(OfflinePlayer player) {
        String prefix = MissileWarsPlugin.getPlugin().getChat().getPlayerPrefix(null, player);

        // Don't show guest prefix on leaderboard
        if (prefix.contains("Guest")) {
            prefix = "&7";
        }

        String nick = MissileWarsPlugin.getPlugin().getSQL().getPlayerNick(player.getUniqueId());
        int exp = MissileWarsPlugin.getPlugin().getSQL().getExpSync(player.getUniqueId());

        return prefix + nick + " " + getRankSymbol(exp);
    }
    
    /**
     * Add some exp to the player.
     * 
     * @param player
     * @param exp
     */
    public static void addExp(Player player, int exp) {
        int current = MissileWarsPlugin.getPlugin().getSQL().getExpSync(player.getUniqueId());
        int currentlevel = getRankLevel(current);
        int newexp = current + exp;
        int newlevel = getRankLevel(newexp);
        
        if (newlevel > currentlevel) {
            ConfigUtils.sendConfigSound("rankup", player);
            for (String s : ConfigUtils.getConfigTextList("messages.rankup", null, null, null)) {
                s = s.replaceAll("%previous%", getRankName(current));
                s = s.replaceAll("%current%", getRankName(newexp));
                s = s.replaceAll("%sp%", ConfigUtils.getConfigFile("items.yml").getInt("default-skillpoints") + newlevel + "");
                
                player.sendMessage(s);
            }
            String othermessage = ConfigUtils.getConfigText("messages.rankup-others", null, null, player);
            othermessage = othermessage.replaceAll("%previous%", getRankName(current));
            othermessage = othermessage.replaceAll("%current%", getRankName(newexp));
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equals(player.getName())) {
                    p.sendMessage(othermessage);
                }
            }
            
            MissileWarsPlugin.getPlugin().getJSON().rankUp(player.getUniqueId());
        }

        MissileWarsPlugin.getPlugin().getSQL().updateExp(player.getUniqueId(), exp);
    }
}
