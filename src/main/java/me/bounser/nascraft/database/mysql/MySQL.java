package me.bounser.nascraft.database.mysql;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.cpi.CPIInstant;
import me.bounser.nascraft.database.Database;
import me.bounser.nascraft.database.commands.*;
import me.bounser.nascraft.database.commands.resources.DayInfo;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.portfolio.Portfolio;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class MySQL implements Database {

    private Connection connection;
    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;
    private final String url;

    public MySQL(String host, String port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
    }

    @Override
    public void connect() {
        try {
            Nascraft.getInstance().getLogger().info("Tentando conectar ao MySQL...");
            Nascraft.getInstance().getLogger().info("Host: " + host + ", Porta: " + port + ", Banco de dados: " + database);

            connection = DriverManager.getConnection(url, username, password);

            if (connection != null && !connection.isClosed()) {
                Nascraft.getInstance().getLogger().info("Conexão com MySQL estabelecida com sucesso!");
                createTables();
            } else {
                Nascraft.getInstance().getLogger().severe("Falha ao conectar ao MySQL: Conexão retornou nula ou fechada");
            }
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().severe("Erro ao conectar ao MySQL: " + e.getMessage());
            Nascraft.getInstance().getLogger().severe("Código do erro SQL: " + e.getErrorCode());

            if (e.getMessage().contains("Communications link failure")) {
                Nascraft.getInstance().getLogger().severe("Não foi possível estabelecer conexão com o servidor MySQL. Verifique se o servidor está rodando e acessível.");
            } else if (e.getMessage().contains("Access denied")) {
                Nascraft.getInstance().getLogger().severe("Acesso negado. Verifique o nome de usuário e senha.");
            } else if (e.getMessage().contains("Unknown database")) {
                Nascraft.getInstance().getLogger().severe("Banco de dados '" + database + "' não existe. Crie o banco de dados antes de conectar.");
            }
        }
    }

    @Override
    public void disconnect() {
        saveEverything();
        if (connection != null) {
            try {
                connection.close();
                Nascraft.getInstance().getLogger().info("Conexão com MySQL fechada com sucesso.");
            } catch (SQLException e) {
                Nascraft.getInstance().getLogger().warning("Erro ao fechar conexão MySQL: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Erro ao verificar estado da conexão MySQL: " + e.getMessage());
            return false;
        }
    }

    private void createTable(Connection connection, String tableName, String columns) {
        try {
            Statement statement = connection.createStatement();
            statement.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (" + columns + ");");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createTables() {
        createTable(connection, "items",
                "identifier VARCHAR(255) PRIMARY KEY, " +
                        "lastprice DOUBLE, " +
                        "lowest DOUBLE, " +
                        "highest DOUBLE, " +
                        "stock DOUBLE DEFAULT 0, " +
                        "taxes DOUBLE");

        createTable(connection, "prices_day",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "day INT, " +
                        "date VARCHAR(255)," +
                        "identifier VARCHAR(255)," +
                        "price DOUBLE," +
                        "volume INT");

        createTable(connection, "prices_month",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "day INT NOT NULL, " +
                        "date VARCHAR(255) NOT NULL," +
                        "identifier VARCHAR(255) NOT NULL," +
                        "price DOUBLE NOT NULL," +
                        "volume INT NOT NULL");

        createTable(connection, "prices_history",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "day INT," +
                        "date VARCHAR(255) NOT NULL," +
                        "identifier INT," +
                        "price DOUBLE," +
                        "volume INT");

        createTable(connection, "portfolios",
                "uuid VARCHAR(36) NOT NULL," +
                        "identifier VARCHAR(255)," +
                        "amount INT, " +
                        "PRIMARY KEY (uuid, identifier)");

        createTable(connection, "portfolios_log",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL," +
                        "day INT," +
                        "identifier VARCHAR(255)," +
                        "amount INT," +
                        "contribution DOUBLE");

        createTable(connection, "portfolios_worth",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL," +
                        "day INT," +
                        "worth DOUBLE");

        createTable(connection, "capacities",
                "uuid VARCHAR(36) PRIMARY KEY," +
                        "capacity INT");

        createTable(connection, "discord_links",
                "userid VARCHAR(18) NOT NULL," +
                        "uuid VARCHAR(36) NOT NULL," +
                        "nickname VARCHAR(255) NOT NULL, " +
                        "PRIMARY KEY (userid)");

        createTable(connection, "trade_log",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL," +
                        "day INT NOT NULL," +
                        "date VARCHAR(255) NOT NULL," +
                        "identifier VARCHAR(255) NOT NULL," +
                        "amount INT NOT NULL," +
                        "value VARCHAR(255) NOT NULL," +
                        "buy TINYINT(1) NOT NULL, " +
                        "discord TINYINT(1) NOT NULL");

        createTable(connection, "cpi",
                "day INT NOT NULL," +
                        "date VARCHAR(255) NOT NULL," +
                        "value DOUBLE NOT NULL, " +
                        "PRIMARY KEY (day)");

        createTable(connection, "alerts",
                "day INT NOT NULL," +
                        "userid VARCHAR(255) NOT NULL," +
                        "identifier VARCHAR(255) NOT NULL," +
                        "price DOUBLE NOT NULL, " +
                        "PRIMARY KEY (userid, identifier)");

        createTable(connection, "flows",
                "day INT PRIMARY KEY," +
                        "flow DOUBLE NOT NULL," +
                        "taxes DOUBLE NOT NULL," +
                        "operations INT NOT NULL");

        createTable(connection, "limit_orders",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "expiration VARCHAR(255) NOT NULL," +
                        "uuid VARCHAR(36) NOT NULL," +
                        "identifier VARCHAR(255) NOT NULL," +
                        "type INT NOT NULL," +
                        "price DOUBLE NOT NULL," +
                        "to_complete INT NOT NULL," +
                        "completed INT NOT NULL," +
                        "cost INT NOT NULL");

        createTable(connection, "loans",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL," +
                        "debt DOUBLE NOT NULL");

        createTable(connection, "interests",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL," +
                        "paid DOUBLE NOT NULL");

        createTable(connection, "user_names",
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL," +
                        "name VARCHAR(255) NOT NULL");
    }

    @Override
    public void saveEverything() {
        for (Item item : MarketManager.getInstance().getAllParentItems()) {
            try {
                if (isConnected()) {
                    ItemProperties.saveItem(connection, item);
                } else {
                    connect();
                    if (isConnected()) {
                        ItemProperties.saveItem(connection, item);
                    }
                }
            } catch (Exception e) {
                Nascraft.getInstance().getLogger().warning(e.getMessage());
            }
        }
    }

    @Override
    public void saveLink(String userId, UUID uuid, String nickname) {
        try {
            if (!isConnected()) connect();
            DiscordLink.saveLink(connection, userId, uuid, nickname);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void removeLink(String userId) {
        try {
            if (!isConnected()) connect();
            DiscordLink.removeLink(connection, userId);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public UUID getUUID(String userId) {
        try {
            if (!isConnected()) connect();
            return DiscordLink.getUUID(connection, userId);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return null;
        }
    }

    @Override
    public String getNickname(String userId) {
        try {
            if (!isConnected()) connect();
            return DiscordLink.getNickname(connection, userId);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return null;
        }
    }

    @Override
    public String getUserId(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return DiscordLink.getUserId(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return null;
        }
    }

    @Override
    public void saveDayPrice(Item item, Instant instant) {
        try {
            if (!isConnected()) connect();
            HistorialData.saveDayPrice(connection, item, instant);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void saveMonthPrice(Item item, Instant instant) {
        try {
            if (!isConnected()) connect();
            HistorialData.saveMonthPrice(connection, item, instant);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void saveHistoryPrices(Item item, Instant instant) {
        try {
            if (!isConnected()) connect();
            HistorialData.saveHistoryPrices(connection, item, instant);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public List<Instant> getDayPrices(Item item) {
        try {
            if (!isConnected()) connect();
            return HistorialData.getDayPrices(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Instant> getMonthPrices(Item item) {
        try {
            if (!isConnected()) connect();
            return HistorialData.getMonthPrices(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Instant> getYearPrices(Item item) {
        try {
            if (!isConnected()) connect();
            return HistorialData.getYearPrices(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Instant> getAllPrices(Item item) {
        try {
            if (!isConnected()) connect();
            return HistorialData.getAllPrices(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Double getPriceOfDay(String identifier, int day) {
        try {
            if (!isConnected()) connect();
            return HistorialData.getPriceOfDay(connection, identifier, day);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return 0.0;
        }
    }

    @Override
    public void saveItem(Item item) {
        try {
            if (!isConnected()) connect();
            ItemProperties.saveItem(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void retrieveItem(Item item) {
        try {
            if (!isConnected()) connect();
            ItemProperties.retrieveItem(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void retrieveItems() {
        try {
            if (!isConnected()) connect();
            ItemProperties.retrieveItems(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public float retrieveLastPrice(Item item) {
        try {
            if (!isConnected()) connect();
            return ItemProperties.retrieveLastPrice(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return 0;
        }
    }

    @Override
    public void saveTrade(Trade trade) {
        try {
            if (!isConnected()) connect();
            TradesLog.saveTrade(connection, trade);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public List<Trade> retrieveTrades(UUID uuid, int offset, int limit) {
        try {
            if (!isConnected()) connect();
            return TradesLog.retrieveTrades(connection, uuid, offset, limit);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Trade> retrieveTrades(UUID uuid, Item item, int offset, int limit) {
        try {
            if (!isConnected()) connect();
            return TradesLog.retrieveTrades(connection, uuid, item, offset, limit);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Trade> retrieveTrades(Item item, int offset, int limit) {
        try {
            if (!isConnected()) connect();
            return TradesLog.retrieveTrades(connection, item, offset, limit);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Trade> retrieveTrades(int offset, int limit) {
        try {
            if (!isConnected()) connect();
            return TradesLog.retrieveLastTrades(connection, offset, limit);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void purgeHistory() {
        try {
            if (!isConnected()) connect();
            TradesLog.purgeHistory(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void updateItemPortfolio(UUID uuid, Item item, int quantity) {
        try {
            if (!isConnected()) connect();
            Portfolios.updateItemPortfolio(connection, uuid, item, quantity);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void removeItemPortfolio(UUID uuid, Item item) {
        try {
            if (!isConnected()) connect();
            Portfolios.removeItemPortfolio(connection, uuid, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void clearPortfolio(UUID uuid) {
        try {
            if (!isConnected()) connect();
            Portfolios.clearPortfolio(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void updateCapacity(UUID uuid, int capacity) {
        try {
            if (!isConnected()) connect();
            Portfolios.updateCapacity(connection, uuid, capacity);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public LinkedHashMap<Item, Integer> retrievePortfolio(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return Portfolios.retrievePortfolio(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    @Override
    public int retrieveCapacity(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return Portfolios.retrieveCapacity(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return 0;
        }
    }

    @Override
    public void logContribution(UUID uuid, Item item, int amount) {
        try {
            if (!isConnected()) connect();
            PortfoliosLog.logContribution(connection, uuid, item, amount);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void logWithdraw(UUID uuid, Item item, int amount) {
        try {
            if (!isConnected()) connect();
            PortfoliosLog.logWithdraw(connection, uuid, item, amount);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public HashMap<Integer, Double> getContributionChangeEachDay(UUID uuid) {
        try {
            if (!isConnected()) connect();
            HashMap<Integer, Double> result = PortfoliosLog.getContributionChangeEachDay(connection, uuid);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public HashMap<Integer, HashMap<String, Integer>> getCompositionEachDay(UUID uuid) {
        try {
            if (!isConnected()) connect();
            HashMap<Integer, HashMap<String, Integer>> result = PortfoliosLog.getCompositionEachDay(connection, uuid);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public int getFirstDay(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return PortfoliosLog.getFirstDay(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return NormalisedDate.getDays();
        }
    }

    @Override
    public void increaseDebt(UUID uuid, Double debt) {
        try {
            if (!isConnected()) connect();
            Debt.increaseDebt(connection, uuid, debt);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void decreaseDebt(UUID uuid, Double debt) {
        try {
            if (!isConnected()) connect();
            Debt.decreaseDebt(connection, uuid, debt);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public double getDebt(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return Debt.getDebt(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
        return 0;
    }

    @Override
    public HashMap<UUID, Double> getUUIDAndDebt() {
        try {
            if (!isConnected()) connect();
            HashMap<UUID, Double> result = Debt.getUUIDAndDebt(connection);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public void addInterestPaid(UUID uuid, Double interest) {
        try {
            if (!isConnected()) connect();
            Debt.addInterestPaid(connection, uuid, interest);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public HashMap<UUID, Double> getUUIDAndInterestsPaid() {
        try {
            if (!isConnected()) connect();
            HashMap<UUID, Double> result = Debt.getUUIDAndInterestsPaid(connection);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public double getInterestsPaid(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return Debt.getInterestsPaid(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
        return 0;
    }

    @Override
    public double getAllOutstandingDebt() {
        try {
            if (!isConnected()) connect();
            return Debt.getAllOutstandingDebt(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
        return 0;
    }

    @Override
    public double getAllInterestsPaid() {
        try {
            if (!isConnected()) connect();
            return Debt.getAllInterestsPaid(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
        return 0;
    }

    @Override
    public void saveOrUpdateWorth(UUID uuid, int day, double worth) {
        try {
            if (!isConnected()) connect();
            PortfoliosWorth.saveOrUpdateWorth(connection, uuid, day, worth);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void saveOrUpdateWorthToday(UUID uuid, double worth) {
        try {
            if (!isConnected()) connect();
            PortfoliosWorth.saveOrUpdateWorthToday(connection, uuid, worth);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public HashMap<UUID, Portfolio> getTopWorth(int n) {
        try {
            if (!isConnected()) connect();
            HashMap<UUID, Portfolio> result = PortfoliosWorth.getTopWorth(connection, n);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public double getLatestWorth(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return PortfoliosWorth.getLatestWorth(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
        return 0;
    }

    @Override
    public void saveCPIValue(float indexValue) {
        try {
            if (!isConnected()) connect();
            Statistics.saveCPI(connection, indexValue);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public List<CPIInstant> getCPIHistory() {
        try {
            if (!isConnected()) connect();
            return Statistics.getAllCPI(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Instant> getPriceAgainstCPI(Item item) {
        try {
            if (!isConnected()) connect();
            return Statistics.getPriceAgainstCPI(connection, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void addTransaction(double newFlow, double effectiveTaxes) {
        try {
            if (!isConnected()) connect();
            Statistics.addTransaction(connection, newFlow, effectiveTaxes);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Error while trying to log a transaction");
        }
    }

    @Override
    public List<DayInfo> getDayInfos() {
        try {
            if (!isConnected()) connect();
            return Statistics.getDayInfos(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public double getAllTaxesCollected() {
        try {
            if (!isConnected()) connect();
            return Statistics.getAllTaxesCollected(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
        return 0;
    }

    @Override
    public void addAlert(String userid, Item item, double price) {
        try {
            if (!isConnected()) connect();
            Alerts.addAlert(connection, userid, item, price);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void removeAlert(String userid, Item item) {
        try {
            if (!isConnected()) connect();
            Alerts.removeAlert(connection, userid, item);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void retrieveAlerts() {
        try {
            if (!isConnected()) connect();
            Alerts.retrieveAlerts(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void removeAllAlerts(String userid) {
        try {
            if (!isConnected()) connect();
            Alerts.removeAllAlerts(connection, userid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void purgeAlerts() {
        try {
            if (!isConnected()) connect();
            Alerts.purgeAlerts(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void addLimitOrder(UUID uuid, LocalDateTime expiration, Item item, int type, double price, int amount) {
        try {
            if (!isConnected()) connect();
            LimitOrders.addLimitOrder(connection, uuid, expiration, item, type, price, amount);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void updateLimitOrder(UUID uuid, Item item, int completed, double cost) {
        try {
            if (!isConnected()) connect();
            LimitOrders.updateLimitOrder(connection, uuid, item, completed, cost);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void removeLimitOrder(String uuid, String identifier) {
        try {
            if (!isConnected()) connect();
            LimitOrders.removeLimitOrder(connection, uuid, identifier);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void retrieveLimitOrders() {
        try {
            if (!isConnected()) connect();
            LimitOrders.retrieveLimitOrders(connection);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }

    @Override
    public String getNameByUUID(UUID uuid) {
        try {
            if (!isConnected()) connect();
            return UserNames.getNameByUUID(connection, uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
        return " ";
    }

    @Override
    public void saveOrUpdateName(UUID uuid, String name) {
        try {
            if (!isConnected()) connect();
            UserNames.saveOrUpdateNick(connection, uuid, name);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning(e.getMessage());
        }
    }
}