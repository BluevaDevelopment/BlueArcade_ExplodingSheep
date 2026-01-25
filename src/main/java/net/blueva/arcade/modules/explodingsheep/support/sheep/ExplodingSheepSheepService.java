package net.blueva.arcade.modules.explodingsheep.support.sheep;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.explodingsheep.state.ExplodingSheepArenaState;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ExplodingSheepSheepService {

    private final ModuleConfigAPI moduleConfig;
    private final Random random;

    public ExplodingSheepSheepService(ModuleConfigAPI moduleConfig, Random random) {
        this.moduleConfig = moduleConfig;
        this.random = random;
    }

    public void startSheepSpawner(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            ExplodingSheepArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_exploding_sheep_spawns";

        int intervalTicks = moduleConfig.getInt("sheep.spawning.interval_ticks", 20);
        int perPlayer = Math.max(1, moduleConfig.getInt("sheep.spawning.per_player", 3));
        int minSheep = Math.max(perPlayer, moduleConfig.getInt("sheep.spawning.min_sheep", 20));
        int batchSize = Math.max(1, moduleConfig.getInt("sheep.spawning.batch_size", 5));
        int maxSheep = Math.max(minSheep, moduleConfig.getInt("sheep.spawning.max_sheep", 90));

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            if (context.getPhase() != GamePhase.PLAYING) {
                return;
            }

            int alivePlayers = context.getAlivePlayers().size();
            if (alivePlayers == 0) {
                return;
            }

            Map<UUID, ExplodingSheepSheepData> tracked = state.getSheep();
            if (tracked.size() >= maxSheep) {
                return;
            }

            int desiredSheep = Math.min(maxSheep, Math.max(minSheep, alivePlayers * perPlayer));
            int toSpawn = Math.min(batchSize, Math.max(0, desiredSheep - tracked.size()));

            for (int i = 0; i < toSpawn; i++) {
                spawnSheep(context, state);
            }
        }, 0L, intervalTicks);
    }

    public void startSheepLifecycleTask(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            ExplodingSheepArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_exploding_sheep_fuses";
        int tickRate = Math.max(2, moduleConfig.getInt("sheep.fuse.update_ticks", 5));

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            Map<UUID, ExplodingSheepSheepData> tracked = state.getSheep();
            if (tracked.isEmpty()) {
                return;
            }

            Iterator<Map.Entry<UUID, ExplodingSheepSheepData>> iterator = tracked.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, ExplodingSheepSheepData> entry = iterator.next();
                ExplodingSheepSheepData data = entry.getValue();
                Sheep sheep = data.sheep();

                if (sheep.isDead() || !sheep.isValid()) {
                    iterator.remove();
                    continue;
                }

                if (!context.isInsideBounds(sheep.getLocation())) {
                    sheep.remove();
                    iterator.remove();
                    continue;
                }

                data.advance(tickRate);
                applyFuseWarnings(data);

                if (data.ticksLeft() <= 0) {
                    explodeSheep(context, data);
                    iterator.remove();
                }
            }
        }, tickRate, tickRate);
    }

    public void spawnSheep(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           ExplodingSheepArenaState state) {
        Location spawn = null;
        for (int attempt = 0; attempt < 6 && spawn == null; attempt++) {
            spawn = getRandomSheepSpawn(context);
        }

        if (spawn == null) {
            return;
        }

        World world = spawn.getWorld();
        if (world == null) {
            return;
        }

        Sheep sheep = world.spawn(spawn, Sheep.class, spawned -> {
            spawned.setColor(DyeColor.WHITE);
            spawned.setAI(true);
            spawned.setSheared(false);
            spawned.setAdult();
            spawned.setRemoveWhenFarAway(false);
        });

        int fuseSeconds = Math.max(5, moduleConfig.getInt("sheep.fuse.duration_seconds", 16));
        ExplodingSheepSheepData data = new ExplodingSheepSheepData(sheep, fuseSeconds * 20);

        state.getSheep().put(sheep.getUniqueId(), data);
    }

    public Location getRandomSheepSpawn(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Location min = context.getArenaAPI().getBoundsMin();
        Location max = context.getArenaAPI().getBoundsMax();
        World world = min.getWorld();

        if (world == null) {
            return null;
        }

        double minX = Math.min(min.getX(), max.getX());
        double maxX = Math.max(min.getX(), max.getX());
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxZ = Math.max(min.getZ(), max.getZ());

        double x = random(minX, maxX);
        double z = random(minZ, maxZ);
        int highestY = world.getHighestBlockYAt((int) x, (int) z);
        double topY = Math.max(min.getY(), max.getY());
        double bottomY = Math.min(min.getY(), max.getY());
        double spawnY = highestY + moduleConfig.getDouble("sheep.spawning.height_offset", 1.0D);
        double clampedY = Math.min(Math.max(spawnY, bottomY + 1), topY + 1);

        Location spawn = new Location(world, x + 0.5, clampedY, z + 0.5);
        return context.isInsideBounds(spawn) ? spawn : null;
    }

    private double random(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private void applyFuseWarnings(ExplodingSheepSheepData data) {
        Sheep sheep = data.sheep();
        int remainingSeconds = Math.max(0, data.ticksLeft() / 20);

        int yellowAt = moduleConfig.getInt("sheep.fuse.yellow_at_seconds", 22);
        int orangeAt = moduleConfig.getInt("sheep.fuse.orange_at_seconds", 12);
        int redAt = moduleConfig.getInt("sheep.fuse.red_at_seconds", 6);
        int blinkStart = moduleConfig.getInt("sheep.fuse.blink_start_seconds", 3);
        int blinkInterval = Math.max(2, moduleConfig.getInt("sheep.fuse.blink_interval_ticks", 4));

        DyeColor targetColor = DyeColor.WHITE;
        if (remainingSeconds <= redAt) {
            targetColor = DyeColor.RED;
        } else if (remainingSeconds <= orangeAt) {
            targetColor = DyeColor.ORANGE;
        } else if (remainingSeconds <= yellowAt) {
            targetColor = DyeColor.YELLOW;
        }

        sheep.setColor(targetColor);

        if (remainingSeconds <= blinkStart) {
            if (data.blinkTicks() >= blinkInterval) {
                data.toggleBlink();
                data.resetBlinkTicks();
            }

            sheep.setColor(data.isBlinkState() ? DyeColor.WHITE : DyeColor.RED);
            sheep.getWorld().spawnParticle(
                    Particle.CLOUD,
                    sheep.getLocation().add(0, 0.5, 0),
                    6, 0.25, 0.25, 0.25, 0.02
            );

            if (data.warningTicks() >= 20) {
                Sound beepSound = getConfiguredSound("sounds.beep", Sound.BLOCK_NOTE_BLOCK_HAT);
                sheep.getWorld().playSound(sheep.getLocation(), beepSound, 0.7f, 1.0f);
                data.resetWarningTicks();
            }
        } else if (data.warningTicks() >= 20) {
            Sound warningSound = getConfiguredSound("sounds.warning", Sound.BLOCK_NOTE_BLOCK_PLING);
            sheep.getWorld().playSound(sheep.getLocation(), warningSound, 0.6f, 1.0f);
            data.resetWarningTicks();
            data.resetBlinkTicks();
        }
    }

    private void explodeSheep(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              ExplodingSheepSheepData data) {
        Sheep sheep = data.sheep();
        Location location = sheep.getLocation();
        World world = location.getWorld();
        if (world == null) {
            sheep.remove();
            return;
        }

        sheep.remove();

        double radius = moduleConfig.getDouble("sheep.explosion.radius", 2.5D);
        double damage = moduleConfig.getDouble("sheep.explosion.damage", 6.0D);
        double knockback = moduleConfig.getDouble("sheep.explosion.knockback", 0.65D);
        boolean smoke = moduleConfig.getBoolean("sheep.explosion.smoke", true);
        boolean clearDrops = moduleConfig.getBoolean("sheep.explosion.clear_drops", true);

        Sound explosionSound = getConfiguredSound("sounds.explosion", Sound.ENTITY_GENERIC_EXPLODE);
        world.playSound(location, explosionSound, 1.0f, 1.0f);

        if (smoke) {
            world.spawnParticle(Particle.EXPLOSION, location, 1, 0.2, 0.2, 0.2);
            world.spawnParticle(Particle.CLOUD, location, 10, radius / 2, 0.4, radius / 2, 0.05);
        }

        breakBlocksAround(context, location, radius);
        knockNearbyPlayers(context, location, radius, damage, knockback);
        if (clearDrops) {
            removeNearbyDrops(location, radius + 1);
        }
    }

    private void breakBlocksAround(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   Location center,
                                   double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int minX = (int) Math.floor(center.getX() - radius);
        int maxX = (int) Math.ceil(center.getX() + radius);
        int minY = (int) Math.floor(center.getY() - 1);
        int maxY = (int) Math.ceil(center.getY() + 1);
        int minZ = (int) Math.floor(center.getZ() - radius);
        int maxZ = (int) Math.ceil(center.getZ() + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location target = new Location(world, x, y, z);
                    if (!context.isInsideBounds(target)) {
                        continue;
                    }

                    if (target.distanceSquared(center) > radius * radius) {
                        continue;
                    }

                    Material type = target.getBlock().getType();
                    if (type != Material.AIR && type != Material.BARRIER && type != Material.BEDROCK) {
                        target.getBlock().setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void knockNearbyPlayers(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            Location center,
            double radius,
            double damage,
            double knockback) {
        for (Player player : context.getAlivePlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            double distance = player.getLocation().distance(center);
            if (distance > radius) {
                continue;
            }

            Vector push = player.getLocation().toVector().subtract(center.toVector()).normalize().multiply(knockback);
            player.damage(damage);
            player.setVelocity(player.getVelocity().add(push));
        }
    }

    private void removeNearbyDrops(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity.getType() == EntityType.ITEM || entity.getType() == EntityType.EXPERIENCE_ORB) {
                entity.remove();
            }
        }
    }

    public void cleanupSheep(ExplodingSheepArenaState state) {
        state.getSheep().values().forEach(data -> {
            if (data.sheep().isValid()) {
                data.sheep().remove();
            }
        });
        state.getSheep().clear();
    }

    public Sound getConfiguredSound(String path, Sound fallback) {
        String soundName = moduleConfig.getString(path, fallback.name());
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
