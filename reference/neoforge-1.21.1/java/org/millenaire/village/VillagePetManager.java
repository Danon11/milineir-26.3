package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.entity.MillVillager;
import org.slf4j.Logger;

/**
 * Manages pet spawning for Millénaire villagers.
 * <p>
 * Pets spawn naturally (with limited probability) when a residential building is
 * completed. The type of pet is chosen to match the culture of the village.
 * A village-wide cap prevents too many animals from accumulating.
 */
public final class VillagePetManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Probability (0–100) that a completed residential building gets a pet. */
    private static final int PET_SPAWN_CHANCE_PCT = 25;

    /** One pet is allowed per N villagers. */
    private static final int VILLAGERS_PER_PET = 4;

    /** Hard cap on total pets per village. */
    private static final int MAX_PETS_PER_VILLAGE = 8;

    /** Radius around the village centre used for pet counting. */
    private static final double PET_SEARCH_RADIUS = 150.0;

    private VillagePetManager() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Should be called after a residential building is completed and its
     * occupants have been spawned. Randomly decides whether a pet appears.
     *
     * @param level    the server level
     * @param village  the owning village
     * @param building the newly completed building
     * @param owner    the primary resident (may be {@code null})
     */
    public static void trySpawnPetForBuilding(
            ServerLevel level,
            Village village,
            BuildingInstance building,
            @Nullable MillVillager owner) {

        if (!building.isOperational()) {
            return;
        }

        int currentPets = countVillagePets(level, village);
        int maxPets = computeMaxPets(village);
        if (currentPets >= maxPets) {
            LOGGER.debug("[Millénaire] Pets: {} at cap ({}/{}), skipping",
                    village.getVillageName(), currentPets, maxPets);
            return;
        }

        if (ThreadLocalRandom.current().nextInt(100) >= PET_SPAWN_CHANCE_PCT) {
            return;
        }

        String culture = village.getCultureId().getPath();
        PetType petType = choosePetType(culture);
        if (petType == null) {
            return;
        }

        BlockPos spawnPos = findPetSpawnPos(level, building);
        if (spawnPos == null) {
            return;
        }

        spawnPet(level, spawnPos, petType, owner);
        LOGGER.info("[Millénaire] Spawned {} for village {} (culture: {})",
                petType.name().toLowerCase(), village.getVillageName(), culture);
    }

    // -----------------------------------------------------------------------
    // Pet type selection — culturally appropriate
    // -----------------------------------------------------------------------

    @Nullable
    private static PetType choosePetType(String culture) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        return switch (culture) {
            // European medieval cultures: cats and dogs
            case "norman", "templar", "byzantine" -> rng.nextBoolean() ? PetType.CAT : PetType.DOG;
            // South Asian: parrots, cats, rabbits
            case "indian" -> {
                int roll = rng.nextInt(3);
                yield roll == 0 ? PetType.PARROT : (roll == 1 ? PetType.CAT : PetType.RABBIT);
            }
            // Mesoamerican / Andean: parrots, rabbits, chickens
            case "mayan", "incan" -> {
                int roll = rng.nextInt(3);
                yield roll == 0 ? PetType.PARROT : (roll == 1 ? PetType.RABBIT : PetType.CHICKEN);
            }
            // East Asian: cats, rabbits
            case "japanese" -> rng.nextBoolean() ? PetType.CAT : PetType.RABBIT;
            // Chinese: cats, dogs, rabbits
            case "chinese" -> {
                int roll = rng.nextInt(3);
                yield roll == 0 ? PetType.CAT : (roll == 1 ? PetType.DOG : PetType.RABBIT);
            }
            // Nordic / Inuit: dogs
            case "inuit", "nordic" -> PetType.DOG;
            // Default: cat or dog
            default -> rng.nextBoolean() ? PetType.CAT : PetType.DOG;
        };
    }

    // -----------------------------------------------------------------------
    // Spawning helpers
    // -----------------------------------------------------------------------

    private static void spawnPet(
            ServerLevel level,
            BlockPos pos,
            PetType type,
            @Nullable MillVillager owner) {

        switch (type) {
            case CAT    -> spawnCat(level, pos, owner);
            case DOG    -> spawnDog(level, pos, owner);
            case PARROT -> spawnParrot(level, pos);
            case RABBIT -> spawnRabbit(level, pos);
            case CHICKEN -> spawnChicken(level, pos);
        }
    }

    private static void spawnCat(ServerLevel level, BlockPos pos, @Nullable MillVillager owner) {
        Cat cat = net.minecraft.world.entity.EntityType.CAT.create(level);
        if (cat == null) return;

        cat.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        cat.setPersistenceRequired();

        // Tame the cat; assign owner UUID so it stays near the building
        cat.setTame(true, false);
        if (owner != null) {
            cat.setOwnerUUID(owner.getUUID());
        }

        // Pick a random cat variant from the registry
        List<ResourceKey<CatVariant>> variantKeys = new ArrayList<>(
                BuiltInRegistries.CAT_VARIANT.registryKeySet());
        if (!variantKeys.isEmpty()) {
            ResourceKey<CatVariant> key = variantKeys.get(
                    ThreadLocalRandom.current().nextInt(variantKeys.size()));
            Holder<CatVariant> holder = BuiltInRegistries.CAT_VARIANT.getHolder(key).orElse(null);
            if (holder != null) {
                cat.setVariant(holder);
            }
        }

        level.addFreshEntity(cat);
    }

    private static void spawnDog(ServerLevel level, BlockPos pos, @Nullable MillVillager owner) {
        Wolf wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
        if (wolf == null) return;

        wolf.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        wolf.setPersistenceRequired();

        wolf.setTame(true, false);
        if (owner != null) {
            wolf.setOwnerUUID(owner.getUUID());
        }

        level.addFreshEntity(wolf);
    }

    private static void spawnParrot(ServerLevel level, BlockPos pos) {
        Parrot parrot = net.minecraft.world.entity.EntityType.PARROT.create(level);
        if (parrot == null) return;

        parrot.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        parrot.setPersistenceRequired();

        Parrot.Variant[] variants = Parrot.Variant.values();
        parrot.setVariant(variants[ThreadLocalRandom.current().nextInt(variants.length)]);

        level.addFreshEntity(parrot);
    }

    private static void spawnRabbit(ServerLevel level, BlockPos pos) {
        Rabbit rabbit = net.minecraft.world.entity.EntityType.RABBIT.create(level);
        if (rabbit == null) return;

        rabbit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        rabbit.setPersistenceRequired();

        level.addFreshEntity(rabbit);
    }

    private static void spawnChicken(ServerLevel level, BlockPos pos) {
        Chicken chicken = net.minecraft.world.entity.EntityType.CHICKEN.create(level);
        if (chicken == null) return;

        chicken.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        chicken.setPersistenceRequired();

        level.addFreshEntity(chicken);
    }

    // -----------------------------------------------------------------------
    // Counting and capping
    // -----------------------------------------------------------------------

    /**
     * Estimates how many village-owned pets currently exist by searching for
     * persistence-required cats/wolves/parrots/rabbits/chickens near the
     * village centre.
     */
    private static int countVillagePets(ServerLevel level, Village village) {
        BlockPos center = village.getCenter();
        AABB area = new AABB(
                center.getX() - PET_SEARCH_RADIUS, center.getY() - 64, center.getZ() - PET_SEARCH_RADIUS,
                center.getX() + PET_SEARCH_RADIUS, center.getY() + 64, center.getZ() + PET_SEARCH_RADIUS
        );

        int count = 0;
        count += level.getEntitiesOfClass(Cat.class, area, Cat::requiresCustomPersistence).size();
        count += level.getEntitiesOfClass(Wolf.class, area, Wolf::requiresCustomPersistence).size();
        count += level.getEntitiesOfClass(Parrot.class, area, Parrot::requiresCustomPersistence).size();
        count += level.getEntitiesOfClass(Rabbit.class, area, Rabbit::requiresCustomPersistence).size();
        count += level.getEntitiesOfClass(Chicken.class, area, Chicken::requiresCustomPersistence).size();
        return count;
    }

    private static int computeMaxPets(Village village) {
        int villagerCount = village.getVillagerRecords().size();
        int petsByPopulation = Math.max(1, villagerCount / VILLAGERS_PER_PET);
        return Math.min(petsByPopulation, MAX_PETS_PER_VILLAGE);
    }

    /**
     * Finds a safe spawn position near the building's path-start point.
     */
    @Nullable
    private static BlockPos findPetSpawnPos(ServerLevel level, BuildingInstance building) {
        BlockPos base = building.getPathStartPos();
        if (base == null) return null;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int surfaceY = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        base.getX() + dx,
                        base.getZ() + dz);
                BlockPos candidate = new BlockPos(base.getX() + dx, surfaceY, base.getZ() + dz);
                BlockPos below = candidate.below();
                if (level.getBlockState(below).isSolidRender(level, below)
                        && !level.getBlockState(candidate).isSuffocating(level, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Internal enum
    // -----------------------------------------------------------------------

    private enum PetType {
        CAT, DOG, PARROT, RABBIT, CHICKEN
    }
}
