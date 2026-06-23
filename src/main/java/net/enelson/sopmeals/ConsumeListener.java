package net.enelson.sopmeals;

import org.bukkit.ChatColor;
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

    /**
     * HIGH приоритет: проверяем лимит повторного поедания.
     * Если лимит превышен и block-consume включён — отменяем событие.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsumeCheck(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String key = resolveKey(item);
        if (key == null) {
            return;
        }
        if (plugin.getMealManager().isRepeatLimitExceeded(player, key)) {
            if (plugin.getMealManager().isBlockConsume()) {
                event.setCancelled(true);
                String msg = plugin.getMealManager().getRepeatLimitMessage();
                if (msg != null && !msg.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
        }
    }

    /**
     * MONITOR приоритет: регистрируем съеденное, даём базовый бонус, проверяем комбо,
     * блокируем ванильное питание если нужно.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String key = resolveKey(item);
        if (key == null) {
            return;
        }

        MealManager manager = plugin.getMealManager();

        // Записываем факт поедания (для лимита повторов).
        manager.recordConsume(player, key);

        // Базовый бонус за предмет (если лимит не превышен).
        boolean limitOk = !manager.isRepeatLimitExceeded(player, key);
        if (limitOk) {
            manager.applyFoodBonus(player, key);
        }

        // Комбо (всегда регистрируем в историю комбо, даже если лимит на повторы).
        manager.handleConsume(player, key);

        // Блокировка ванильного питания.
        if (manager.isBlockVanillaNutrition()) {
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

    private String resolveKey(ItemStack item) {
        String breweryName = plugin.getBreweryHook().getRecipeName(item);
        if (breweryName != null && !breweryName.isEmpty()) {
            return "brewery:" + breweryName;
        }
        return item.getType().name();
    }
}
