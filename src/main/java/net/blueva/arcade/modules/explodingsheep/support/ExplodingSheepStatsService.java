package net.blueva.arcade.modules.explodingsheep.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.entity.Player;

import java.util.Collection;

public class ExplodingSheepStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;

    public ExplodingSheepStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo, ModuleConfigAPI moduleConfig) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
    }

    public void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getStringFrom("language.yml", "stats.labels.wins", "Wins"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.wins", "Exploding Sheep wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getStringFrom("language.yml", "stats.labels.games_played", "Games Played"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.games_played", "Exploding Sheep games played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("sheep_sheared", moduleConfig.getStringFrom("language.yml", "stats.labels.sheep_sheared", "Sheep sheared"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.sheep_sheared", "Fuse-stopping sheep"), StatScope.MODULE));
    }

    public boolean isEnabled() {
        return statsAPI != null;
    }

    public void recordGamesPlayed(Collection<Player> players) {
        if (statsAPI == null) {
            return;
        }

        for (Player player : players) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }

    public void recordWin(Player player) {
        if (statsAPI == null) {
            return;
        }

        statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
        statsAPI.addGlobalStat(player, "wins", 1);
    }

    public void recordSheepSheared(Player player) {
        if (statsAPI == null) {
            return;
        }

        statsAPI.addModuleStat(player, moduleInfo.getId(), "sheep_sheared", 1);
    }
}
