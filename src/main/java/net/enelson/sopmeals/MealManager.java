package net.enelson.sopmeals;

import java.util.ArrayList;
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

public class MealManager {

    private final SopMeals plugin;

    private boolean blockVanillaNutrition;

    // Repeat limit
    private int repeatMaxPerPeriod;
    private long repeatPeriodMillis;
    private boolean repeatBlockConsume;
    private String repeatLimitMessage;
    private List<String> overeatingEffects = new ArrayList<>();

    // Per-food bonus
    private Map<String, int[]> foodBonuses = new HashMap<>();

    // Pairings (еда + напиток = эффекты)
    private long pairingWindowMillis;
    private List<PairingDefinition> pairings = new ArrayList<>();

    // История поедания для лимита повторов
    private final Map<UUID, Map<String, List<Long>>> repeatHistory = new ConcurrentHashMap<>();
    // Недавно съеденное для пар (ключ + время)
    private final Map<UUID, List<Consumed>> recentConsumes = new ConcurrentHashMap<>();

    public MealManager(SopMeals plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        blockVanillaNutrition = plugin.getConfig().getBoolean("block-vanilla-nutrition", true);

        // Repeat limit
        ConfigurationSection rl = plugin.getConfig().getConfigurationSection("repeat-limit");
        if (rl != null) {
            repeatMaxPerPeriod = rl.getInt("max-per-period", 3);
            repeatPeriodMillis = Math.max(1, rl.getLong("period-seconds", 300)) * 1000L;
            repeatBlockConsume = rl.getBoolean("block-consume", false);
            repeatLimitMessage = rl.getString("message", "");
            overeatingEffects = rl.getStringList("overeating-effects");
        } else {
            repeatMaxPerPeriod = 0;
            repeatPeriodMillis = 300000L;
            repeatBlockConsume = false;
            repeatLimitMessage = "";
            overeatingEffects = new ArrayList<>();
        }

        // Per-food bonuses
        Map<String, int[]> bonuses = new HashMap<>();
        ConfigurationSection foodsSection = plugin.getConfig().getConfigurationSection("foods");
        if (foodsSection != null) {
            for (String key : foodsSection.getKeys(false)) {
                ConfigurationSection fs = foodsSection.getConfigurationSection(key);
                if (fs == null) {
                    continue;
                }
                int food = fs.getInt("food", 0);
                int sat = (int) (fs.getDouble("saturation", 0.0) * 100);
                bonuses.put(normalizeKey(key), new int[] { food, sat });
            }
        }
        foodBonuses = bonuses;

        // Pairings
        pairingWindowMillis = Math.max(1, plugin.getConfig().getLong("pairing-window-seconds", 60)) * 1000L;
        List<PairingDefinition> loadedPairings = new ArrayList<>();
        ConfigurationSection pairingsSection = plugin.getConfig().getConfigurationSection("pairings");
        if (pairingsSection != null) {
            for (String name : pairingsSection.getKeys(false)) {
                ConfigurationSection section = pairingsSection.getConfigurationSection(name);
                if (section == null) {
                    continue;
                }
                String food = normalizeKey(section.getString("food", ""));
                String drink = normalizeKey(section.getString("drink", ""));
                if (food.isEmpty() || drink.isEmpty()) {
                    plugin.getLogger().warning("Пара '" + name + "' без food/drink — пропущена.");
                    continue;
                }
                List<String> effects = section.getStringList("effects");
                String message = section.getString("message", null);
                loadedPairings.add(new PairingDefinition(name, food, drink, effects, message));
            }
        }
        pairings = loadedPairings;

        plugin.getLogger().info("Загружено: foods=" + foodBonuses.size() + ", pairings=" + pairings.size());
    }

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

    // ===== Вызывается из листенера при поедании =====

    public void handleConsume(Player player, String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        String normalized = normalizeKey(key);
        UUID uuid = player.getUniqueId();

        // Записываем для пар
        List<Consumed> list = recentConsumes.computeIfAbsent(uuid, k -> new ArrayList<>());
        purgeOld(list);
        list.add(new Consumed(normalized, System.currentTimeMillis()));

        // Проверяем пары
        evaluatePairings(player, list);
    }

    private void evaluatePairings(Player player, List<Consumed> list) {
        for (PairingDefinition pairing : pairings) {
            boolean hasFood = false;
            boolean hasDrink = false;
            for (Consumed c : list) {
                if (c.key.equals(pairing.food)) {
                    hasFood = true;
                }
                if (c.key.equals(pairing.drink)) {
                    hasDrink = true;
                }
            }
            if (hasFood && hasDrink) {
                applyPairing(player, pairing);
                // Убираем использованные из списка
                removeFirst(list, pairing.food);
                removeFirst(list, pairing.drink);
            }
        }
    }

    private void applyPairing(Player player, PairingDefinition pairing) {
        for (String effectString : pairing.effects) {
            PotionEffect effect = parseEffect(effectString);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
        if (pairing.message != null && !pairing.message.isEmpty()) {
            player.sendMessage(color(plugin.applyPlaceholders(player, pairing.message)));
        }
        // Событие для BattlePass
        try {
            Bukkit.getPluginManager().callEvent(
                    new net.enelson.sopmeals.event.MealComboEvent(player, pairing.name));
        } catch (Throwable ignored) {
        }
    }

    private void removeFirst(List<Consumed> list, String key) {
        Iterator<Consumed> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().key.equals(key)) {
                it.remove();
                return;
            }
        }
    }

    private void purgeOld(List<Consumed> list) {
        long cutoff = System.currentTimeMillis() - pairingWindowMillis;
        Iterator<Consumed> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().time < cutoff) {
                it.remove();
            }
        }
    }

    // ===== Repeat limit =====

    public void recordConsume(Player player, String key) {
        if (repeatMaxPerPeriod <= 0) {
            return;
        }
        String normalized = normalizeKey(key);
        UUID uuid = player.getUniqueId();
        Map<String, List<Long>> history = repeatHistory.computeIfAbsent(uuid, k -> new HashMap<>());
        List<Long> times = history.computeIfAbsent(normalized, k -> new ArrayList<>());
        times.add(System.currentTimeMillis());
    }

    public boolean isRepeatLimitExceeded(Player player, String key) {
        if (repeatMaxPerPeriod <= 0) {
            return false;
        }
        String normalized = normalizeKey(key);
        UUID uuid = player.getUniqueId();
        Map<String, List<Long>> history = repeatHistory.get(uuid);
        if (history == null) {
            return false;
        }
        List<Long> times = history.get(normalized);
        if (times == null) {
            return false;
        }
        long cutoff = System.currentTimeMillis() - repeatPeriodMillis;
        int count = 0;
        Iterator<Long> it = times.iterator();
        while (it.hasNext()) {
            if (it.next() < cutoff) {
                it.remove();
            } else {
                count++;
            }
        }
        return count >= repeatMaxPerPeriod;
    }

    public boolean isBlockConsume() {
        return repeatBlockConsume;
    }

    public String getRepeatLimitMessage() {
        return repeatLimitMessage;
    }

    public void applyOvereatingEffects(Player player) {
        if (overeatingEffects == null || overeatingEffects.isEmpty()) {
            return;
        }
        for (String effectString : overeatingEffects) {
            PotionEffect effect = parseEffect(effectString);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
    }

    // ===== Per-food bonus =====

    public void applyFoodBonus(Player player, String key) {
        String normalized = normalizeKey(key);
        int[] bonus = foodBonuses.get(normalized);
        if (bonus == null) {
            return;
        }
        int foodBonus = bonus[0];
        double satBonus = bonus[1] / 100.0;
        if (foodBonus != 0) {
            int newFood = Math.max(0, Math.min(20, player.getFoodLevel() + foodBonus));
            player.setFoodLevel(newFood);
        }
        if (satBonus != 0.0) {
            float maxSaturation = player.getFoodLevel();
            float newSat = (float) Math.max(0.0, Math.min(maxSaturation, player.getSaturation() + satBonus));
            player.setSaturation(newSat);
        }
    }

    // ===== Misc =====

    public boolean isBlockVanillaNutrition() {
        return blockVanillaNutrition;
    }

    public void clear(UUID uuid) {
        repeatHistory.remove(uuid);
        recentConsumes.remove(uuid);
    }

    private PotionEffect parseEffect(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String[] parts = raw.split(":");
            PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase());
            if (type == null) {
                return null;
            }
            int duration = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 200;
            int amplifier = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            return new PotionEffect(type, duration, amplifier);
        } catch (Exception exception) {
            return null;
        }
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

    private static final class PairingDefinition {
        final String name;
        final String food;
        final String drink;
        final List<String> effects;
        final String message;

        PairingDefinition(String name, String food, String drink, List<String> effects, String message) {
            this.name = name;
            this.food = food;
            this.drink = drink;
            this.effects = effects != null ? effects : new ArrayList<>();
            this.message = message;
        }
    }
}
