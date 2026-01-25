package net.blueva.arcade.modules.explodingsheep.state;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.blueva.arcade.modules.explodingsheep.support.sheep.ExplodingSheepSheepData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExplodingSheepArenaState {

    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private final Map<UUID, ExplodingSheepSheepData> sheep = new ConcurrentHashMap<>();
    private final Set<UUID> eliminationNotified = ConcurrentHashMap.newKeySet();
    private volatile boolean ended;
    private volatile UUID winner;
    private volatile int timeLeft;

    public ExplodingSheepArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        this.context = context;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public Map<UUID, ExplodingSheepSheepData> getSheep() {
        return sheep;
    }

    public Set<UUID> getEliminationNotified() {
        return eliminationNotified;
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean markEnded() {
        if (ended) {
            return false;
        }
        ended = true;
        return true;
    }

    public UUID getWinner() {
        return winner;
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = timeLeft;
    }
}
