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
                try (Connection c = conn.getConnection(); PreparedStatement stmt = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS umw_players(
                                uuid CHAR(36) NOT NULL,
                                inventory MEDIUMTEXT,
                                wins INT DEFAULT 0 NOT NULL,
                                games INT DEFAULT 0 NOT NULL,
                                kills INT DEFAULT 0 NOT NULL,
                                missiles INT DEFAULT 0 NOT NULL,
                                utility INT DEFAULT 0 NOT NULL,
                                PRIMARY KEY (uuid)
                        );
                        """
                )) {
                    stmt.execute();
                    logger.log(Level.INFO, "[MissileWars] Default tables have been setup!");
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Failed to setup the MySQL table!");
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
     * Closes everything in case it isn't closed.
     */
    public void onDisable() {
        conn.closePool();
    }
    
}
