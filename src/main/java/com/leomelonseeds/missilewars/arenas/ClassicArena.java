package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.SQLManager;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;

public class ClassicArena extends Arena {
    
    public ClassicArena(String name, int capacity) {
        super(name, capacity);
        gamemode = "classic";
    }

    public ClassicArena(Map<String, Object> serializedArena) {
        super(serializedArena);
    }
    
    @Override
    protected void performGamemodeSetup() {
        // Setup portals
        for (MissileWarsTeam team : new MissileWarsTeam[] {blueTeam, redTeam}) {
            FileConfiguration maps = ConfigUtils.getConfigFile("maps.yml");
            ConfigurationSection config = maps.getConfigurationSection(gamemode + "." + mapName + "." + "portals");
            if (config == null) {
                config = maps.getConfigurationSection(gamemode + ".default-map.portals");
            }
            for (String s : config.getKeys(false)) {
                double x = config.getDouble(s + ".x");
                double y = config.getDouble(s + ".y");
                double z = config.getDouble(s + ".z");
                if (team == redTeam) {
                    z = z * -1;
                }
                Location portalLoc = new Location(getWorld(), x, y, z);
                team.getPortals().put(portalLoc, true);
            }
        }
    }
    
    @Override
    protected void calculateStats(MissileWarsTeam winningTeam) {
        // Setup player variables
        List<String> winningMessages = ConfigUtils.getConfigTextList("messages." + gamemode + "-end", null, null, null);
        String earnMessage = ConfigUtils.getConfigText("messages.earn-currency", null, null, null);
        FileConfiguration ranksConfig = ConfigUtils.getConfigFile("ranks.yml");
        int spawn_missile = ranksConfig.getInt("experience.spawn_missile");
        int use_utility = ranksConfig.getInt("experience.use_utility");
        int kill = ranksConfig.getInt("experience.kill");
        double portal_broken = (double) ranksConfig.getInt("experience.portal_broken") / blueTeam.getTotalPortals();
        int shield_health = ranksConfig.getInt("experience.shield_health");
        int win = ranksConfig.getInt("experience.win");

        int red_shield_health_amount = ((int) ((100 - blueTeam.getShieldHealth())) / 10) * shield_health;
        int blue_shield_health_amount = ((int) ((100 - redTeam.getShieldHealth())) / 10) * shield_health;

        // Find players with mvp, most deaths, and kills
        List<MissileWarsPlayer> mvp = new ArrayList<>();
        List<MissileWarsPlayer> mostKills = new ArrayList<>();
        List<MissileWarsPlayer> mostDeaths = new ArrayList<>();
        for (MissileWarsPlayer player : players) {
            if (getTeam(player.getMCPlayerId()).equals("no team")) {
                continue;
            }
            
            // Top MVPs
            if (mvp.isEmpty() || mvp.get(0).getMVP() < player.getMVP()) {
                mvp.clear();
                mvp.add(player);
            } else if (mvp.get(0).getMVP() == player.getMVP()) {
                mvp.add(player);
            }
            // Top kills
            if (mostKills.isEmpty() || mostKills.get(0).getKills() < player.getKills()) {
                mostKills.clear();
                mostKills.add(player);
            } else if (mostKills.get(0).getKills() == player.getKills()) {
                mostKills.add(player);
            }
            // Top deaths
            if (mostDeaths.isEmpty() || mostDeaths.get(0).getDeaths() < player.getDeaths()) {
                mostDeaths.clear();
                mostDeaths.add(player);
            } else if (mostDeaths.get(0).getDeaths() == player.getDeaths()) {
                mostDeaths.add(player);
            }
        }

        // Produce most mvp/kills/deaths list
        List<String> mostMVPList = new ArrayList<>();
        for (MissileWarsPlayer player : mvp) {
            mostMVPList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_mvp = String.join(", ", mostMVPList);
        
        List<String> mostKillsList = new ArrayList<>();
        for (MissileWarsPlayer player : mostKills) {
            mostKillsList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_kills = String.join(", ", mostKillsList);

        List<String> mostDeathsList = new ArrayList<>();
        for (MissileWarsPlayer player : mostDeaths) {
            mostDeathsList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        String most_deaths = String.join(", ", mostDeathsList);

        int most_mvp_amount = mvp.isEmpty() ? 0 : mvp.get(0).getMVP();
        int most_kills_amount = mostKills.isEmpty() ? 0 : mostKills.get(0).getKills();
        int most_deaths_amount = mostDeaths.isEmpty() ? 0 : mostDeaths.get(0).getDeaths();

        Economy econ = MissileWarsPlugin.getPlugin().getEconomy();
        LocalDateTime endTime = LocalDateTime.now();
        long gameTime = Duration.between(startTime, endTime).toSeconds();
        
        // Calculate win message
        List<String> actualWinMessages = new ArrayList<>();
        String winner = winningTeam == null ? "&e&lNONE" : winningTeam == blueTeam ? "&9&lBLUE" : "&c&lRED";
        for (String s : winningMessages) {
            s = s.replaceAll("%umw_winning_team%", winner);
            s = s.replaceAll("%umw_most_mvp_amount%", Integer.toString(most_mvp_amount));
            s = s.replaceAll("%umw_most_kills_amount%", Integer.toString(most_kills_amount));
            s = s.replaceAll("%umw_most_deaths_amount%", Integer.toString(most_deaths_amount));
            s = s.replaceAll("%umw_most_mvp%", most_mvp);
            s = s.replaceAll("%umw_most_kills%", most_kills);
            s = s.replaceAll("%umw_most_deaths%", most_deaths);
            actualWinMessages.add(s);
        }

        // Update stats for each player
        for (MissileWarsPlayer player : players) {
            // Send win message
            for (String s : actualWinMessages) {
                player.getMCPlayer().sendMessage(ConfigUtils.toComponent(s));
            }

            // -1 = TIE, 0 = LOST, 1 = WIN
            int won = winningTeam == null ? -1 : 0;

            // Calculate currency gain per-game
            UUID uuid = player.getMCPlayerId();
            if (getTeam(uuid).equals("no team")) {
                continue;
            }

            int amountEarned = 0;
            int playerAmount = 0;
            int teamAmount = 0;
            long playTime = Duration.between(player.getJoinTime(), endTime).toSeconds();
            if (playTime <= 40) {
                ConfigUtils.sendConfigMessage("messages.earn-none-time", player.getMCPlayer(), null, null);
                continue;
            }
            
            playerAmount = spawn_missile * player.getMissiles() +
                           use_utility * player.getUtility() +
                           kill * player.getKills() +
                           (int) (portal_broken * player.getMVP());
            if (blueTeam.containsPlayer(uuid)) {
                teamAmount = blue_shield_health_amount;
                if (winningTeam == blueTeam) {
                    teamAmount += win;
                    won = 1;
                }
            } else {
                teamAmount = red_shield_health_amount;
                if (winningTeam == redTeam) {
                    teamAmount += win;
                    won = 1;
                }
            }
            
            double percentPlayed = (double) playTime / gameTime;
            amountEarned = playerAmount + (int) (percentPlayed * teamAmount);

            // Update player stats
            SQLManager sql = MissileWarsPlugin.getPlugin().getSQL();

            sql.updateClassicStats(uuid, player.getMVP(), won, 1, player.getKills(), player.getMissiles(), player.getUtility(), player.getDeaths());
            sql.updateWinstreak(uuid, gamemode, won);
            RankUtils.addExp(player.getMCPlayer(), amountEarned);

            String earnMessagePlayer = earnMessage.replaceAll("%umw_amount_earned%", Integer.toString(amountEarned));
            player.getMCPlayer().sendMessage(ConfigUtils.toComponent(earnMessagePlayer));
            econ.depositPlayer(player.getMCPlayer(), amountEarned);
        }
    }
    
    /**
     * Register the breaking of a portal at a location in this Arena.
     *
     * @param location the location
     */
    public void registerPortalBreak(Location location, Entity entity) {
        // Ignore if game not running
        if (!running) {
            return;
        }

        // Check if portal broke at blue or red z
        MissileWarsTeam broketeam;
        MissileWarsTeam enemy;
        int z = location.getBlockZ();
        if (z > 0) {
            broketeam = redTeam;
            enemy = blueTeam;
        } else {
            broketeam = blueTeam;
            enemy = redTeam;
        }
        
        // Check if portal break was registered
        if (!broketeam.registerPortalBreak(location)) {
            return;
        }
        
        // Check if team still has living portals
        if (broketeam.hasLivingPortal()) {
            broketeam.sendTitle("own-portal-destroyed");
            enemy.sendTitle("enemy-portal-destroyed");
        }
        
        // Check if has associated player
        Player player = ConfigUtils.getAssociatedPlayer(entity, this);
        
        // Send messages if player found
        if (player != null && !getTeam(player.getUniqueId()).equals("no team")) {
            // Only add to stats if on opposite team
            if (enemy.containsPlayer(player.getUniqueId())) {
                getPlayerInArena(player.getUniqueId()).addToMVP(1);
            }
        }
        Component msg = CosmeticUtils.getPortalMessage(player, broketeam.getName());
        for (MissileWarsPlayer mwPlayer : players) {
            mwPlayer.getMCPlayer().sendMessage(msg);
        }
        
        // End game if both do not have living portal (only possible when waiting for tie)
        if (!(redTeam.hasLivingPortal() || blueTeam.hasLivingPortal())) {
            endGame(null);
            return;
        }
        
        // Otherwise don't do anything if tie is waiting
        if (waitingForTie) {
            return;
        }
        
        // And also don't do anything if both teams are alive
        if (redTeam.hasLivingPortal() && blueTeam.hasLivingPortal()) {
            return;
        }

        // Check if either team's last portal has been broken
        // We know from the above conditions that only one team has a living portal in this case
        // and furthermore, the team that is ALIVE is enemy since break was registered for broketeam
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        int wait = plugin.getConfig().getInt("tie-wait-time");
        if (getSecondsRemaining() <= 300) {
            endGame(enemy);
        } else {
            enemy.sendTitle("enemy-portals-destroyed");
            broketeam.sendTitle("own-portals-destroyed");
            waitingForTie = true;
            
            // Set all to spectator to prevent further action
            for (MissileWarsPlayer mwp : broketeam.getMembers()) {
                mwp.getMCPlayer().setGameMode(GameMode.SPECTATOR);
            }
            for (MissileWarsPlayer mwp : enemy.getMembers()) {
                mwp.getMCPlayer().setGameMode(GameMode.SPECTATOR);
            }
            
            // Setup tie for tie wait time
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (enemy.hasLivingPortal()) {
                    endGame(enemy);
                }
            }, wait * 20L));
        }
    }
}
