package org.millenaire.fabric.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.fabric.MillenaireCommands;
import org.millenaire.fabric.content.BuildingPlacement;

import java.util.*;

/**
 * Quest world buildings ({@code bedrockbuilding:culture,building}): when a quest step that names one is done,
 * the building is placed in the wild 150 to 300 blocks from the player, who is told where. Reaching it sets
 * the player tag {@code explored_<building>}, which the following step asks for.
 */
public final class QuestSites extends SavedData {
    public record Site(String player, String building, int x, int y, int z, int radius) {
        BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private static final Codec<Site> SITE = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("player").forGetter(Site::player), Codec.STRING.fieldOf("building").forGetter(Site::building),
            Codec.INT.fieldOf("x").forGetter(Site::x), Codec.INT.fieldOf("y").forGetter(Site::y), Codec.INT.fieldOf("z").forGetter(Site::z),
            Codec.INT.fieldOf("radius").forGetter(Site::radius)
    ).apply(i, Site::new));
    private static final Codec<QuestSites> CODEC = RecordCodecBuilder.create(i -> i.group(
            SITE.listOf().optionalFieldOf("sites", List.of()).forGetter(s -> List.copyOf(s.sites))
    ).apply(i, QuestSites::new));
    private static final SavedDataType<QuestSites> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "quest_sites"), QuestSites::new, CODEC, DataFixTypes.LEVEL);
    static final int MIN_DISTANCE = 150, MAX_DISTANCE = 300, ATTEMPTS = 12;

    private final List<Site> sites = new ArrayList<>();

    public QuestSites() {}
    private QuestSites(List<Site> sites) { this.sites.addAll(sites); }

    public static QuestSites get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    static Codec<QuestSites> codec() { return CODEC; }

    public List<Site> sites() { return List.copyOf(sites); }

    public static String exploredTag(String building) { return "explored_" + building; }

    /** Places the quest building for the player and remembers it; returns the message for the player. */
    public static Component place(ServerPlayer player, QuestDefinition.BedrockBuilding building) {
        ServerLevel level = player.level();
        var catalog = MillenaireCommands.contentCatalog();
        var plan = catalog.plan(building.culture() + ":" + building.building() + "_A0");
        if (plan == null) return Component.literal("The place this quest speaks of (" + building.building() + ") is unknown.").withStyle(ChatFormatting.RED);
        var random = new Random(level.getGameTime() ^ player.getUUID().getLeastSignificantBits());
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);
            int x = player.getBlockX() + (int) (Math.cos(angle) * distance), z = player.getBlockZ() + (int) (Math.sin(angle) * distance);
            level.getChunk(x >> 4, z >> 4); // generating one chunk on a quest action is acceptable
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) continue;
            try {
                BlockPos origin = new BlockPos(x, y, z);
                var prepared = BuildingPlacement.prepare(plan, catalog.palette(), origin, random.nextInt(4), catalog.goods(), level.getSeed() ^ origin.asLong());
                // Load (generating if needed) the chunks under the whole footprint before checking it.
                Set<Long> chunks = new HashSet<>();
                for (var change : prepared.changes()) chunks.add(net.minecraft.world.level.ChunkPos.pack(change.pos().getX() >> 4, change.pos().getZ() >> 4));
                for (long chunk : chunks) level.getChunk(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk));
                var world = BuildingPlacement.world(level);
                if (!BuildingPlacement.checkDestinations(prepared, world, true).isEmpty()) continue;
                BuildingPlacement.place(prepared, world, true);
                var sites = get(level.getServer());
                sites.sites.add(new Site(player.getStringUUID(), building.building(), x, y, z, Math.max(plan.width(), plan.length()) / 2 + 4));
                sites.setDirty();
                String direction = direction(x - player.getBlockX(), z - player.getBlockZ());
                return Component.literal("You are told of a strange place " + distance + " blocks to the " + direction
                        + ", at " + x + " " + y + " " + z + ".").withStyle(ChatFormatting.AQUA);
            } catch (java.io.IOException | RuntimeException exception) {
                // try another spot
            }
        }
        return Component.literal("The place this quest speaks of could not be found; try again elsewhere.").withStyle(ChatFormatting.RED);
    }

    public static String direction(int dx, int dz) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        return names[(int) Math.floorMod(Math.round(angle / 45.0), 8)];
    }

    /** Marks sites reached by their players, every two seconds. */
    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 40 != 0) return;
        var sites = get(server);
        if (sites.sites.isEmpty()) return;
        var quests = FabricQuestState.get(server);
        for (Site site : List.copyOf(sites.sites)) {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(site.player()));
            if (player == null || player.level() != server.overworld()) continue;
            if (!player.blockPosition().closerThan(site.pos(), site.radius())) continue;
            quests.setPlayerTag(player.getUUID(), exploredTag(site.building()), true);
            sites.sites.remove(site);
            sites.setDirty();
            player.sendSystemMessage(Component.literal("[Millénaire] You have found the place you were looking for. Go back and tell what you saw.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
