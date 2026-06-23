package net.enelson.sopmeals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Хранит недавно съеденное каждым игроком и проверяет совпадения с комбо.
 */
public class MealManager {

    private final SopMeals plugin;

    private long mealWindowMillis;
    private boolean consumeOnCombo;
    private boolean blockVanillaNutrition;
    private long comboCooldownMillis;
    private List<ComboDefinition> combos = new ArrayList<>();

    // Недавно съеденное: игрок -> список (ключ ингредиента + время).
    private final Map<UUID, List<Consumed>> recent = new ConcurrentHashMap<>();
    // Кулдаун срабатывания комбо: игрок -> (имя комбо -> время последнего срабатывания).
    private final Map<UUID, Map<String, Long>> lastTrigger = new ConcurrentHashMap<>();

    public MealManager(SopMeals plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        mealWindowMillis = Math.max(1, plugin.getConfig().getLong("meal-window-seconds", 120)) * 1000L;
        consumeOnCombo = plugin.getConfig().getBoolean("consume-on-combo", true);
        blockVanillaNutrition = plugin.getConfig().getBoolean("block-vanilla-nutrition", false);
        comboCooldownMillis = Math.max(0, plugin.getConfig().getLong("combo-cooldown-seconds", 1)) * 1000L;

        List<ComboDefinition> loaded = new ArrayList<>();
        ConfigurationSection combosSection = plugin.getConfig().getConfigurationSection("combos");
        if (combosSection != null) {
            for (String name : combosSection.getKeys(false)) {
                ConfigurationSection section = combosSection.getConfigurationSection(name);
                if (section == null) {
                    continue;
                }
                Map<String, Integer> ingredients = new HashMap<>();
                ConfigurationSection ingSection = section.getConfigurationSection("ingredients");
                if (ingSection != null) {
                    for (String key : ingSection.getKeys(false)) {
                        int count = ingSection.getInt(key, 1);
                        if (count > 0) {
                            ingredients.put(normalizeKey(key), count);
                        }
                    }
                }
                if (ingredients.isEmpty()) {
                    plugin.getLogger().warning("Комбо '" + name + "' без ингредиентов — пропущено.");
                    continue;
                }
                int food = section.getInt("food", 0);
                double saturation = section.getDouble("saturation", 0.0);
                List<String> effects = section.getStringList("effects");
                String message = section.getString("message", null);
                loaded.add(new ComboDefinition(name, ingredients, food, saturation, effects, message));
            }
        }
        // Большие комбо проверяем первыми, чтобы они имели приоритет над частичными.
        loaded.sort(Comparator.comparingInt(ComboDefinition::getTotalIngredients).reversed());
        combos = loaded;
        plugin.getLogger().info("Загружено комбо: " + combos.size());
    }

    /** Приводит ключ к каноническому виду. brewery:Name -> brewery:name (нижний регистр), материалы — в верхний регистр. */
    static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.toLowerCase().startsWith("brewery:")) {
            return "brewery:" + trimmed.substring("brewery:".length()).trim().toLowerCase();
        }
        return trimmed.toUpperCase();
    }

    public void handleConsume(Player player, String ingredientKey) {
        if (ingredientKey == null || ingredientKey.isEmpty() || combos.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        List<Consumed> list = recent.computeIfAbsent(uuid, k -> new ArrayList<>());
        purgeOld(list, now);
        list.add(new Consumed(normalizeKey(ingredientKey), now));

        evaluate(player, list, now);
    }

    private void evaluate(Player player, List<Consumed> list, long now) {
        Map<String, Long> triggers = lastTrigger.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        for (ComboDefinition combo : combos) {
            Long last = triggers.get(combo.getName());
            if (last != null && now - last < comboCooldownMillis) {
                continue;
            }
            if (!matches(list, combo)) {
                continue;
            }
            applyReward(player, combo);
            triggers.put(combo.getName(), now);
            if (consumeOnCombo) {
                removeIngredients(list, combo);
            }
        }
    }

    private boolean matches(List<Consumed> list, ComboDefinition combo) {
        Map<String, Integer> available = new HashMap<>();
        for (Consumed consumed : list) {
            available.merge(consumed.key, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> required : combo.getIngredients().entrySet()) {
            int have = available.getOrDefault(required.getKey(), 0);
            if (have < required.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void removeIngredients(List<Consumed> list, ComboDefinition combo) {
        for (Map.Entry<String, Integer> required : combo.getIngredients().entrySet()) {
            int toRemove = required.getValue();
            Iterator<Consumed> it = list.iterator();
            while (it.hasNext() && toRemove > 0) {
                if (it.next().key.equals(required.getKey())) {
                    it.remove();
                    toRemove--;
                }
            }
        }
    }

    private void applyReward(Player player, ComboDefinition combo) {
        if (combo.getFood() != 0) {            int newFood = Math.max(0, Math.min(20, player.getFoodLevel() + combo.getFood()));
            player.setFoodLevel(newFood);
        }
        if (combo.getSaturation() != 0.0) {
            float maxSaturation = player.getFoodLevel();
            float newSaturation = (float) Math.max(0.0, Math.min(maxSaturation, player.getSaturation() + combo.getSaturation()));
            player.setSaturation(newSaturation);
        }
        for (String effectString : combo.getEffects()) {
            PotionEffect effect = parseEffect(effectString);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
        String message = combo.getMessage();
        if (message != null && !message.isEmpty()) {
            player.sendMessage(color(plugin.applyPlaceholders(player, message)));
        }

        // Сообщаем экосистеме (например, SopBattlePass), что комбо сработало.
        try {
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new net.enelson.sopmeals.event.MealComboEvent(player, combo.getName()));
        } catch (Throwable ignored) {
        }
    }

    private PotionEffect parseEffect(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String[] parts = raw.split(":");
            PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase());
            if (type == null) {
                plugin.getLogger().warning("Неизвестный эффект в комбо: " + parts[0]);
                return null;
            }
            int duration = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 200;
            int amplifier = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            return new PotionEffect(type, duration, amplifier);
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось разобрать эффект '" + raw + "': " + exception.getMessage());
            return null;
        }
    }

    private void purgeOld(List<Consumed> list, long now) {
        Iterator<Consumed> it = list.iterator();
        while (it.hasNext()) {
            if (now - it.next().time > mealWindowMillis) {
                it.remove();
            }
        }
    }

    public void clear(UUID uuid) {
        recent.remove(uuid);
        lastTrigger.remove(uuid);
    }

    public boolean isBlockVanillaNutrition() {
        return blockVanillaNutrition;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final class Consumed {
        final String key;
        final long time;

        Consumed(String key, long time) {
            this.key = key;
            this.time = time;
        }
    }
}
