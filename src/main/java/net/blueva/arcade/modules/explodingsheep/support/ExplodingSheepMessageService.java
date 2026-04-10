package net.blueva.arcade.modules.explodingsheep.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ExplodingSheepMessageService {

    private final ModuleConfigAPI moduleConfig;

    public ExplodingSheepMessageService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void sendDescription(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<String> description = moduleConfig.getStringListFrom("language.yml", "description");

        for (Player player : context.getPlayers()) {
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void broadcastDeathMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      Player victim) {
        // Don't broadcast death messages for spectators
        if (context.getSpectators().contains(victim)) {
            return;
        }

        String message = getRandomMessage("messages.deaths.generic");
        if (message == null) {
            return;
        }

        message = message.replace("{victim}", victim.getName());

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    public String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getStringListFrom("language.yml", path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }
}
