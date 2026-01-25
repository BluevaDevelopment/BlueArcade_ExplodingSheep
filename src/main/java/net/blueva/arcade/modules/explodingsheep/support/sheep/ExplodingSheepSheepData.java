package net.blueva.arcade.modules.explodingsheep.support.sheep;

import org.bukkit.entity.Sheep;

public class ExplodingSheepSheepData {
    private final Sheep sheep;
    private int ticksLeft;
    private boolean blinkState;
    private int warningTicks;
    private int blinkTicks;

    public ExplodingSheepSheepData(Sheep sheep, int ticksLeft) {
        this.sheep = sheep;
        this.ticksLeft = ticksLeft;
    }

    public Sheep sheep() {
        return sheep;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public void advance(int amount) {
        this.ticksLeft = Math.max(0, ticksLeft - amount);
        this.warningTicks += amount;
        this.blinkTicks += amount;
    }

    public boolean isBlinkState() {
        return blinkState;
    }

    public void toggleBlink() {
        this.blinkState = !this.blinkState;
    }

    public int warningTicks() {
        return warningTicks;
    }

    public int blinkTicks() {
        return blinkTicks;
    }

    public void resetWarningTicks() {
        this.warningTicks = 0;
    }

    public void resetBlinkTicks() {
        this.blinkTicks = 0;
    }
}
