package me.bounser.nascraft.database.mysql;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.chart.cpi.CPIInstant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL-specific implementation for Statistics operations
 */
public class MySQLStatistics {

    // Prefixo para todas as tabelas
    private static final String TABLE_PREFIX = "nascraft_";

    /**
     * MySQL-compatible implementation of addTransaction
     * Uses INSERT ... ON DUPLICATE KEY UPDATE instead of REPLACE
     */
    public static void addTransaction(Connection connection, double newFlow, double effectiveTaxes) {
        try {
            int today = NormalisedDate.getDays();

            // Check if record exists
            String query = "SELECT flow, operations, taxes FROM " + TABLE_PREFIX + "flows WHERE day = ?";
            PreparedStatement prep = connection.prepareStatement(query);
            prep.setInt(1, today);
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                // Update existing record
                double flow = rs.getDouble("flow") + newFlow;
                double taxes = rs.getDouble("taxes") + Math.abs(effectiveTaxes);
                int operations = rs.getInt("operations") + 1;

                String updateSql = "UPDATE " + TABLE_PREFIX + "flows SET flow = ?, taxes = ?, operations = ? WHERE day = ?";
                PreparedStatement updateStmt = connection.prepareStatement(updateSql);
                updateStmt.setDouble(1, flow);
                updateStmt.setDouble(2, taxes);
                updateStmt.setInt(3, operations);
                updateStmt.setInt(4, today);
                updateStmt.executeUpdate();
            } else {
                // Insert new record
                String insertSql = "INSERT INTO " + TABLE_PREFIX + "flows (day, flow, taxes, operations) VALUES (?, ?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertSql);
                insertStmt.setInt(1, today);
                insertStmt.setDouble(2, newFlow);
                insertStmt.setDouble(3, Math.abs(effectiveTaxes));
                insertStmt.setInt(4, 1);
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error adding transaction: " + e.getMessage());
        }
    }

    /**
     * Optimized CPI query for MySQL
     */
    public static List<CPIInstant> getAllCPI(Connection connection) {
        List<CPIInstant> cpiInstants = new ArrayList<>();

        try {
            // Use ORDER BY for consistent results with table prefix
            String sql = "SELECT value, date FROM " + TABLE_PREFIX + "cpi ORDER BY day ASC";
            PreparedStatement prep = connection.prepareStatement(sql);
            ResultSet rs = prep.executeQuery();

            while (rs.next()) {
                cpiInstants.add(new CPIInstant(
                        rs.getFloat("value"),
                        LocalDateTime.parse(rs.getString("date"))
                ));
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error retrieving CPI data: " + e.getMessage());
        }

        return cpiInstants;
    }

    /**
     * Save CPI value for a specific day
     */
    public static void saveCPIValue(Connection connection, float indexValue) {
        try {
            int today = NormalisedDate.getDays();
            String sql = "INSERT INTO " + TABLE_PREFIX + "cpi (day, date, value) VALUES (?,?,?) " +
                    "ON DUPLICATE KEY UPDATE date=VALUES(date), value=VALUES(value)";

            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setInt(1, today);
            prep.setString(2, LocalDateTime.now().toString());
            prep.setFloat(3, indexValue);
            prep.executeUpdate();

        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error saving CPI value: " + e.getMessage());
        }
    }

    /**
     * Get total taxes collected across all days
     */
    public static double getAllTaxesCollected(Connection connection) {
        try {
            String sql = "SELECT SUM(taxes) as total_taxes FROM " + TABLE_PREFIX + "flows";
            PreparedStatement prep = connection.prepareStatement(sql);
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                return rs.getDouble("total_taxes");
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error getting total taxes: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Get flow statistics for a specific day
     */
    public static FlowStatistics getDayFlowStatistics(Connection connection, int day) {
        try {
            String sql = "SELECT flow, taxes, operations FROM " + TABLE_PREFIX + "flows WHERE day = ?";
            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setInt(1, day);
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                return new FlowStatistics(
                        rs.getDouble("flow"),
                        rs.getDouble("taxes"),
                        rs.getInt("operations")
                );
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error getting day flow statistics: " + e.getMessage());
        }
        return new FlowStatistics(0.0, 0.0, 0);
    }

    /**
     * Get flow statistics for a date range
     */
    public static FlowStatistics getFlowStatisticsRange(Connection connection, int startDay, int endDay) {
        try {
            String sql = "SELECT SUM(flow) as total_flow, SUM(taxes) as total_taxes, SUM(operations) as total_operations " +
                    "FROM " + TABLE_PREFIX + "flows WHERE day BETWEEN ? AND ?";
            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setInt(1, startDay);
            prep.setInt(2, endDay);
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                return new FlowStatistics(
                        rs.getDouble("total_flow"),
                        rs.getDouble("total_taxes"),
                        rs.getInt("total_operations")
                );
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error getting flow statistics range: " + e.getMessage());
        }
        return new FlowStatistics(0.0, 0.0, 0);
    }

    /**
     * Get the latest CPI value
     */
    public static float getLatestCPI(Connection connection) {
        try {
            String sql = "SELECT value FROM " + TABLE_PREFIX + "cpi ORDER BY day DESC LIMIT 1";
            PreparedStatement prep = connection.prepareStatement(sql);
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                return rs.getFloat("value");
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Error getting latest CPI: " + e.getMessage());
        }
        return 1.0f; // Default CPI value
    }

    /**
     * Inner class to represent flow statistics
     */
    public static class FlowStatistics {
        private final double totalFlow;
        private final double totalTaxes;
        private final int totalOperations;

        public FlowStatistics(double totalFlow, double totalTaxes, int totalOperations) {
            this.totalFlow = totalFlow;
            this.totalTaxes = totalTaxes;
            this.totalOperations = totalOperations;
        }

        public double getTotalFlow() { return totalFlow; }
        public double getTotalTaxes() { return totalTaxes; }
        public int getTotalOperations() { return totalOperations; }
        public double getAverageTransactionValue() {
            return totalOperations > 0 ? totalFlow / totalOperations : 0.0;
        }
        public double getTaxRate() {
            return totalFlow != 0 ? (totalTaxes / Math.abs(totalFlow)) * 100 : 0.0;
        }
    }
}