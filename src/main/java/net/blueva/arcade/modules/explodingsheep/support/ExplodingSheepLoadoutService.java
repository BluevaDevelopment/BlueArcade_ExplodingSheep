package net.blueva.arcade.modules.explodingsheep.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class ExplodingSheepLoadoutService {

    private final ModuleConfigAPI moduleConfig;

    public ExplodingSheepLoadoutService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void giveStartingItems(Player player) {
        List<String> startingItems = moduleConfig.getStringList("items.starting_items");

        if (startingItems == null || startingItems.isEmpty()) {
            return;
        }

        for (String itemString : startingItems) {
            try {
                String[] parts = itemString.split(":");
                if (parts.length >= 2) {
                    Material material = Material.valueOf(parts[0].toUpperCase());
                    int amount = Integer.parseInt(parts[1]);
                    int slot = parts.length >= 3 ? Integer.parseInt(parts[2]) : -1;

                    ItemStack item = new ItemStack(material, amount);

                    if (slot >= 0 && slot < 36) {
                        player.getInventory().setItem(slot, item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                }
            } catch (Exception e) {
                // Ignore malformed entries
            }
        }
    }

    public void applyStartingEffects(Player player) {
        applyEffects(player, "effects.starting_effects");
    }

    public void applyRespawnEffects(Player player) {
        applyEffects(player, "effects.respawn_effects");
    }

    private void applyEffects(Player player, String path) {
        List<String> effects = moduleConfig.getStringList(path);

        if (effects == null || effects.isEmpty()) {
            return;
        }

        for (String effectString : effects) {
            try {
                String[] parts = effectString.split(":");
                if (parts.length >= 3) {
                    PotionEffectType effectType = PotionEffectType.getByName(parts[0].toUpperCase());
                    int duration = Integer.parseInt(parts[1]);
                    int amplifier = Integer.parseInt(parts[2]);

                    if (effectType != null) {
                        player.addPotionEffect(new PotionEffect(effectType, duration, amplifier, false, false));
                    }
                }
            } catch (Exception e) {
                // Ignore malformed entries
            }
        }
    }
}
