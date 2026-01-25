package net.blueva.arcade.modules.explodingsheep.support;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.explodingsheep.state.ExplodingSheepArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExplodingSheepScoreboardService {

    public Map<String, String> getCustomPlaceholders(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            Player player,
            ExplodingSheepArenaState state,
            Map<Player, Integer> playerSheared) {
        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
        placeholders.put("total_players", String.valueOf(context.getPlayers().size()));
        placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
        placeholders.put("sheared_sheep", String.valueOf(playerSheared.getOrDefault(player, 0)));

        placeholders.put("active_sheep", String.valueOf(state.getSheep().size()));
        placeholders.put("time", String.valueOf(state.getTimeLeft()));

        return placeholders;
    }

    public Map<String, String> getScoreboardPlaceholders(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            Player player,
            ExplodingSheepArenaState state,
            Map<Player, Integer> playerSheared) {
        Map<String, String> placeholders = getCustomPlaceholders(context, player, state, playerSheared);

        placeholders.put("time", String.valueOf(Math.max(0, state.getTimeLeft())));
        placeholders.put("round", String.valueOf(context.getCurrentRound()));
        placeholders.put("round_max", String.valueOf(context.getMaxRounds()));
        placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
        placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
        placeholders.put("alive_total", placeholders.get("alive") + "/" + placeholders.get("total_players"));

        List<Player> leaderboard = getTopPlayersByShears(context, playerSheared);
        placeholders.put("place_1", leaderboard.size() >= 1 ? leaderboard.get(0).getName() : "-");
        placeholders.put("place_2", leaderboard.size() >= 2 ? leaderboard.get(1).getName() : "-");
        placeholders.put("place_3", leaderboard.size() >= 3 ? leaderboard.get(2).getName() : "-");
        placeholders.put("place_4", leaderboard.size() >= 4 ? leaderboard.get(3).getName() : "-");
        placeholders.put("place_5", leaderboard.size() >= 5 ? leaderboard.get(4).getName() : "-");
        placeholders.put("shears_1", leaderboard.size() >= 1 ? String.valueOf(playerSheared.getOrDefault(leaderboard.get(0), 0)) : "0");
        placeholders.put("shears_2", leaderboard.size() >= 2 ? String.valueOf(playerSheared.getOrDefault(leaderboard.get(1), 0)) : "0");
        placeholders.put("shears_3", leaderboard.size() >= 3 ? String.valueOf(playerSheared.getOrDefault(leaderboard.get(2), 0)) : "0");
        placeholders.put("shears_4", leaderboard.size() >= 4 ? String.valueOf(playerSheared.getOrDefault(leaderboard.get(3), 0)) : "0");
        placeholders.put("shears_5", leaderboard.size() >= 5 ? String.valueOf(playerSheared.getOrDefault(leaderboard.get(4), 0)) : "0");

        return placeholders;
    }

    public List<Player> getTopPlayersByShears(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            Map<Player, Integer> playerSheared) {
        List<Player> candidates = context.getAlivePlayers();
        if (candidates.isEmpty()) {
            candidates = context.getPlayers();
        }
        Map<Player, Integer> shears = new HashMap<>();
        for (Player player : candidates) {
            shears.put(player, playerSheared.getOrDefault(player, 0));
        }

        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(shears.entrySet());
        sorted.sort((a, b) -> {
            int compare = Integer.compare(b.getValue(), a.getValue());
            if (compare != 0) {
                return compare;
            }
            return a.getKey().getName().compareToIgnoreCase(b.getKey().getName());
        });

        List<Player> leaderboard = new ArrayList<>();
        for (Map.Entry<Player, Integer> entry : sorted) {
            leaderboard.add(entry.getKey());
            if (leaderboard.size() >= 5) {
                break;
            }
        }

        return leaderboard;
    }
}
