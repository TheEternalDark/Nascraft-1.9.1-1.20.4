package me.bounser.nascraft.database.mysql;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.formatter.RoundUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MySQL-specific implementation for TradesLog operations
 */
public class MySQLTradesLog {

    // Prefixo para todas as tabelas
    private static final String TABLE_PREFIX = "nascraft_";

    /**
     * Optimized purge history method for MySQL
     * Uses more efficient date comparison and indexing
     */
    public static void purgeHistory(Connection connection, int daysToKeep) {
        if (daysToKeep == -1) return;

        try {
            // Use MySQL's efficient DELETE with indexed column and table prefix
            String sql = "DELETE FROM " + TABLE_PREFIX + "trade_log WHERE day < ?";
            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setInt(1, daysToKeep);

            int rowsDeleted = prep.executeUpdate();
            Nascraft.getInstance().getLogger().info("Purged " + rowsDeleted + " old trade records from database");

        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error purging trade history: " + e.getMessage());
        }
    }

    /**
     * Batch insert for multiple trades (performance optimization)
     */
    public static void saveTradesBatch(Connection connection, List<Trade> trades) {
        if (trades == null || trades.isEmpty()) return;

        try {
            connection.setAutoCommit(false);
            String sql = "INSERT INTO " + TABLE_PREFIX + "trade_log (uuid, day, date, identifier, amount, value, buy, discord) VALUES (?,?,?,?,?,?,?,?)";
            PreparedStatement stmt = connection.prepareStatement(sql);

            for (Trade trade : trades) {
                stmt.setString(1, trade.getUuid().toString());
                stmt.setInt(2, NormalisedDate.getDays());
                stmt.setString(3, trade.getDate().toString());
                stmt.setString(4, trade.getItem().getIdentifier());
                stmt.setInt(5, trade.getAmount());
                stmt.setDouble(6, RoundUtils.round(trade.getValue()));
                stmt.setBoolean(7, trade.isBuy());
                stmt.setBoolean(8, trade.throughDiscord());
                stmt.addBatch();
            }

            stmt.executeBatch();
            connection.commit();

        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                Nascraft.getInstance().getLogger().severe("Failed to rollback transaction: " + ex.getMessage());
            }
            Nascraft.getInstance().getLogger().severe("Error saving trades batch: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                Nascraft.getInstance().getLogger().severe("Failed to reset auto-commit: " + e.getMessage());
            }
        }
    }

    /**
     * Get total number of trades for a specific item
     */
    public static int getTradeCount(Connection connection, String identifier) {
        try {
            String sql = "SELECT COUNT(*) FROM " + TABLE_PREFIX + "trade_log WHERE identifier = ?";
            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setString(1, identifier);

            var rs = prep.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error getting trade count: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Get total volume traded for a specific item
     */
    public static double getTotalVolume(Connection connection, String identifier) {
        try {
            String sql = "SELECT SUM(value) FROM " + TABLE_PREFIX + "trade_log WHERE identifier = ?";
            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setString(1, identifier);

            var rs = prep.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error getting total volume: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Get trade statistics for a specific day
     */
    public static TradeStatistics getDayStatistics(Connection connection, int day) {
        try {
            String sql = "SELECT COUNT(*) as trade_count, SUM(value) as total_value, " +
                    "SUM(CASE WHEN buy = true THEN 1 ELSE 0 END) as buy_count, " +
                    "SUM(CASE WHEN buy = false THEN 1 ELSE 0 END) as sell_count " +
                    "FROM " + TABLE_PREFIX + "trade_log WHERE day = ?";

            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setInt(1, day);

            var rs = prep.executeQuery();
            if (rs.next()) {
                return new TradeStatistics(
                        rs.getInt("trade_count"),
                        rs.getDouble("total_value"),
                        rs.getInt("buy_count"),
                        rs.getInt("sell_count")
                );
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error getting day statistics: " + e.getMessage());
        }
        return new TradeStatistics(0, 0.0, 0, 0);
    }

    /**
     * Inner class to represent trade statistics
     */
    public static class TradeStatistics {
        private final int totalTrades;
        private final double totalValue;
        private final int buyCount;
        private final int sellCount;

        public TradeStatistics(int totalTrades, double totalValue, int buyCount, int sellCount) {
            this.totalTrades = totalTrades;
            this.totalValue = totalValue;
            this.buyCount = buyCount;
            this.sellCount = sellCount;
        }

        public int getTotalTrades() { return totalTrades; }
        public double getTotalValue() { return totalValue; }
        public int getBuyCount() { return buyCount; }
        public int getSellCount() { return sellCount; }
        public double getAverageValue() {
            return totalTrades > 0 ? totalValue / totalTrades : 0.0;
        }
    }
}