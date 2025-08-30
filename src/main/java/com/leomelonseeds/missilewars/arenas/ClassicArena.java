package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer.Stat;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsTeam;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;
import com.leomelonseeds.missilewars.utilities.db.SQLManager;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;

public class ClassicArena extends Arena {
    
    private final static int GLOW_TICKS = 300;
    
    /** LEFT = RED, RIGHT = BLUE */
    private Map<MissileWarsPlayer, MutablePair<BukkitTask, BukkitTask>> glowTasks;
    
    public ClassicArena(String name, int capacity) {
        super(name, capacity);
        gamemode = "classic";
        this.glowTasks = new HashMap<>();
    }

    public ClassicArena(Map<String, Object> serializedArena) {
        super(serializedArena);
        this.glowTasks = new HashMap<>();
    }
    
    @Override
    public void resetWorld() {
        glowTasks.values().forEach(p -> {
            for (BukkitTask t : new BukkitTask[] {p.getLeft(), p.getRight()}) {
                if (t != null && !t.isCancelled()) {
                    t.cancel();
                }
            }
        });
        glowTasks.clear();
        redTeam.destroyPortalGlow(false);
        blueTeam.destroyPortalGlow(false);
        super.resetWorld();
    }

    @Override
    public void addCallback(MissileWarsPlayer player) {
        glowPortals(blueTeam, player);
        glowPortals(redTeam, player);
        super.addCallback(player);
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
                team.addPortal(new Location(getWorld(), x, y, z));
            }
        }
    }
    
    private Pair<Integer, String> getTopStat(Stat stat) {
        List<MissileWarsPlayer> most = new ArrayList<>();
        for (MissileWarsPlayer player : players.values()) {
            if (getTeam(player.getMCPlayerId()) == TeamName.NONE) {
                continue;
            }
            
            if (most.isEmpty() || most.get(0).getStat(stat) < player.getStat(stat)) {
                most.clear();
                most.add(player);
            } else if (most.get(0).getStat(stat) == player.getStat(stat)) {
                most.add(player);
            }
        }
        
        List<String> mostList = new ArrayList<>();
        for (MissileWarsPlayer player : most) {
            mostList.add(ConfigUtils.getFocusName(player.getMCPlayer()));
        }
        
        String mostString = String.join(", ", mostList);
        int mostAmount = most.isEmpty() ? 0 : most.get(0).getStat(stat);
        return Pair.of(mostAmount, mostString);
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
        Pair<Integer, String> mostPortals = getTopStat(Stat.PORTALS);
        Pair<Integer, String> mostKills = getTopStat(Stat.KILLS);
        Pair<Integer, String> mostDeaths = getTopStat(Stat.DEATHS);

        Economy econ = MissileWarsPlugin.getPlugin().getEconomy();
        LocalDateTime endTime = LocalDateTime.now();
        long gameTime = Duration.between(startTime, endTime).toSeconds();
        
        // Calculate win message
        List<String> actualWinMessages = new ArrayList<>();
        String winner = winningTeam == null ? "&e&lNONE" : winningTeam == blueTeam ? "&9&lBLUE" : "&c&lRED";
        for (String s : winningMessages) {
            s = s.replaceAll("%umw_winning_team%", winner);
            s = s.replaceAll("%umw_most_mvp_amount%", mostPortals.getLeft() + "");
            s = s.replaceAll("%umw_most_kills_amount%", mostKills.getLeft() + "");
            s = s.replaceAll("%umw_most_deaths_amount%", mostDeaths.getLeft() + "");
            s = s.replaceAll("%umw_most_mvp%", mostPortals.getRight());
            s = s.replaceAll("%umw_most_kills%", mostKills.getRight());
            s = s.replaceAll("%umw_most_deaths%", mostDeaths.getRight());
            actualWinMessages.add(s);
        }

        // Update stats for each player
        for (MissileWarsPlayer player : players.values()) {
            // Send win message
            for (String s : actualWinMessages) {
                player.getMCPlayer().sendMessage(ConfigUtils.toComponent(s));
            }

            // -1 = TIE, 0 = LOST, 1 = WIN
            int won = winningTeam == null ? -1 : 0;

            // Calculate currency gain per-game
            UUID uuid = player.getMCPlayerId();
            if (getTeam(uuid) == TeamName.NONE) {
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

            int portals = player.getStat(Stat.PORTALS);
            int missiles = player.getStat(Stat.MISSILES);
            int utility = player.getStat(Stat.UTILITY);
            int kills = player.getStat(Stat.KILLS);
            int deaths = player.getStat(Stat.DEATHS);
            playerAmount = spawn_missile * missiles +
                           use_utility * utility +
                           kill * kills +
                           (int) (portal_broken * portals);
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

            sql.updateClassicStats(uuid, portals, Math.max(won, 0), 1, kills, missiles, utility, deaths);
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
            glowPortals(broketeam);
        }
        
        // Check if has associated player
        Player player;
        if (entity instanceof Player) {
            player = (Player) entity;
        } else {
            player = ArenaUtils.getAssociatedPlayer(entity, this);
        }
        
        // Send messages if player found
        if (player != null && getTeam(player.getUniqueId()) != TeamName.NONE) {
            // Only add to stats if on opposite team
            if (enemy.containsPlayer(player.getUniqueId())) {
                getPlayerInArena(player.getUniqueId()).incrementStat(Stat.PORTALS);
            }
        }
        Component msg = CosmeticUtils.getPortalMessage(player, broketeam.getName());
        for (MissileWarsPlayer mwPlayer : players.values()) {
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
        glowPortals(enemy);
        if (getSecondsRemaining() <= 300) {
            endGame(enemy);
        } else {
            enemy.sendTitle("enemy-portals-destroyed");
            broketeam.sendTitle("own-portals-destroyed");
            endGame(enemy, MissileWarsPlugin.getPlugin().getConfig().getInt("tie-wait-time") * 20);
        }
    }
    
    /**
     * Makes portals from the specified team glow for every player
     * in the arena. Portals are hidden after 10 seconds
     * 
     * @param team
     */
    protected void glowPortals(MissileWarsTeam team) {
        players.values().forEach(mwp -> glowPortals(team, mwp));
    }
    
    /**
     * Makes all available portals from the specified team glow for the specified
     * player. The portals are hidden after 10 seconds.
     * 
     * @param player
     * @param keep
     */
    protected void glowPortals(MissileWarsTeam team, MissileWarsPlayer player) {
        team.setPortalGlow(player, true);
        boolean isRed = team.getName() == TeamName.RED;
        BukkitTask remTask = ConfigUtils.schedule(GLOW_TICKS, () -> team.setPortalGlow(player, false));
        MutablePair<BukkitTask, BukkitTask> curTasks = glowTasks.get(player);
        if (curTasks == null) {
            if (isRed) {
                glowTasks.put(player, MutablePair.of(remTask, null));
            } else {
                glowTasks.put(player, MutablePair.of(null, remTask));
            }
            return;
        }
        
        if (isRed) {
            if (curTasks.getLeft() != null) {
                curTasks.getLeft().cancel();
            }
            curTasks.setLeft(remTask);
        } else {
            if (curTasks.getRight() != null) {
                curTasks.getRight().cancel();
            }
            curTasks.setRight(remTask);
        }
    }
}
