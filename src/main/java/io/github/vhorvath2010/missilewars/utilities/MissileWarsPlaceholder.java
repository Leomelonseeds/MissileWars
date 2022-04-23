package io.github.vhorvath2010.missilewars.utilities;

import java.text.DecimalFormat;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsTeam;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class MissileWarsPlaceholder extends PlaceholderExpansion {


    @Override
    public String getIdentifier() {
        return "umw";
    }

    @Override
    public String getAuthor() {
        return "Vhbob";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        
        // Stop trying to fetch placeholders if the plugin is disabled
        if (!MissileWarsPlugin.getPlugin().isEnabled()) {
            return null;
        }
        
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        DecimalFormat df = new DecimalFormat("##.##");

        // General purpose placeholders
        if (params.equalsIgnoreCase("focus")) {
            return ConfigUtils.getFocusName(player);
        }
        
        if (params.equalsIgnoreCase("team")) {
            return playerArena == null ? "no team" : ChatColor.stripColor(playerArena.getTeam(player.getUniqueId()));
        } 
        
        // Rank placeholders
        if (params.contains("rank")) {
            
            int exp = MissileWarsPlugin.getPlugin().getSQL().getExpSync(player.getUniqueId());
            int level = RankUtils.getRankLevel(exp);
            int max = 10;
            
            if (params.equalsIgnoreCase("rank_exp_total")) {
                return Integer.toString(exp);
            }
            
            if (params.equalsIgnoreCase("rank_name")) {
                return RankUtils.getRankName(exp);
            }
            
            if (params.equalsIgnoreCase("rank_symbol")) {
                return RankUtils.getRankSymbol(exp);
            }
            
            if (params.equalsIgnoreCase("rank_exp")) {
                if (level >= max) {
                    return "0";
                }
                return Integer.toString(RankUtils.getCurrentExp(exp));
            }
            
            if (params.equalsIgnoreCase("rank_exp_next")) {
                if (level >= max) {
                    return "N/A";
                }
                return Integer.toString(RankUtils.getNextExp(exp));
            }
            
            if (params.equalsIgnoreCase("rank_progress_percentage")) {
                if (level >= max) {
                    return "0%";
                }
                return df.format(RankUtils.getExpProgress(exp) * 100) + "%";
            }
            
            if (params.contains("rank_progress_bar")) {
                String[] args = params.split("_");
                int size = Integer.parseInt(args[3]);
                if (level >= max) {
                    String result = "";
                    for (int i = 0; i < size; i++) {
                        result = result + "|";
                    }
                    return ChatColor.GRAY + result;
                }
                return RankUtils.getProgressBar(exp, size);
            }
            
            return null;
        }
        
        // Stats placeholders
        if (params.contains("stats")) {
            
            SQLManager sql = MissileWarsPlugin.getPlugin().getSQL();
            String[] args = params.split("_");
            String gamemode = args[1];
            String stat = args[2];
            int result = 0;
            
            // stats_[gamemode/overall]_[stat]
            if (args.length == 3) {
                if (gamemode.equalsIgnoreCase("overall")) {
                    result = sql.getOverallStatSync(player.getUniqueId(), stat);
                } else {
                    result = sql.getGamemodeStatSync(player.getUniqueId(), gamemode, stat);
                }
                return Integer.toString(result);
            }
            
            // stats_[gamemode/overall]_[stat]_top_#_player
            // Gets the player name of a top stat
            
            // stats_[gamemode/overall]_[stat]_top_#_stat
            // Gets the statistic number of a top stat
            
            // stats_[gamemode/overall]_[stat]_rank
            // Gets the player position in for that stat
            
            return null;
        }
        
        if (playerArena == null) {
            return null;
        }
        
        // Arena specific placeholders
        
        if (params.equalsIgnoreCase("arena")) {
            return playerArena.getName();
        }
        
        if (params.equalsIgnoreCase("gamemode")) {
            return ChatColor.GREEN + "Classic";
        }
        
        boolean inGame = playerArena.isRunning() || playerArena.isResetting();
        
        if (params.equalsIgnoreCase("ingame")) {
            return inGame ? "true" : "false";
        }
        
        if (params.equalsIgnoreCase("red_queue")) {
            return Integer.toString(playerArena.getRedQueue());
        }
        
        if (params.equalsIgnoreCase("blue_queue")) {
            return Integer.toString(playerArena.getBlueQueue());
        }
        
        if (!inGame) {
            return null;
        }

        // In-Game placeholders
        MissileWarsTeam redTeam = playerArena.getRedTeam();
        MissileWarsTeam blueTeam = playerArena.getBlueTeam();
        
        if (params.equalsIgnoreCase("map")) {
            return ConfigUtils.getMapText(playerArena.getMapType(), playerArena.getMapName(), "name");
        }
        
        if (params.equalsIgnoreCase("time_remaining")) {
            return playerArena.getTimeRemaining();
        }
        
        if (params.equalsIgnoreCase("red_shield_health")) {
            double health = Math.max(0, redTeam.getShieldHealth());
            ChatColor chatcolor;
            if (health >= 90) {
                chatcolor = ChatColor.DARK_GREEN;
            } else if (health >= 80) {
                chatcolor = ChatColor.GREEN;
            } else if (health >= 70) {
                chatcolor = ChatColor.YELLOW; 
            } else if (health >= 60) {
                chatcolor = ChatColor.GOLD;
            } else if (health >= 50) {
                chatcolor = ChatColor.RED;
            } else {
                chatcolor = ChatColor.DARK_RED;
            }
            return chatcolor + df.format(health)+ "%";
        }

        if (params.equalsIgnoreCase("blue_shield_health")) {
            double health = Math.max(0, blueTeam.getShieldHealth());
            ChatColor chatcolor;
            if (health >= 90) {
                chatcolor = ChatColor.DARK_GREEN;
            } else if (health >= 80) {
                chatcolor = ChatColor.GREEN;
            } else if (health >= 70) {
                chatcolor = ChatColor.YELLOW; 
            } else if (health >= 60) {
                chatcolor = ChatColor.GOLD;
            } else if (health >= 50) {
                chatcolor = ChatColor.RED;
            } else {
                chatcolor = ChatColor.DARK_RED;
            }
            return chatcolor + df.format(health)+ "%";
        }
        
        if (params.equalsIgnoreCase("red_team")) {
            return Integer.toString(redTeam.getSize());
        }
        
        if (params.equalsIgnoreCase("blue_team")) {
            return Integer.toString(blueTeam.getSize());
        }
        
        if (params.equalsIgnoreCase("red_portals")) {
            String firstPortal = redTeam.getFirstPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            String secondPortal = redTeam.getSecondPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            if (playerArena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.RED + "red" + ChatColor.RESET)) {
                return firstPortal + secondPortal;
            }
            return secondPortal + firstPortal;
        }
        
        if (params.equalsIgnoreCase("blue_portals")) {
            String firstPortal = blueTeam.getFirstPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            String secondPortal = blueTeam.getSecondPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            if (playerArena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.BLUE + "blue" + ChatColor.RESET)) {
                return secondPortal + firstPortal;
            }
            return firstPortal + secondPortal;
        }

        return null; // Placeholder is unknown by the Expansion
    }

}
