package net.enelson.sopmeals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class MealCommand implements CommandExecutor, TabCompleter {

    private final SopMeals plugin;

    public MealCommand(SopMeals plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("sopmeals.admin")) {
                sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission", "&cНет прав.")));
                return true;
            }
            plugin.getMealManager().load();
            sender.sendMessage(color(plugin.getConfig().getString("messages.reloaded", "&aПерезагружено.")));
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                options.add("reload");
            }
            return options;
        }
        return Collections.emptyList();
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
