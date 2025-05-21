package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.advancedgui.LayoutModifier;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.discord.alerts.DiscordAlerts;
import me.bounser.nascraft.discord.DiscordBot;
import me.bounser.nascraft.discord.DiscordLog;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.portfolio.PortfoliosManager;
import me.leoko.advancedgui.manager.GuiWallManager;
import me.leoko.advancedgui.utils.GuiWallInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TasksManager {

    public static TasksManager instance;

    private final int ticksPerSecond = 20;

    private final Plugin AGUI = Bukkit.getPluginManager().getPlugin("AdvancedGUI");

    public static TasksManager getInstance() { return instance == null ? instance = new TasksManager() : instance; }

    private TasksManager(){
        try {
            LocalTime timeNow = LocalTime.now();

            LocalTime nextMinute = timeNow.plusMinutes(1).withSecond(0);
            Duration timeRemaining = Duration.between(timeNow, nextMinute);


            saveDataTask();
            noiseTask((int) timeRemaining.getSeconds());
            discordTask((int) timeRemaining.getSeconds());
            shortTermPricesTask((int) timeRemaining.getSeconds());
            hourlyTask();
            saveInstants();

            DatabaseManager.get().getDatabase().purgeHistory();
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Error initializing TasksManager: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void shortTermPricesTask(int delay) {
        try {
            Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {
                try {
                    float allChanges = 0;
                    for (Item item : MarketManager.getInstance().getAllParentItems()) {
                        if (Config.getInstance().getPriceNoise())
                            allChanges += item.getPrice().getChange();

                        item.lowerOperations();

                        item.getPrice().addValueToShortTermStorage();
                    }

                    MarketManager.getInstance().updateMarketChange1h(allChanges/MarketManager.getInstance().getAllParentItems().size());

                    if (AGUI != null &&
                            AGUI.isEnabled() &&
                            GuiWallManager.getInstance().getActiveInstances() != null)

                        for (GuiWallInstance instance : GuiWallManager.getInstance().getActiveInstances()) {

                            if (instance.getLayout().getName().equals("Nascraft"))
                                for (Player player : Bukkit.getOnlinePlayers())
                                    if (instance.getInteraction(player) != null)
                                        LayoutModifier.getInstance().updateMainPage(instance.getInteraction(player).getComponentTree(), true, player);
                        }
                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe("Error in shortTermPricesTask: " + e.getMessage());
                }
            }, (long) delay * ticksPerSecond, 60L * ticksPerSecond);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule shortTermPricesTask: " + e.getMessage());
        }
    }

    private void discordTask(int delay) {
        try {
            if (Config.getInstance().getDiscordEnabled()) {
                Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {
                    try {
                        if (Config.getInstance().getDiscordMenuEnabled()) {
                            DiscordBot.getInstance().update();
                            DiscordAlerts.getInstance().updateAlerts();
                        }

                        if (Config.getInstance().getLogChannelEnabled())
                            DiscordLog.getInstance().flushBuffer();
                    } catch (Exception e) {
                        Nascraft.getInstance().getLogger().severe("Error in discordTask: " + e.getMessage());
                    }
                }, (long) delay * ticksPerSecond, ((long) Config.getInstance().getUpdateTime() *  ticksPerSecond));
            }
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule discordTask: " + e.getMessage());
        }
    }

    private void noiseTask(int delay) {
        try {
            Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {
                try {
                    for (Item item : MarketManager.getInstance().getAllParentItems()) {
                        if (Config.getInstance().getPriceNoise())
                            item.getPrice().applyNoise();
                    }
                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe("Error in noiseTask: " + e.getMessage());
                }
            }, (long) delay * ticksPerSecond, (long) Config.getInstance().getNoiseTime() *  ticksPerSecond);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule noiseTask: " + e.getMessage());
        }
    }

    private void saveDataTask() {
        try {
            Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {
                try {
                    DatabaseManager.get().getDatabase().saveEverything();
                    DatabaseManager.get().getDatabase().saveCPIValue(MarketManager.getInstance().getConsumerPriceIndex());
                    PortfoliosManager.getInstance().savePortfoliosWorthOfOnlinePlayers();
                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe("Error in saveDataTask: " + e.getMessage());
                }
            }, 60L * 5 * ticksPerSecond, 60L * 5 * ticksPerSecond);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule saveDataTask: " + e.getMessage());
        }
    }

    private void saveInstants() {
        try {
            Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {
                try {
                    for (Item item : MarketManager.getInstance().getAllParentItems()) {
                        item.getItemStats().addInstant(new Instant(
                                LocalDateTime.now(),
                                item.getPrice().getValue(),
                                item.getVolume()
                        ));
                        item.restartVolume();
                    }
                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe("Error in saveInstants: " + e.getMessage());
                }
            }, 2400, 60L * ticksPerSecond);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule saveInstants: " + e.getMessage());
        }
    }

    private void hourlyTask() {
        try {
            LocalTime timeNow = LocalTime.now();
            LocalTime nextHour = timeNow.plusHours(1).withMinute(0).withSecond(0);
            Duration timeRemaining = Duration.between(timeNow, nextHour);

            Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {
                try {
                    for (Item item : MarketManager.getInstance().getAllItems()) {
                        item.getPrice().restartHourLimits();
                    }

                    MarketManager.getInstance().setOperationsLastHour(0);

                    if (Config.getInstance().getAlertsMenuEnabled())
                        DatabaseManager.get().getDatabase().purgeAlerts();
                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe("Error in hourlyTask: " + e.getMessage());
                }
            }, timeRemaining.getSeconds()*ticksPerSecond, 60 * 60 * ticksPerSecond);
        } catch (Exception e) {
            Nascraft.getInstance().getLogger().severe("Could not schedule hourlyTask: " + e.getMessage());
        }
    }
}