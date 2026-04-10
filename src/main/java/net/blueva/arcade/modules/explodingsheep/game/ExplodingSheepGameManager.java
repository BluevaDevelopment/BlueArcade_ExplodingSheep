package net.blueva.arcade.modules.explodingsheep.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.modules.explodingsheep.state.ExplodingSheepArenaState;
import net.blueva.arcade.modules.explodingsheep.support.ExplodingSheepLoadoutService;
import net.blueva.arcade.modules.explodingsheep.support.ExplodingSheepMessageService;
import net.blueva.arcade.modules.explodingsheep.support.ExplodingSheepScoreboardService;
import net.blueva.arcade.modules.explodingsheep.support.ExplodingSheepStatsService;
import net.blueva.arcade.modules.explodingsheep.support.sheep.ExplodingSheepSheepData;
import net.blueva.arcade.modules.explodingsheep.support.sheep.ExplodingSheepSheepService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExplodingSheepGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final ExplodingSheepStatsService statsService;
    private final ExplodingSheepMessageService messageService;
    private final ExplodingSheepLoadoutService loadoutService;
    private final ExplodingSheepScoreboardService scoreboardService;
    private final ExplodingSheepSheepService sheepService;

    private final Map<Integer, ExplodingSheepArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArenas = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerSheared = new ConcurrentHashMap<>();

    public ExplodingSheepGameManager(ModuleInfo moduleInfo,
                                     ModuleConfigAPI moduleConfig,
                                     CoreConfigAPI coreConfig,
                                     ExplodingSheepStatsService statsService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.messageService = new ExplodingSheepMessageService(moduleConfig);
        this.loadoutService = new ExplodingSheepLoadoutService(moduleConfig);
        this.scoreboardService = new ExplodingSheepScoreboardService();
        this.sheepService = new ExplodingSheepSheepService(moduleConfig, new Random());
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        ExplodingSheepArenaState state = new ExplodingSheepArenaState(context);
        arenas.put(arenaId, state);

        for (Player player : context.getPlayers()) {
            playerArenas.put(player, arenaId);
        }

        messageService.sendDescription(context);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage("titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage("titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            String title = coreConfig.getLanguage("titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage("titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public boolean freezePlayersOnCountdown() {
        return false;
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ExplodingSheepArenaState state = arenas.get(context.getArenaId());
        if (state == null) {
            return;
        }

        startGameTimer(context, state);
        sheepService.startSheepSpawner(context, state);
        sheepService.startSheepLifecycleTask(context, state);

        for (Player player : context.getPlayers()) {
            playerSheared.put(player, 0);
            player.setGameMode(GameMode.SURVIVAL);
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            context.getScoreboardAPI().showModuleScoreboard(player);
            context.getScoreboardAPI().update(player, scoreboardService.getScoreboardPlaceholders(context, player, state, playerSheared));
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                ExplodingSheepArenaState state) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 180;
        }

        final int[] timeLeft = {gameTime};
        state.setTimeLeft(gameTime);

        String taskId = "arena_" + arenaId + "_exploding_sheep_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            timeLeft[0]--;

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();

            if (alivePlayers.size() <= 1 || timeLeft[0] <= 0) {
                endGameOnce(context, state);
                return;
            }

            state.setTimeLeft(timeLeft[0]);

            String actionBarTemplate = coreConfig.getLanguage("action_bar.in_game.global");
            for (Player player : allPlayers) {
                if (!player.isOnline()) {
                    continue;
                }

                if (actionBarTemplate != null) {
                    String actionBarMessage = actionBarTemplate
                            .replace("{time}", String.valueOf(timeLeft[0]))
                            .replace("{round}", String.valueOf(context.getCurrentRound()))
                            .replace("{round_max}", String.valueOf(context.getMaxRounds()));
                    context.getMessagesAPI().sendActionBar(player, actionBarMessage);
                }

                Map<String, String> customPlaceholders = scoreboardService.getScoreboardPlaceholders(context, player, state, playerSheared);
                context.getScoreboardAPI().update(player, customPlaceholders);
            }
        }, 0L, 20L);
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             ExplodingSheepArenaState state) {
        if (!state.markEnded()) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());
        sheepService.cleanupSheep(state);

        List<Player> leaderboard = scoreboardService.getTopPlayersByShears(context, playerSheared);
        if (!leaderboard.isEmpty()) {
            Player winner = leaderboard.getFirst();
            context.setWinner(winner);
            handleWin(winner, state);

            for (int i = 1; i < Math.min(3, leaderboard.size()); i++) {
                Player podiumPlayer = leaderboard.get(i);
                if (!context.getSpectators().contains(podiumPlayer)) {
                    context.finishPlayer(podiumPlayer);
                }
            }
        }

        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        ExplodingSheepArenaState state = arenas.get(arenaId);
        if (state == null) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        sheepService.cleanupSheep(state);

        arenas.remove(arenaId);

        if (statsService.isEnabled()) {
            statsService.recordGamesPlayed(context.getPlayers());
        }

        playerArenas.entrySet().removeIf(entry -> entry.getValue().equals(arenaId));
        playerSheared.keySet().removeAll(context.getPlayers());
    }

    public void handleDisable() {
        if (!arenas.isEmpty()) {
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> anyContext =
                    arenas.values().iterator().next().getContext();
            anyContext.getSchedulerAPI().cancelModuleTasks("exploding_sheep");
        }

        for (ExplodingSheepArenaState state : arenas.values()) {
            sheepService.cleanupSheep(state);
        }

        arenas.clear();
        playerArenas.clear();
        playerSheared.clear();
    }

    public void handleWin(Player player, ExplodingSheepArenaState state) {
        if (state.getWinner() != null) {
            return;
        }

        state.setWinner(player.getUniqueId());
        statsService.recordWin(player);
    }

    public void handlePlayerElimination(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        // Don't eliminate spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        ExplodingSheepArenaState state = arenas.get(context.getArenaId());
        if (state == null) {
            return;
        }

        Set<UUID> notified = state.getEliminationNotified();
        if (!notified.add(player.getUniqueId())) {
            return;
        }

        messageService.broadcastDeathMessage(context, player);
        context.eliminatePlayer(player, moduleConfig.getStringFrom("language.yml", "messages.eliminated"));
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public void handleRespawnEffects(Player player) {
        loadoutService.applyRespawnEffects(player);
    }

    public void handleSheepShear(Player player, Sheep sheep) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null || context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        ExplodingSheepArenaState state = arenas.get(context.getArenaId());
        if (state == null) {
            return;
        }

        Map<UUID, ExplodingSheepSheepData> trackedSheep = state.getSheep();
        if (!trackedSheep.containsKey(sheep.getUniqueId())) {
            return;
        }

        trackedSheep.remove(sheep.getUniqueId());
        sheep.remove();

        playerSheared.merge(player, 1, Integer::sum);
        statsService.recordSheepSheared(player);

        context.getSoundsAPI().play(player, sheepService.getConfiguredSound("sounds.shear", Sound.ENTITY_SHEEP_SHEAR), 1.0f, 1.0f);
        updateArenaScoreboards(context, state);
    }

    public boolean isTrackedSheep(Sheep sheep) {
        UUID sheepId = sheep.getUniqueId();
        for (ExplodingSheepArenaState state : arenas.values()) {
            if (state.getSheep().containsKey(sheepId)) {
                return true;
            }
        }
        return false;
    }

    private void updateArenaScoreboards(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        ExplodingSheepArenaState state) {
        for (Player target : context.getPlayers()) {
            if (!target.isOnline()) {
                continue;
            }
            context.getScoreboardAPI().update(target, scoreboardService.getScoreboardPlaceholders(context, target, state, playerSheared));
        }
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return null;
        }
        ExplodingSheepArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return new HashMap<>();
        }

        ExplodingSheepArenaState state = arenas.get(context.getArenaId());
        if (state == null) {
            return new HashMap<>();
        }

        return scoreboardService.getCustomPlaceholders(context, player, state, playerSheared);
    }
}
