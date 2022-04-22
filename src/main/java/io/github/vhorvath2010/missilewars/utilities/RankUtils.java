package io.github.vhorvath2010.missilewars.utilities;

import org.bukkit.configuration.file.FileConfiguration;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import net.md_5.bungee.api.ChatColor;

public class RankUtils {
    
    /**
     * Convert rank level to total exp
     * 
     * @return rank level -> exp
     */
    private static int f(int x) {
        return 500 * x * x + 250;
    }
    
    /**
     * Gets rank level from an exp value
     * 
     * @param exp
     * @return the rank level
     */
    public static int getRankLevel(int exp) {
        if (exp < 250) {
            return 0;
        }
        return Math.min(10, (int) Math.floor(Math.sqrt((exp - 250)/500)));
    }
    
    /**
     * Gets the exp required to reach the next level
     * 
     * @param rank
     * @return the exp required for the NEXT level
     */
    public static int getNextExp(int exp) {
        int current = getRankLevel(exp);
        return f(current + 1) - f(current);
    }
    
    /**
     * Gets current exp in the level
     * 
     * @param exp
     * @return current amount of exp
     */
    public static int getCurrentExp(int exp) {
        int current = getRankLevel(exp);
        return exp - f(current);
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
        FileConfiguration rankConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "ranks.yml");
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
        FileConfiguration rankConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "ranks.yml");
        String rankColor = rankConfig.getString("ranks." + rank + ".color");
        String rankSymbol = rankConfig.getString("ranks." + rank + ".symbol");
        return ChatColor.translateAlternateColorCodes('&', rankColor + rankSymbol);
    }
}
