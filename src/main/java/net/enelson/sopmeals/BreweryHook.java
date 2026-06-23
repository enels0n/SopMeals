package net.enelson.sopmeals;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Изолированный мост к BreweryX/Brewery через рефлексию.
 * Класс безопасен, даже если BreweryX не установлен — методы просто вернут null.
 */
public final class BreweryHook {

    private final boolean available;
    private Method getBrewMethod;
    private Method getCurrentRecipeMethod;
    private Method getRecipeNameMethod;

    public BreweryHook() {
        boolean ok = false;
        if (Bukkit.getPluginManager().getPlugin("BreweryX") != null
                || Bukkit.getPluginManager().getPlugin("Brewery") != null) {
            try {
                Class<?> apiClass = Class.forName("com.dre.brewery.api.BreweryApi");
                getBrewMethod = apiClass.getMethod("getBrew", ItemStack.class);

                Class<?> brewClass = Class.forName("com.dre.brewery.Brew");
                getCurrentRecipeMethod = brewClass.getMethod("getCurrentRecipe");

                Class<?> recipeClass = Class.forName("com.dre.brewery.recipe.BRecipe");
                getRecipeNameMethod = recipeClass.getMethod("getRecipeName");
                ok = true;
            } catch (Throwable throwable) {
                Bukkit.getLogger().warning("[SopMeals] BreweryX найден, но API не подключилось: " + throwable);
            }
        }
        this.available = ok;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * @return имя рецепта BreweryX для предмета, либо null если это не напиток BreweryX.
     */
    public String getRecipeName(ItemStack item) {
        if (!available || item == null) {
            return null;
        }
        try {
            Object brew = getBrewMethod.invoke(null, item);
            if (brew == null) {
                return null;
            }
            Object recipe = getCurrentRecipeMethod.invoke(brew);
            if (recipe == null) {
                return null;
            }
            Object name = getRecipeNameMethod.invoke(recipe);
            return name != null ? name.toString() : null;
        } catch (Throwable throwable) {
            return null;
        }
    }
}
