package net.blueva.arcade.modules.explodingsheep.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.explodingsheep.game.ExplodingSheepGameManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;

public class ExplodingSheepListener implements Listener {

    private final ExplodingSheepGameManager gameManager;

    public ExplodingSheepListener(ExplodingSheepGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null) {
            return;
        }

        if (!context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() == GamePhase.COUNTDOWN && gameManager.freezePlayersOnCountdown()) {
            player.teleport(event.getFrom());
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            if (!context.isInsideBounds(event.getTo())) {
                Location spawn = context.getArenaAPI().getRandomSpawn();
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
            return;
        }

        if (!context.isInsideBounds(event.getTo())) {
            gameManager.handlePlayerElimination(player);
            return;
        }

        Location boundsMin = context.getArenaAPI().getBoundsMin();
        Location boundsMax = context.getArenaAPI().getBoundsMax();
        double minY = Math.min(boundsMin.getY(), boundsMax.getY());
        if (event.getTo().getY() < minY - 1) {
            gameManager.handlePlayerElimination(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(victim);
        if (context == null) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSheepExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Sheep sheep)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }

        if (!gameManager.isTrackedSheep(sheep)) {
            return;
        }

        event.setCancelled(true);
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(event.getPlayer());
        if (context == null) {
            return;
        }

        if (!context.isPlayerPlaying(event.getPlayer())) {
            return;
        }

        if (event.getBlock().getType() != Material.AIR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShear(PlayerShearEntityEvent event) {
        if (!(event.getEntity() instanceof Sheep sheep)) {
            return;
        }

        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);
        if (context == null || context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (!context.isPlayerPlaying(player)) {
            return;
        }

        event.setCancelled(true);
        gameManager.handleSheepShear(player, sheep);
    }
}
