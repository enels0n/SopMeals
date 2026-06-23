package net.enelson.sopmeals;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SopMeals extends JavaPlugin {

    private MealManager mealManager;
    private BreweryHook breweryHook;
    private boolean placeholderApi;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        breweryHook = new BreweryHook();
        placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        mealManager = new MealManager(this);
        mealManager.load();

        getServer().getPluginManager().registerEvents(new ConsumeListener(this), this);

        PluginCommand command = getCommand("sopmeals");
        if (command != null) {
            MealCommand executor = new MealCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("SopMeals включён" + (breweryHook.isAvailable() ? " (BreweryX подключён)" : ""));
    }

    public MealManager getMealManager() {
        return mealManager;
    }

    public BreweryHook getBreweryHook() {
        return breweryHook;
    }

    /** Применяет PlaceholderAPI, если он установлен. */
    public String applyPlaceholders(Player player, String text) {
        if (text == null) {
            return "";
        }
        if (placeholderApi && player != null) {
            try {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {
            }
        }
        return text;
    }
}
