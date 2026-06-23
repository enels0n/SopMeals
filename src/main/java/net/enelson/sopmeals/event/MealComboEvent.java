package net.enelson.sopmeals.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается, когда игрок собрал и активировал комбо из еды/напитков.
 */
public class MealComboEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String comboId;

    public MealComboEvent(Player player, String comboId) {
        this.player = player;
        this.comboId = comboId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getComboId() {
        return comboId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
