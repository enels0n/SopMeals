package net.enelson.sopmeals;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class ConsumeListener implements Listener {

    private final SopMeals plugin;

    public ConsumeListener(SopMeals plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String key = resolveKey(item);
        if (key != null) {
            plugin.getMealManager().handleConsume(player, key);
        }

        // Если включена блокировка ванильного питания — фиксируем текущие значения
        // (уже с учётом бонуса комбо) и возвращаем их на следующий тик, гася ванильную прибавку.
        if (plugin.getMealManager().isBlockVanillaNutrition()) {
            final int food = player.getFoodLevel();
            final float saturation = player.getSaturation();
            final float exhaustion = player.getExhaustion();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.setFoodLevel(food);
                player.setSaturation(saturation);
                player.setExhaustion(exhaustion);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMealManager().clear(event.getPlayer().getUniqueId());
    }

    /** Определяет ключ ингредиента: сначала проверяем напитки BreweryX, иначе ванильный материал. */
    private String resolveKey(ItemStack item) {
        String breweryName = plugin.getBreweryHook().getRecipeName(item);
        if (breweryName != null && !breweryName.isEmpty()) {
            return "brewery:" + breweryName;
        }
        return item.getType().name();
    }
}
