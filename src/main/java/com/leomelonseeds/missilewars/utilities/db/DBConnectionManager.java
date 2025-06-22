package com.leomelonseeds.missilewars.utilities.db;

import java.sql.Connection;
import java.sql.SQLException;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DBConnectionManager {

    private final MissileWarsPlugin plugin;
    private HikariDataSource dataSource;


    public DBConnectionManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        setupDatabase();
    }

    private void setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + plugin.getConfig().getString("mysql.url"));
        config.setUsername(plugin.getConfig().getString("mysql.username"));
        config.setPassword(plugin.getConfig().getString("mysql.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(10);

        dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
