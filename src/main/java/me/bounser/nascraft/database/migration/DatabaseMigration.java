package me.bounser.nascraft.database.migration;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.database.DatabaseType;
import me.bounser.nascraft.database.mysql.MySQL;
import me.bounser.nascraft.database.sqlite.SQLite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Utility class to migrate data from SQLite to MySQL
 */
public class DatabaseMigration {

    private final SQLite sqliteDatabase;
    private final MySQL mysqlDatabase;

    public DatabaseMigration(SQLite sqliteDatabase, MySQL mysqlDatabase) {
        this.sqliteDatabase = sqliteDatabase;
        this.mysqlDatabase = mysqlDatabase;
    }

    /**
     * Migrate all data from SQLite to MySQL
     */
    public void migrateAll() {
        Nascraft.getInstance().getLogger().info("Starting database migration from SQLite to MySQL...");

        try {
            // Create tables in MySQL
            mysqlDatabase.createTables();

            // Migrate each table
            migrateTable("items", "identifier, lastprice, lowest, highest, stock, taxes");
            migrateTable("prices_day", "day, date, identifier, price, volume");
            migrateTable("prices_month", "day, date, identifier, price, volume");
            migrateTable("prices_history", "day, date, identifier, price, volume");
            migrateTable("portfolios", "uuid, identifier, amount");
            migrateTable("portfolios_log", "uuid, day, identifier, amount, contribution");
            migrateTable("portfolios_worth", "uuid, day, worth");
            migrateTable("capacities", "uuid, capacity");
            migrateTable("discord_links", "userid, uuid, nickname");
            migrateTable("trade_log", "uuid, day, date, identifier, amount, value, buy, discord");
            migrateTable("cpi", "day, date, value");
            migrateTable("alerts", "day, userid, identifier, price");
            migrateTable("flows", "day, flow, taxes, operations");
            migrateTable("limit_orders", "expiration, uuid, identifier, type, price, to_complete, completed, cost");
            migrateTable("loans", "uuid, debt");
            migrateTable("interests", "uuid, paid");
            migrateTable("user_names", "uuid, name");

            Nascraft.getInstance().getLogger().info("Database migration completed successfully!");

        } catch (Exception e) {
            Nascraft.getInstance().getLogger().log(Level.SEVERE, "Error during database migration: " + e.getMessage(), e);
        }
    }

    /**
     * Migrate a single table from SQLite to MySQL
     */
    private void migrateTable(String tableName, String columns) {
        Nascraft.getInstance().getLogger().info("Migrating table: " + tableName);

        try (Connection sqliteConn = sqliteDatabase.getConnection();
             Connection mysqlConn = mysqlDatabase.getConnection()) {

            // Get data from SQLite
            String selectSql = "SELECT " + columns + " FROM " + tableName;
            PreparedStatement selectStmt = sqliteConn.prepareStatement(selectSql);
            ResultSet rs = selectStmt.executeQuery();

            // Prepare MySQL statement
            String[] columnArray = columns.split(", ");
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < columnArray.length; i++) {
                if (i > 0) placeholders.append(", ");
                placeholders.append("?");
            }

            String insertSql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
            PreparedStatement insertStmt = mysqlConn.prepareStatement(insertSql);

            // Set auto-commit to false for batch operations
            mysqlConn.setAutoCommit(false);

            int batchSize = 0;
            int totalRows = 0;

            // Process each row
            while (rs.next()) {
                for (int i = 1; i <= columnArray.length; i++) {
                    insertStmt.setObject(i, rs.getObject(i));
                }
                insertStmt.addBatch();
                batchSize++;
                totalRows++;

                // Execute batch every 1000 rows
                if (batchSize >= 1000) {
                    insertStmt.executeBatch();
                    mysqlConn.commit();
                    batchSize = 0;
                }
            }

            // Execute remaining batch
            if (batchSize > 0) {
                insertStmt.executeBatch();
                mysqlConn.commit();
            }

            Nascraft.getInstance().getLogger().info("Migrated " + totalRows + " rows from table: " + tableName);

        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().log(Level.SEVERE, "Error migrating table " + tableName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Command to run the migration
     */
    public static void runMigration() {
        // Create temporary SQLite and MySQL instances
        SQLite sqliteDb = SQLite.getInstance();

        // Get MySQL connection details from config
        String host = Nascraft.getInstance().getConfig().getString("database.mysql.host", "localhost");
        int port = Nascraft.getInstance().getConfig().getInt("database.mysql.port", 3306);
        String database = Nascraft.getInstance().getConfig().getString("database.mysql.database", "nascraft");
        String username = Nascraft.getInstance().getConfig().getString("database.mysql.username", "root");
        String password = Nascraft.getInstance().getConfig().getString("database.mysql.password", "password");

        MySQL mysqlDb = new MySQL(host, port, database, username, password);

        // Connect to both databases
        sqliteDb.connect();
        mysqlDb.connect();

        // Run migration
        DatabaseMigration migration = new DatabaseMigration(sqliteDb, mysqlDb);
        migration.migrateAll();

        // Disconnect
        sqliteDb.disconnect();
        mysqlDb.disconnect();

        // Update config to use MySQL
        Nascraft.getInstance().getConfig().set("database.type", "MYSQL");
        Nascraft.getInstance().saveConfig();

        Nascraft.getInstance().getLogger().info("Migration complete. Plugin will now use MySQL database.");
    }
}