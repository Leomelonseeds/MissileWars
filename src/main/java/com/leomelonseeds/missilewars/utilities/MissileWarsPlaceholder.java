package com.leomelonseeds.missilewars.utilities;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer.Stat;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.db.SQLManager;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class MissileWarsPlaceholder extends PlaceholderExpansion {


    @Override
    public String getIdentifier() {
        return "umw";
    }

    @Override
    public String getAuthor() {
        return "M310N";
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
        if (!MissileWarsPlugin.getPlugin().isEnabled() || player == null) {
            return null;
        }

        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        DecimalFormat df = new DecimalFormat("##.##");

        // General purpose placeholders
        if (params.equals("focus")) {
            return ConfigUtils.getFocusName(player);
        }

        if (params.contains("team")) {
            TeamName team = playerArena == null ? TeamName.NONE : playerArena.getTeam(player.getUniqueId());
            if (params.equals("team")) {
                return team.toString();
            }
            if (params.equals("team_color")) {
                return team.getColor();
            }
        }
        
        if (params.contains("deck")) {
            // If the json hasn't finished loading yet what are ya gonna do
            JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
            if (json == null) {
                return null;
            }
            
            DeckStorage deck = DeckStorage.fromString(json.getString("Deck"));
            if (params.equals("deck_plain")) {
                return deck.toString();
            }
            
            // umw_deck_npcname_[deck]
            // Changes based on player's selected deck
            if (params.contains("npcname")) {
                DeckStorage plDeck = DeckStorage.fromString(params.split("_")[2]);
                String res = plDeck.getNPCName();
                if (plDeck == deck) {
                    String stripped = ConfigUtils.removeColors(res);
                    String[] parts = stripped.split(" ");
                    String symbold = res.contains("§r") ? "" : "&l";
                    res = "&7› " + deck.getColor() + "&l&n" + parts[0] + " " + 
                                   deck.getColor() + "&n" + symbold + parts[1] + "&r &7‹";
                }
                
                return res;
            }

            String preset = json.getString("Preset");
            if (params.equals("deck")) {
                return deck.getColor() + deck + "§7 [" + preset + "]";
            }
            
            FileConfiguration sec = ConfigUtils.getConfigFile("items.yml");
            String type = params.split("_")[1];
            String passive = json.getJSONObject(deck.toString()).getJSONObject(preset).getJSONObject(type).getString("selected");
            if (passive.equals("None")) {
                return "None";
            }

            String path = type.equals("gpassive") ? "gpassive." + passive + ".name" :
                deck + "." + type + "." + passive + ".name";
            return sec.getString(path);
        }

        // Rank placeholders
        if (params.contains("rank_")) {

            int exp = MissileWarsPlugin.getPlugin().getSQL().getExpSync(player.getUniqueId());
            int level = RankUtils.getRankLevel(exp);
            int max = 10;

            if (params.equals("rank_level")) {
                return Integer.toString(level);
            }
            
            if (params.equals("rank_exp_total")) {
                return Integer.toString(exp);
            }

            if (params.equals("rank_name")) {
                return RankUtils.getRankName(exp);
            }

            if (params.equals("rank_symbol")) {
                return RankUtils.getRankSymbol(exp);
            }

            if (params.equals("rank_exp")) {
                if (level >= max) {
                    return "0";
                }
                return Integer.toString(RankUtils.getCurrentExp(exp));
            }

            if (params.equals("rank_exp_next")) {
                if (level >= max) {
                    return "N/A";
                }
                return Integer.toString(RankUtils.getNextExp(exp));
            }

            if (params.equals("rank_progress_percentage")) {
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
                    return "§7" + result;
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
                return ConfigUtils.convertAmps(playerName + " &7- &f" + playerStat);
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

        if (params.equals("arena")) {
            return playerArena == null ? "Lobby" : StringUtils.capitalize(playerArena.getName());
        }

        if (params.equals("ingame")) {
            return playerArena == null ? "false" : Boolean.toString(playerArena.isRunning() || playerArena.isResetting());
        }

        if (playerArena == null) {
            return null;
        }

        // Arena specific placeholders
        
        if (params.contains("tutorial")) {
            if (!(playerArena instanceof TutorialArena)) {
                return null;
            }

            String arg = params.split("_")[1];
            if (arg.equals("maxstages")) {
                return TutorialArena.MAX_STAGES + "";
            }
            
            Integer stage = ((TutorialArena) playerArena).getStage(player.getUniqueId());
            if (arg.equals("stage")) {
                return stage + "";
            }
            
            int line = Integer.parseInt(params.split("_")[1]);
            if (stage == null) {
                return "";
            }
            
            List<String> lines = ConfigUtils.getConfigTextList("scoreboard.stage" + stage, null, null, null);
            if (line >= lines.size()) {
                return "";
            }
            return lines.get(line);
        }

        if (params.equals("gamemode")) {
            return "§a" + "Classic";
        }

        if (params.equals("red_queue")) {
            return Integer.toString(playerArena.getRedQueue());
        }

        if (params.equals("blue_queue")) {
            return Integer.toString(playerArena.getBlueQueue());
        }

        // In-Game placeholders
        MissileWarsTeam redTeam = playerArena.getRedTeam();
        MissileWarsTeam blueTeam = playerArena.getBlueTeam();

        if (params.equals("red_team")) {
            if (redTeam == null) {
                return "0";
            }
            return Integer.toString(redTeam.getSize());
        }

        if (params.equals("blue_team")) {
            if (blueTeam == null) {
                return "0";
            }
            return Integer.toString(blueTeam.getSize());
        }

        if (params.equals("map")) {
            return ConfigUtils.getMapText(playerArena.getGamemode(), playerArena.getMapName(), "name");
        }

        if (params.equals("time_remaining")) {
            return playerArena.getTimeRemaining();
        }

        if (params.equals("red_shield_health")) {
            double health = Math.max(0, redTeam.getShieldHealth());
            String chatcolor;
            if (health >= 90) {
                chatcolor = "§2";
            } else if (health >= 80) {
                chatcolor = "§a";
            } else if (health >= 70) {
                chatcolor = "§e";
            } else if (health >= 60) {
                chatcolor = "§6";
            } else if (health >= 50) {
                chatcolor = "§c";
            } else {
                chatcolor = "§4";
            }
            return chatcolor + df.format(health)+ "%";
        }

        if (params.equals("blue_shield_health")) {
            double health = Math.max(0, blueTeam.getShieldHealth());
            String chatcolor;
            if (health >= 90) {
                chatcolor = "§2";
            } else if (health >= 80) {
                chatcolor = "§a";
            } else if (health >= 70) {
                chatcolor = "§e";
            } else if (health >= 60) {
                chatcolor = "§6";
            } else if (health >= 50) {
                chatcolor = "§c";
            } else {
                chatcolor = "§4";
            }
            return chatcolor + df.format(health)+ "%";
        }

        if (params.equals("red_portals")) {
            return redTeam.getRemainingPortals() + "";
        }

        if (params.equals("blue_portals")) {
            return blueTeam.getRemainingPortals() + "";
        }

        if (params.equals("red_portals_total")) {
            return redTeam.getTotalPortals() + "";
        }

        if (params.equals("blue_portals_total")) {
            return blueTeam.getTotalPortals() + "";
        }

        if (params.equals("kills")) {
            return playerArena.getPlayerInArena(player.getUniqueId()).getStat(Stat.KILLS) + "";
        }

        return null; // Placeholder is unknown by the Expansion
    }
}
