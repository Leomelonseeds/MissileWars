package io.github.vhorvath2010.missilewars.utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
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
    public void updateClassicStats(UUID uuid, int wins, int games, int kills, int missiles, int utility) {
        scheduler.runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        """
                        INSERT INTO umw_stats_classic(uuid, wins, games, kills, missiles, utility)
                        VALUES(?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                        wins = wins + VALUES(wins),
                        games = games + VALUES(games), 
                        kills = kills + VALUES(kills), 
                        missiles = missiles + VALUES(missiles), 
                        utility = utility + VALUES(utility)                    
                        """
                )) {
                    stmt.setString(1, uuid.toString());
                    stmt.setInt(2, wins);
                    stmt.setInt(3, games);
                    stmt.setInt(4, kills);
                    stmt.setInt(5, missiles);
                    stmt.setInt(6, utility);
                    stmt.execute();
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to update stats for " + Bukkit.getPlayer(uuid).getName());
                }
            }
        });
    }
    

    
    /**
     * Gets player exp value from the database
     * 
     * @param uuid
     */
    public int getExp(UUID uuid) {
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
            return 0;
        }
        return exp;
    }
    
    /**
     * Closes everything in case it isn't closed.
     */
    public void onDisable() {
        conn.closePool();
    }
    
}
