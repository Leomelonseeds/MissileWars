package com.leomelonseeds.missilewars.utilities;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;

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
    public boolean persist() {
        return true;
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

        if (params.equalsIgnoreCase("deck")) {
            JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
            String deck = json.getString("Deck");
            String preset = json.getString("Preset");
            ChatColor chatcolor = ChatColor.DARK_GREEN;
            switch (deck) {
                case "Vanguard":
                    chatcolor = ChatColor.GOLD;
                    break;
                case "Sentinel":
                    chatcolor = ChatColor.AQUA;
                    break;
                case "Berserker":
                    chatcolor = ChatColor.RED;
            }
            return chatcolor + json.getString("Deck") + ChatColor.GRAY + " [" + preset + "]";
        }

        // Rank placeholders
        if (params.contains("rank_")) {

            int exp = MissileWarsPlugin.getPlugin().getSQL().getExpSync(player.getUniqueId());
            int level = RankUtils.getRankLevel(exp);
            int max = 10;

            if (params.equalsIgnoreCase("rank_level")) {
                return Integer.toString(level);
            }
            
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

        // Stats placeholders. Includes top 10 placeholders, which includes top 10 exp values.
        if (params.contains("stats")) {

            SQLManager sql = MissileWarsPlugin.getPlugin().getSQL();
            String[] args = params.split("_");
            String gamemode = args[1];
            String stat = args[2];

            // stats_[gamemode/overall]_[stat]
            if (args.length == 3) {
                return Integer.toString(sql.getStatSync(player.getUniqueId(), stat, gamemode));
            }

            // Args length must be 4 now, since we are looking for a top stat
            // stats_[gamemode/overall]_[stat]_top_#
            // a formatted entry for a leaderboard spot
            // # can be 1-10, nothing more or error
            if (args[3].equals("top")) {
                List<ArrayList<Object>> list = sql.getTopTenStat(stat, gamemode);
                int index = Integer.parseInt(args[4]) - 1;
                String playerName = RankUtils.getLeaderboardPlayer((OfflinePlayer) list.get(index).get(0));
                String playerStat = Integer.toString((int) list.get(index).get(1));
                return ChatColor.translateAlternateColorCodes('&', playerName + " &7- &f" + playerStat);
            }

            // stats_[gamemode/overall]_[stat]_rank
            // Gets the player position in for that stat
            if (args[3].equals("rank")) {
                return Integer.toString(sql.getStatRank(player.getUniqueId(), stat, gamemode));
            }

            return null;
        }
        
        if (params.contains("count")) {
            return Integer.toString(manager.getPlayers(params.split("_")[1]));
        }

        if (params.equalsIgnoreCase("arena")) {
            return playerArena == null ? "Lobby" : StringUtils.capitalize(playerArena.getName());
        }

        if (playerArena == null) {
            return null;
        }

        // Arena specific placeholders

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
            return ConfigUtils.getMapText(playerArena.getGamemode(), playerArena.getMapName(), "name");
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
            return redTeam.getRemainingPortals() + "";
        }

        if (params.equalsIgnoreCase("blue_portals")) {

            return blueTeam.getRemainingPortals() + "";
        }

        return null; // Placeholder is unknown by the Expansion
    }

}
