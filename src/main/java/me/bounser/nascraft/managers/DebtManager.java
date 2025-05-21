package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.portfolio.Portfolio;
import me.bounser.nascraft.portfolio.PortfoliosManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DebtManager {

    private static DebtManager instance;

    public static DebtManager getInstance() { return instance == null ? instance = new DebtManager() : instance; }

    private DebtManager() {
        try {
            interestCollector();
            checkMargins();
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Error initializing DebtManager: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void checkMargins() {
        try {
            Bukkit.getScheduler().runTaskTimer(Nascraft.getInstance(),
                    () -> {
                        try {
                            HashMap<UUID, Double> debtors = DatabaseManager.get().getDatabase().getUUIDAndDebt();

                            // Fix: Check if debtors is null before using it
                            if (debtors == null) {
                                Nascraft.getInstance().getLogger().warning("getUUIDAndDebt() returned null in checkMargins()");
                                return; // Skip this execution if debtors is null
                            }

                            for (UUID debtorUUID : debtors.keySet()) {
                                try {
                                    Player player = Bukkit.getPlayer(debtorUUID);
                                    double maxDebt = getMaximumLoan(debtorUUID);

                                    if (player == null) {
                                        if (debtors.get(debtorUUID) >= maxDebt)
                                            forceMarginCall(debtorUUID);
                                    } else {
                                        if (debtors.get(debtorUUID) >= maxDebt) {
                                            forceMarginCall(debtorUUID);
                                        } else if (debtors.get(debtorUUID) >= maxDebt * 0.95) {
                                            Lang.get().message(player, Lang.get().message(Message.PORTFOLIO_DEBT_ALERT));
                                        }
                                    }
                                } catch (Exception e) {
                                    Nascraft.getInstance().getLogger().warning("Error processing debtor " + debtorUUID + ": " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            Nascraft.getInstance().getLogger().severe("Error in checkMargins task: " + e.getMessage());
                        }
                    }, 30*20, 20L * Config.getInstance().getMarginCheckingPeriod());
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule checkMargins task: " + e.getMessage());
        }
    }

    public void interestCollector() {
        try {
            Bukkit.getScheduler().runTaskTimer(Nascraft.getInstance(),
                    () -> {
                        try {
                            HashMap<UUID, Double> debtors = DatabaseManager.get().getDatabase().getUUIDAndDebt();

                            // Fix: Check if debtors is null before using it
                            if (debtors == null) {
                                Nascraft.getInstance().getLogger().warning("getUUIDAndDebt() returned null in interestCollector()");
                                return; // Skip this execution if debtors is null
                            }

                            for (UUID debtorUUID : debtors.keySet()) {
                                try {
                                    double interest = Math.max(debtors.get(debtorUUID) * Config.getInstance().getLoansDailyInterest(), Config.getInstance().getLoansMinimumInterest());
                                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(debtorUUID);
                                    Currency currency = CurrenciesManager.getInstance().getDefaultCurrency();

                                    if (MoneyManager.getInstance().hasEnoughMoney(offlinePlayer, currency, interest)) {
                                        MoneyManager.getInstance().simpleWithdraw(offlinePlayer, CurrenciesManager.getInstance().getDefaultCurrency(), interest);
                                        DatabaseManager.get().getDatabase().addInterestPaid(debtorUUID, interest);

                                        Player player = Bukkit.getPlayer(debtorUUID);

                                        if (player != null)
                                            Lang.get().message(player, Lang.get().message(Message.PORTFOLIO_DEBT_INTEREST_PAYED)
                                                    .replace("[AMOUNT]", Formatter.format(CurrenciesManager.getInstance().getDefaultCurrency(), interest, Style.ROUND_BASIC)));

                                    } else {
                                        DatabaseManager.get().getDatabase().increaseDebt(debtorUUID, interest);

                                        Player player = Bukkit.getPlayer(debtorUUID);

                                        if (player != null)
                                            Lang.get().message(player, Lang.get().message(Message.PORTFOLIO_DEBT_INTEREST_ACC)
                                                    .replace("[AMOUNT]", Formatter.format(CurrenciesManager.getInstance().getDefaultCurrency(), interest, Style.ROUND_BASIC)));
                                    }
                                } catch (Exception e) {
                                    Nascraft.getInstance().getLogger().warning("Error processing interest for debtor " + debtorUUID + ": " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            Nascraft.getInstance().getLogger().severe("Error in interestCollector task: " + e.getMessage());
                        }
                    }, calculateInitialDelay(LocalTime.now(), Config.getInstance().getInterestPaymentHour())*20, TimeUnit.DAYS.toSeconds(1)*20);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule interestCollector task: " + e.getMessage());
        }
    }

    private static long calculateInitialDelay(LocalTime currentTime, LocalTime targetTime) {
        if (currentTime.isBefore(targetTime)) {
            return Duration.between(currentTime, targetTime).toSeconds();
        } else {
            LocalTime midnight = LocalTime.MIDNIGHT;
            long timeUntilMidnight = Duration.between(currentTime, midnight).toSeconds();
            long timeFromMidnightToTarget = Duration.between(midnight, targetTime).toSeconds();
            return timeUntilMidnight + timeFromMidnightToTarget;
        }
    }

    public double getDebtOfPlayer(UUID uuid) {
        try {
            return DatabaseManager.get().getDatabase().getDebt(uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Error getting debt for player " + uuid + ": " + e.getMessage());
            return 0.0; // Return 0 as a safe default
        }
    }

    public void decreaseDebt(UUID uuid, double debt) {
        try {
            DatabaseManager.get().getDatabase().decreaseDebt(uuid, debt);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Error decreasing debt for player " + uuid + ": " + e.getMessage());
        }
    }

    public void increaseDebt(UUID uuid, double debt) {
        try {
            DatabaseManager.get().getDatabase().increaseDebt(uuid, debt);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Error increasing debt for player " + uuid + ": " + e.getMessage());
        }
    }

    public double getLifeTimeInterests(UUID uuid) {
        try {
            return DatabaseManager.get().getDatabase().getInterestsPaid(uuid);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Error getting lifetime interests for player " + uuid + ": " + e.getMessage());
            return 0.0; // Return 0 as a safe default
        }
    }

    public double getMaximumLoan(UUID uuid) {
        try {
            /*
            From a portfolio of 10 * 10$ = 100$:

            -> For liquidation costs:
                - Taxes: From selling 50$ * 6% = 3$
                - Price dilution: Selling 50$ (5) has an impact on subsequent sells. For example 2$
            -> Security margin: 5% * 50$ = 2.5$

            Total available loan: 100$ * 50% - 3$ - 2$ - 2.5$ = 42.5$
             */

            Portfolio portfolio = PortfoliosManager.getInstance().getPortfolio(uuid);

            // Check if portfolio is null
            if (portfolio == null) {
                Nascraft.getInstance().getLogger().warning("Portfolio is null for player " + uuid);
                return 0.0;
            }

            // Check if portfolio content is null
            if (portfolio.getContent() == null) {
                Nascraft.getInstance().getLogger().warning("Portfolio content is null for player " + uuid);
                return 0.0;
            }

            double maxLoan = 0;

            for (Item item : portfolio.getContent().keySet()) {
                if (item != null) {
                    Integer quantity = portfolio.getContent().get(item);
                    if (quantity != null) {
                        maxLoan += item.sellPrice(quantity) * (1-Config.getInstance().getLoanSecurityMargin());
                    }
                }
            }

            return maxLoan;
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().warning("Error calculating maximum loan for player " + uuid + ": " + e.getMessage());
            return 0.0; // Return 0 as a safe default
        }
    }

    public void forceMarginCall(UUID uuid) {
        try {
            double debt = getDebtOfPlayer(uuid);
            double maxLoan = getMaximumLoan(uuid);
            Currency currency = CurrenciesManager.getInstance().getDefaultCurrency();
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            double toPay = debt - maxLoan;

            if (Config.getInstance().getLoansMaxSize() < debt)
                toPay = Math.max(toPay, debt - Config.getInstance().getLoansMaxSize());

            if (MoneyManager.getInstance().hasEnoughMoney(player, currency, toPay)) {
                MoneyManager.getInstance().simpleWithdraw(player, currency, toPay);
                DatabaseManager.get().getDatabase().decreaseDebt(uuid, toPay);

                Player onlinePlayer = Bukkit.getPlayer(uuid);

                if (onlinePlayer != null)
                    Lang.get().message(onlinePlayer, Lang.get().message(Message.PORTFOLIO_DEBT_MONEY)
                            .replace("[AMOUNT]", Formatter.format(CurrenciesManager.getInstance().getDefaultCurrency(), toPay, Style.ROUND_BASIC)));
            } else {
                Portfolio portfolio = PortfoliosManager.getInstance().getPortfolio(uuid);

                if (portfolio == null) {
                    Nascraft.getInstance().getLogger().warning("Cannot force margin call: Portfolio is null for player " + uuid);
                    return;
                }

                portfolio.liquidatePerCurrency(currency, result -> {
                    try {
                        if (debt <= result) {
                            MoneyManager.getInstance().simpleWithdraw(player, currency, debt);
                            DatabaseManager.get().getDatabase().decreaseDebt(uuid, debt);
                        } else {
                            MoneyManager.getInstance().simpleWithdraw(player, currency, result);
                            DatabaseManager.get().getDatabase().decreaseDebt(uuid, result);
                        }

                        Player onlinePlayer = Bukkit.getPlayer(uuid);

                        if (onlinePlayer != null)
                            Lang.get().message(onlinePlayer, Message.PORTFOLIO_DEBT_LIQUIDATED);
                    } catch (Exception e) {
                        Nascraft.getInstance().getLogger().severe("Error in liquidation callback: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Error in forceMarginCall for player " + uuid + ": " + e.getMessage());
        }
    }
}