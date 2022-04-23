package io.github.vhorvath2010.missilewars.utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitScheduler;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;

/**
 * Class to fetch and set data from the MySQL database in async
 */
public class SQLManager {
    
    private MissileWarsPlugin plugin;
    private final DBConnectionManager conn;
    
    private BukkitScheduler scheduler;
    private Logger logger;
    
    /**
     * @param plugin
     */
    public SQLManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        logger = Bukkit.getLogger();
        scheduler = Bukkit.getScheduler();
        conn = new DBConnectionManager(plugin);
    }
    
    /**
     * Checks whether the connection to the database has been established
     * 
     * @return whether the execution was successful
     */
    public boolean testConnection() {
        try {
            logger.log(Level.INFO, "Testing MYSQL connection...");
            conn.getConnection();
            logger.log(Level.INFO, "Connection established!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "The MySQL connection failed! Check your config.");
            return false;
        }      
        return true;
    }
    
    /**
     * Initial setup of MySQL tables
     */
    public void setupTables() {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                // Player table
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS umw_players(
                                uuid CHAR(36) NOT NULL,
                                inventory MEDIUMTEXT,
                                exp INT DEFAULT 0 NOT NULL,
                                winstreak INT DEFAULT 0 NOT NULL,
                                PRIMARY KEY (uuid)
                        );
                        """
                )) {
                    stmt.execute();
                    logger.log(Level.INFO, "[MissileWars] Setup the umw_players table!");
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to setup the umw_players table!");
                }
                
                // Classic stats table
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS umw_stats_classic(
                                uuid CHAR(36) NOT NULL,
                                wins INT DEFAULT 0 NOT NULL,
                                games INT DEFAULT 0 NOT NULL,
                                kills INT DEFAULT 0 NOT NULL,
                                missiles INT DEFAULT 0 NOT NULL,
                                utility INT DEFAULT 0 NOT NULL,
                                deaths INT DEFAULT 0 NOT NULL,
                                winstreak INT DEFAULT 0 NOT NULL,
                                PRIMARY KEY (uuid),
                                FOREIGN KEY (uuid) REFERENCES umw_players(uuid) 
                        );
                        """
                )) {
                    stmt.execute();
                    logger.log(Level.INFO, "[MissileWars] Setup the umw_stats_classic table!");
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to setup the umw_stats_classic table!");
                }
                
                // CTF stats table
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS umw_stats_ctf(
                                uuid CHAR(36) NOT NULL,
                                captures INT DEFAULT 0 NOT NULL,
                                wins INT DEFAULT 0 NOT NULL,
                                games INT DEFAULT 0 NOT NULL,
                                kills INT DEFAULT 0 NOT NULL,
                                missiles INT DEFAULT 0 NOT NULL,
                                utility INT DEFAULT 0 NOT NULL,
                                deaths INT DEFAULT 0 NOT NULL,
                                winstreak INT DEFAULT 0 NOT NULL,
                                PRIMARY KEY (uuid),
                                FOREIGN KEY (uuid) REFERENCES umw_players(uuid)
                        );
                        """
                )) {
                    stmt.execute();
                    logger.log(Level.INFO, "[MissileWars] Setup the umw_stats_ctf table!");
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to setup the umw_stats_ctf table!");
                }
                
                // Domination stats table
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS umw_stats_domination(
                                uuid CHAR(36) NOT NULL,
                                ptcaptures INT DEFAULT 0 NOT NULL,
                                wins INT DEFAULT 0 NOT NULL,
                                games INT DEFAULT 0 NOT NULL,
                                kills INT DEFAULT 0 NOT NULL,
                                missiles INT DEFAULT 0 NOT NULL,
                                utility INT DEFAULT 0 NOT NULL,
                                deaths INT DEFAULT 0 NOT NULL,
                                winstreak INT DEFAULT 0 NOT NULL,
                                PRIMARY KEY (uuid),
                                FOREIGN KEY (uuid) REFERENCES umw_players(uuid)
                        );
                        """
                )) {
                    stmt.execute();
                    logger.log(Level.INFO, "[MissileWars] Setup the umw_stats_domination table!");
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to setup the umw_stats_domination table!");
                } 
            }
        });
    }
    
    /**
     * Create an entry for a new player
     * 
     * @param uuid
     */
    public void createPlayer(UUID uuid) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        "INSERT IGNORE INTO umw_players(uuid) VALUE(?);"
                )) {
                    stmt.setString(1, uuid.toString());
                    stmt.execute();
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to create a new entry for " + Bukkit.getPlayer(uuid).getName());
                }
            }
        });
    }
    
    
    /**
     * Saves a player inventory to the database
     * 
     * @param uuid
     * @param inventory
     */
    public void setInventory(UUID uuid, String inventory) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        "UPDATE umw_players SET inventory = ? WHERE uuid = ?;"
                )) {
                    stmt.setString(1, inventory);
                    stmt.setString(2, uuid.toString());
                    stmt.execute();
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to set inventory for " + Bukkit.getPlayer(uuid).getName());
                }
            }
        });
    }
    
    /**
     * Gets a player inventory from the database
     * 
     * @param uuid
     * @param callback
     */
    public void getInventory(UUID uuid, DBCallback callback) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        "SELECT inventory FROM umw_players WHERE uuid = ?;"
                )) {
                    stmt.setString(1, uuid.toString());
                    ResultSet resultSet = stmt.executeQuery();
                    String inv = null;
                    if (resultSet.next()) {
                        inv = resultSet.getString("inventory");
                    }
                    final String result = inv;
                    scheduler.runTask(plugin, new Runnable() {  
                        @Override
                        public void run() {
                            callback.onQueryDone(result);
                        }
                        
                    });
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to get inventory for " + Bukkit.getPlayer(uuid).getName());
                }
            }
        });
    }
    
    /**
     * Updates experience value of player
     * 
     * @param uuid
     * @param amount
     */
    public void updateExp(UUID uuid, int amount) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        "UPDATE umw_players SET exp = exp + ? WHERE uuid = ?;"
                )) {
                    stmt.setInt(1, amount);
                    stmt.setString(2, uuid.toString());
                    stmt.execute();
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to update experience for " + Bukkit.getPlayer(uuid).getName());
                }
            }
        });
    }
    
    /**
     * Update classic gamemode stats for a player
     * 
     * @param uuid
     * @param wins
     * @param games
     * @param kills
     * @param missiles
     * @param utility
     */
    public void updateClassicStats(UUID uuid, int wins, int games, int kills, int missiles, int utility, int deaths) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        """
                        INSERT INTO umw_stats_classic(uuid, wins, games, kills, missiles, utility, deaths)
                        VALUES(?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                        wins = wins + VALUES(wins),
                        games = games + VALUES(games), 
                        kills = kills + VALUES(kills), 
                        missiles = missiles + VALUES(missiles), 
                        utility = utility + VALUES(utility), 
                        deaths = deaths + VALUES(deaths);          
                        """
                )) {
                    stmt.setString(1, uuid.toString());
                    stmt.setInt(2, wins);
                    stmt.setInt(3, games);
                    stmt.setInt(4, kills);
                    stmt.setInt(5, missiles);
                    stmt.setInt(6, utility);
                    stmt.setInt(7, deaths);
                    stmt.execute();
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to update stats for " + Bukkit.getPlayer(uuid).getName());
                }
            }
        });
    }
    
    /**
     * Updates a player's winstreak, or resets back to 0 on lose
     * 
     * @param uuid
     * @param gamemode
     * @param won
     */
    public void updateWinstreak(UUID uuid, String gamemode, int won) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                if (won == 1) {
                    
                    String query = 
                            """
                            INSERT INTO umw_stats_$1(uuid, winstreak)
                            VALUES(?, ?)
                            ON DUPLICATE KEY UPDATE
                            winstreak = winstreak + VALUES(winstreak);              
                            """.replace("$1", gamemode);
                    
                    // Update gamemode tables
                    try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                            query
                    )) {
                        stmt.setString(1, uuid.toString());
                        stmt.setInt(2, won);
                        stmt.execute();
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to update winstreak for " + Bukkit.getPlayer(uuid).getName());
                    }
                    
                    // Update overall table
                    try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                            "UPDATE umw_players SET winstreak = winstreak + ? WHERE uuid = ?;"
                    )) {
                        stmt.setInt(1, won);
                        stmt.setString(2, uuid.toString());
                        stmt.execute();
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to update winstreak for " + Bukkit.getPlayer(uuid).getName());
                    }
                } else {
                    
                    String query = 
                            """
                            INSERT INTO umw_stats_$1(uuid, winstreak)
                            VALUES(?, 0)
                            ON DUPLICATE KEY UPDATE
                            winstreak = 0;
                            """.replace("$1", gamemode);
                    
                    // Update gamemode tables
                    try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                            query
                    )) {
                        stmt.setString(1, uuid.toString());
                        stmt.execute();
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to update winstreak for " + Bukkit.getPlayer(uuid).getName());
                    }
                    
                    // Update overall table
                    try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                            "UPDATE umw_players SET winstreak = 0 WHERE uuid = ?;"
                    )) {
                        stmt.setString(1, uuid.toString());
                        stmt.execute();
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Failed to update winstreak for " + Bukkit.getPlayer(uuid).getName());
                    }
                }
            }
        });
    }
    
    /*
    public int getStatRank(UUID uuid, String stat, String gamemode) {
        
    }
    
    public int getOverallStatRank(UUID uuid, String stat) {
        
    }
    
    public int getPlayerStatRank();
    
    public int getGamemodeStatRank();*/
    
    /**
     * The main method of getting the top 10 list for a certain statistic.
     * Here, the statistic can also be exp or winstreak.
     * 
     * @param stat
     * @param gamemode
     * @return An arraylist of player and integer for top ten
     */
    public List<ArrayList<Object>> getTopTenStat(String stat, String gamemode) {
        
        // Case for overall stat
        if (gamemode.equalsIgnoreCase("overall")) {
            
            if (stat.equalsIgnoreCase("exp") || stat.equalsIgnoreCase("winstreak")) {
                return getTopTenPlayerStat(stat);
            }
            
            return getTopTenOverallStat(stat);
        }
        
        // Otherwise,
        return getTopTenGamemodeStat(stat, gamemode);
    }
    
    private List<ArrayList<Object>> getTopTenGamemodeStat(String stat, String gamemode){
        
        List<ArrayList<Object>> result = new ArrayList<ArrayList<Object>>();
        String query = "SELECT uuid, $1 FROM umw_stats_$2 ORDER BY $1 DESC LIMIT 10";
        query = query.replace("$1", stat);
        query = query.replace("$2", gamemode);
        
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                query
        )) {
            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                ArrayList<Object> temp = new ArrayList<Object>();
                
                String uuid = resultSet.getString("uuid");
                int statistic = resultSet.getInt(stat);
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                
                temp.add(player);
                temp.add(statistic);
                result.add(temp);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get top 10 " + gamemode + " " + stat + ".");
        }
        
        return result;
    }
    
    private List<ArrayList<Object>> getTopTenPlayerStat(String stat) {
        
        List<ArrayList<Object>> result = new ArrayList<ArrayList<Object>>();
        String query = "SELECT uuid, $1 FROM umw_players ORDER BY $1 DESC LIMIT 10";
        query = query.replace("$1", stat);
        
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                query
        )) {
            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                ArrayList<Object> temp = new ArrayList<Object>();
                
                String uuid = resultSet.getString("uuid");
                int statistic = resultSet.getInt(stat);
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                
                temp.add(player);
                temp.add(statistic);
                result.add(temp);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get top 10 player " + stat + " stat.");
        }
        return result;
    }
    
    private List<ArrayList<Object>> getTopTenOverallStat(String stat) {
        
        List<ArrayList<Object>> result = new ArrayList<ArrayList<Object>>();
        String query = """
                       SELECT players.uuid,
                       (CASE WHEN a.$1 is NULL THEN 0 ELSE a.$1 END) + 
                       (CASE WHEN b.$1 is NULL THEN 0 ELSE b.$1 END) + 
                       (CASE WHEN c.$1 is NULL THEN 0 ELSE c.$1 END) AS $1
                       FROM umw_players players
                       LEFT JOIN umw_stats_classic a
                       ON players.uuid = a.uuid
                       LEFT JOIN umw_stats_ctf b
                       ON a.uuid = b.uuid
                       LEFT JOIN umw_stats_domination c
                       ON b.uuid = c.uuid
                       ORDER BY $1 DESC LIMIT 10;
                       """.replace("$1", stat);
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                query
        )) {
            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                ArrayList<Object> temp = new ArrayList<Object>();
                
                String uuid = resultSet.getString("uuid");
                int statistic = resultSet.getInt(stat);
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                
                temp.add(player);
                temp.add(statistic);
                result.add(temp);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get top 10 overall " + stat + " stat.");
        }
        return result;
        
    }
    
    /**
     * The main method to find a statistic for a player
     * Find a statistic for a gamemode in sync
     * stat can be any of wins, games, kills, missiles, utility, deaths
     * Or gamemode specific like captures
     * If gamemode is "overall", then the overall stat will be gotten
     * 
     * @param uuid
     * @param gamemode
     * @param stat
     * @return a gamemode statistic
     */
    public int getStatSync(UUID uuid, String gamemode, String stat) {
        
        // Check for overall
        if (gamemode.equalsIgnoreCase("overall")) {
            
            // Special case for winstreak
            if (stat.equalsIgnoreCase("winstreak")) {
                return getOverallWinstreak(uuid);
            }
            
            return getOverallStat(uuid, stat);
        }
        
        // Otherwise just return a gamemode stat
        return getGamemodeStat(uuid, gamemode, stat);
    }
    
    /**
     * Get an overall stat, which is stats from all gamemodes added together
     * The stat needs to be present in all gamemodes, or else errors will be thrown
     * In the case of stat being winstreak, then the overall winstreak will be returned.
     * 
     * @param uuid
     * @param stat
     * @return an overall stat
     */
    private int getOverallStat(UUID uuid, String stat) {
        String query = """
                       SELECT
                       (CASE WHEN a.$1 is NULL THEN 0 ELSE a.$1 END) + 
                       (CASE WHEN b.$1 is NULL THEN 0 ELSE b.$1 END) + 
                       (CASE WHEN c.$1 is NULL THEN 0 ELSE c.$1 END) AS $1
                       FROM umw_players players
                       LEFT JOIN umw_stats_classic a
                       ON players.uuid = a.uuid
                       LEFT JOIN umw_stats_ctf b
                       ON a.uuid = b.uuid
                       LEFT JOIN umw_stats_domination c
                       ON b.uuid = c.uuid
                       WHERE players.uuid = ?;
                       """.replace("$1", stat);
        int result = 0;
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                query
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                result = resultSet.getInt(stat);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get overall stat " + stat + " for " +
                    Bukkit.getPlayer(uuid).getName());
        }
        return result;
    }
    
    /**
     * Gets the overall winstreak of a player
     * 
     * @param uuid
     * @return a players overall winstreak
     */
    private int getOverallWinstreak(UUID uuid) {
        int winstreak = 0;
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                "SELECT winstreak FROM umw_players WHERE uuid = ?;"
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                winstreak = resultSet.getInt("winstreak");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get overall winstreak for " + 
                    Bukkit.getPlayer(uuid).getName());
        }
        return winstreak;
    }
    
    /**
     * Gets the statistic from a specific gamemode for a specific player
     * 
     * @param uuid
     * @param gamemode
     * @param stat
     * @return the statistic as an int
     */
    private int getGamemodeStat(UUID uuid, String gamemode, String stat) {
        String query = "SELECT $1 FROM umw_stats_$2 WHERE uuid = ?;";
        int result = 0;
        query = query.replace("$1", stat.toLowerCase());
        query = query.replace("$2", gamemode.toLowerCase());
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                query
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                result = resultSet.getInt(stat);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get " + gamemode + " stat " + stat + " for " +
                    Bukkit.getPlayer(uuid).getName());
        }
        return result;
    }

    
    /**
     * Gets player exp value from the database in sync
     * 
     * @param uuid
     */
    public int getExpSync(UUID uuid) {
        int exp = 0;
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                "SELECT exp FROM umw_players WHERE uuid = ?;"
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                exp = resultSet.getInt("exp");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get exp for " + Bukkit.getPlayer(uuid).getName());
        }
        return exp;
    }
    
    /**
     * Gets player exp value from database
     * 
     * @param uuid
     * @param callback
     */
    public void getExp(UUID uuid, DBCallback callback) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        "SELECT exp FROM umw_players WHERE uuid = ?;"
                )) {
                    stmt.setString(1, uuid.toString());
                    ResultSet resultSet = stmt.executeQuery();
                    int exp = 0;
                    if (resultSet.next()) {
                        exp = resultSet.getInt("exp");
                    }
                    int finalExp = exp;
                    scheduler.runTask(plugin, new Runnable() {  
                        @Override
                        public void run() {
                            callback.onQueryDone(finalExp);
                        }
                        
                    });
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to get exp for " + Bukkit.getPlayer(uuid).getName());
                }
            }
        });
    }
    
    /**
     * Gets a player nickname from the Chatcontrol database.
     * this feels illegal to be honest
     * 
     * @param uuid
     * @return 
     */
    public String getPlayerNick(UUID uuid) {
        String nick = Bukkit.getOfflinePlayer(uuid).getName();
        try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                "SELECT Nick FROM ChatControl WHERE uuid = ?;"
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                if (resultSet.getString("Nick") != null) {
                    nick = resultSet.getString("Nick");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get nickname of player " + nick + " from CHC database.");
        }
        return nick;
    }
    
    /**
     * Closes everything in case it isn't closed.
     */
    public void onDisable() {
        conn.closePool();
    }
    
}
