package net.enelson.sopmeals;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Одна настроенная комбинация еды/напитков и её награда.
 */
public class ComboDefinition {

    private final String name;
    private final Map<String, Integer> ingredients;
    private final int food;
    private final double saturation;
    private final List<String> effects;
    private final String message;
    private final int totalIngredients;

    public ComboDefinition(String name, Map<String, Integer> ingredients, int food, double saturation,
                           List<String> effects, String message) {
        this.name = name;
        this.ingredients = ingredients;
        this.food = food;
        this.saturation = saturation;
        this.effects = effects != null ? effects : Collections.emptyList();
        this.message = message;
        int total = 0;
        for (Integer count : ingredients.values()) {
            total += count != null ? count : 0;
        }
        this.totalIngredients = total;
    }

    public String getName() {
        return name;
    }

    public Map<String, Integer> getIngredients() {
        return ingredients;
    }

    public int getFood() {
        return food;
    }

    public double getSaturation() {
        return saturation;
    }

    public List<String> getEffects() {
        return effects;
    }

    public String getMessage() {
        return message;
    }

    public int getTotalIngredients() {
        return totalIngredients;
    }
}
