package lol.zym.removeEnderPearlDrops;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class RemoveEnderPearlDrops extends JavaPlugin implements Listener {

    private final double DETECTION_RADIUS = 64.0;
    private final Random random = new Random();

    private final Set<Biome> ALLOWED_SKELETON_BIOMES = new HashSet<>(Arrays.asList(
            Biome.SNOWY_PLAINS, Biome.ICE_SPIKES, Biome.FROZEN_RIVER,
            Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN, Biome.SNOWY_SLOPES,
            Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.GROVE
    ));

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (World world : getServer().getWorlds()) {
                    for (Enderman enderman : world.getEntitiesByClass(Enderman.class)) {

                        LivingEntity target = enderman.getTarget();
                        // If they have a target, check if it's still valid (not Creative/Spectator)
                        if (target instanceof Player player) {
                            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                                enderman.setTarget(null);
                            } // else statement not needed
                        }
                        findTarget(enderman, world);
                    }
                }
            }
        }.runTaskTimer(this, 20L, 100L);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Enderman enderman)) return;

        // Existing behaviour: strip ender pearls from the drops
        event.getDrops().removeIf(item -> item.getType() == Material.ENDER_PEARL);

        // Bonus loot: only if the enderman was targeting a player who is NOT on a skybase
        LivingEntity target = enderman.getTarget();
        if (target instanceof Player player && !isOnSkybase(player)) {
            addEndermanBonusLoot(event.getDrops());
        }
    }

    // Bonus loot rolled when an enderman dies while targeting a non-skybase player.
// Each entry is rolled independently, so 0..N items can drop.
    private record LootEntry(Material material, double chance, int minAmount, int maxAmount) {}

    private final List<LootEntry> ENDERMAN_BONUS_LOOT = Arrays.asList(
            new LootEntry(Material.DIAMOND,    0.05, 1, 1),
            new LootEntry(Material.EMERALD,    0.15, 1, 1),
            new LootEntry(Material.IRON_INGOT, 0.30, 1, 1),
            new LootEntry(Material.COPPER_INGOT, 0.40, 1, 1),
            new LootEntry(Material.SUGAR_CANE,   0.25, 1, 1),
            new LootEntry(Material.GOLD_INGOT,  0.10, 1, 1),
            new LootEntry(Material.WHEAT_SEEDS, 0.10, 1, 1)
    );

    private void addEndermanBonusLoot(List<ItemStack> drops) {
        for (LootEntry entry : ENDERMAN_BONUS_LOOT) {
            if (random.nextDouble() < entry.chance()) {
                int amount = entry.minAmount() + random.nextInt(entry.maxAmount() - entry.minAmount() + 1);
                drops.add(new ItemStack(entry.material(), amount));
            }
        }
    }

    @EventHandler
    public void onPiglinBarter(PiglinBarterEvent event) {
        ListIterator<ItemStack> it = event.getOutcome().listIterator();
        while (it.hasNext()) {
            if (it.next().getType() == Material.ENDER_PEARL) {
                it.set(new ItemStack(Material.OBSIDIAN, 1));
            }
        }
    }

    private static final int RARE_SKELETON_CHANCE = 2576; // 1 in N

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Skeleton) && !(entity instanceof Stray) && !(entity instanceof Wolf)) return;

        // Skip entities this plugin spawned itself - they're already fully configured.
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        Biome biome = event.getLocation().getBlock().getBiome();
        boolean inSkeletonBiome = ALLOWED_SKELETON_BIOMES.contains(biome);

        // 0. Wolves: a skeleton in a skeleton biome (always) or outside it (1 in 2576);
        //    otherwise a hostile mob.
        if (entity instanceof Wolf) {
            event.setCancelled(true);
            if (inSkeletonBiome || random.nextInt(RARE_SKELETON_CHANCE) == 0) {
                Skeleton skeleton = (Skeleton) event.getLocation().getWorld().spawnEntity(event.getLocation(), EntityType.SKELETON);
                applySkeletonEquipment(skeleton);
            } else {
                spawnReplacement(event.getLocation());
            }
            return;
        }

        // 1. Biome Restriction: outside a skeleton biome the mob is replaced -
        //    except a plain Skeleton survives untouched 1 in 2576 times.
        if (!inSkeletonBiome) {
            if (entity instanceof Skeleton && random.nextInt(RARE_SKELETON_CHANCE) == 0) {
                return; // remain as is
            }
            event.setCancelled(true);
            spawnReplacement(event.getLocation());
            return;
        }

        // 2. Stray Replacement: 7 in 8 Strays become Skeletons, unless it was a freeze conversion
        if (entity instanceof Stray) {
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.FROZEN) {
                if (random.nextInt(8) < 7) {
                    event.setCancelled(true);
                    Skeleton skeleton = (Skeleton) event.getLocation().getWorld().spawnEntity(event.getLocation(), EntityType.SKELETON);
                    applySkeletonEquipment(skeleton);
                    return;
                }
            }
        }

        // 3. Melee Chance: 1 in 8 Skeletons get a wooden melee weapon
        if (entity instanceof Skeleton) {
            applySkeletonEquipment((Skeleton) entity);
        }
    }

    private void spawnReplacement(Location location) {
        int roll = random.nextInt(100); // 0 to 99

        EntityType replacementType;
        if (roll < 80) {        // 0-79 (80%)
            replacementType = EntityType.ZOMBIE;
        } else if (roll < 97) { // 80-96 (16%)
            replacementType = EntityType.CREEPER;
        } else if (roll < 99) { // 97-98 (2%)
            replacementType = EntityType.ENDERMAN;
        } else {                // 99 (1%)
            replacementType = EntityType.WITCH;
        }

        location.getWorld().spawnEntity(location, replacementType);
    }

    private void applySkeletonEquipment(Skeleton skeleton) {
        if (random.nextInt(8) == 0) {
            // Pool of all wooden tools/weapons
            Material[] woodenTools = {
                    Material.WOODEN_SWORD,
                    Material.WOODEN_AXE,
                    Material.WOODEN_PICKAXE,
                    Material.WOODEN_SHOVEL,
                    Material.WOODEN_HOE
            };

            // Select a random tool from the array
            Material selectedTool = woodenTools[random.nextInt(woodenTools.length)];

            skeleton.getEquipment().setItemInMainHand(new ItemStack(selectedTool));

            // Low drop chance so players don't get flooded with wooden hoes
            skeleton.getEquipment().setItemInMainHandDropChance(0.085f);
        }
    }

    public boolean isOnSkybase(Player player) {
        if (player.getLocation().getY() <= 79) return false;
        if (!((Entity) player).isOnGround()) return false;

        Block feet = player.getLocation().getBlock().getRelative(0, -1, 0);

        // Check if they are standing on something valid (solid or glass)
        if (feet.getType().isAir()) return false;

        int airCount = 0;
        for (int i = 2; i <= 15; i++) {
            if (player.getLocation().subtract(0, i, 0).getBlock().getType() == Material.WATER ||  player.getLocation().subtract(0, i, 0).getBlock().getType() == Material.AIR) {
                airCount++;
            }
        }
        // If at least 11 out of 14 blocks below are air, they are likely on a skybase
        return airCount >= 11;
    }

    private void findTarget(Enderman enderman, World world) {
        Player closestPlayer = null;
        double closestDistanceSq = DETECTION_RADIUS * DETECTION_RADIUS;

        for (Player player : world.getPlayers()) {
            // Basic checks: Survival/Adventure and Skybase status
            if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                    && isOnSkybase(player)) {

                double distSq = enderman.getLocation().distanceSquared(player.getLocation());
                if (distSq <= closestDistanceSq) {
                    closestDistanceSq = distSq;
                    closestPlayer = player;
                }
            }
        }

        if (closestPlayer != null) {
            enderman.setTarget(closestPlayer);
        }
    }
    //TODO: Small chance for silverfish to replace monster spawn on stone mountain related biomes
}