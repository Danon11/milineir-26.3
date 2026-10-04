package org.millenaire.fabric.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.fabric.FabricReputationState;

import java.util.*;

/** Quest progress: player, global and per-player villager tags, action data and running quest instances. */
public final class FabricQuestState extends SavedData implements QuestRuntime.State {
    private static final Codec<QuestRuntime.Instance> INSTANCE = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(QuestRuntime.Instance::id),
            Codec.STRING.fieldOf("quest").forGetter(QuestRuntime.Instance::quest),
            UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(QuestRuntime.Instance::player),
            Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC).fieldOf("villagers").forGetter(QuestRuntime.Instance::villagers),
            Codec.INT.fieldOf("step").forGetter(QuestRuntime.Instance::step),
            Codec.LONG.fieldOf("step_start").forGetter(QuestRuntime.Instance::stepStart)
    ).apply(i, QuestRuntime.Instance::new));
    private static final Codec<Map<String, List<String>>> TAGS = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());
    private static final Codec<FabricQuestState> CODEC = RecordCodecBuilder.create(i -> i.group(
            TAGS.optionalFieldOf("player_tags", Map.of()).forGetter(s -> lists(s.playerTags)),
            Codec.STRING.listOf().optionalFieldOf("global_tags", List.of()).forGetter(s -> List.copyOf(s.globalTags)),
            TAGS.optionalFieldOf("villager_tags", Map.of()).forGetter(s -> lists(s.villagerTags)),
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING)).optionalFieldOf("action_data", Map.of())
                    .forGetter(s -> s.actionData),
            INSTANCE.listOf().optionalFieldOf("instances", List.of()).forGetter(s -> List.copyOf(s.instances))
    ).apply(i, FabricQuestState::new));
    private static final SavedDataType<FabricQuestState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "quests"), FabricQuestState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Set<String>> playerTags = new TreeMap<>();
    private final Set<String> globalTags = new TreeSet<>();
    /** Villager id to tags; tags are stored as {@code <player>_<tag>} as in the original. */
    private final Map<String, Set<String>> villagerTags = new TreeMap<>();
    private final Map<String, Map<String, String>> actionData = new TreeMap<>();
    private final List<QuestRuntime.Instance> instances = new ArrayList<>();
    private MinecraftServer server;

    public FabricQuestState() {}

    private FabricQuestState(Map<String, List<String>> players, List<String> global, Map<String, List<String>> villagers,
                             Map<String, Map<String, String>> actions, List<QuestRuntime.Instance> instances) {
        players.forEach((key, tags) -> playerTags.put(key, new TreeSet<>(tags)));
        globalTags.addAll(global);
        villagers.forEach((key, tags) -> villagerTags.put(key, new TreeSet<>(tags)));
        actions.forEach((key, values) -> actionData.put(key, new TreeMap<>(values)));
        this.instances.addAll(instances);
    }

    private static Map<String, List<String>> lists(Map<String, Set<String>> source) {
        Map<String, List<String>> result = new TreeMap<>();
        source.forEach((key, tags) -> result.put(key, List.copyOf(tags)));
        return result;
    }

    public static FabricQuestState get(MinecraftServer server) {
        FabricQuestState state = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        state.server = server;
        return state;
    }

    static Codec<FabricQuestState> codec() { return CODEC; }

    @Override public boolean playerTag(UUID player, String tag) { return playerTags.getOrDefault(player.toString(), Set.of()).contains(tag); }
    @Override public boolean globalTag(String tag) { return globalTags.contains(tag); }
    @Override public boolean villagerTag(UUID villager, UUID player, String tag) {
        return villagerTags.getOrDefault(villager.toString(), Set.of()).contains(player + "_" + tag);
    }
    @Override public int reputation(String village, UUID player) {
        return server == null ? 0 : FabricReputationState.get(server).get(village, player);
    }
    @Override public List<QuestRuntime.Instance> active(UUID player) {
        return instances.stream().filter(instance -> instance.player().equals(player)).toList();
    }

    public Optional<QuestRuntime.Instance> instance(long id) { return instances.stream().filter(i -> i.id() == id).findFirst(); }
    public List<QuestRuntime.Instance> instances() { return List.copyOf(instances); }

    public void add(QuestRuntime.Instance instance) { instances.add(instance); setDirty(); }
    public void replace(QuestRuntime.Instance instance) {
        instances.replaceAll(existing -> existing.id() == instance.id() ? instance : existing);
        setDirty();
    }
    public void remove(long id) { if (instances.removeIf(i -> i.id() == id)) setDirty(); }

    public void setPlayerTag(UUID player, String tag, boolean value) {
        var tags = playerTags.computeIfAbsent(player.toString(), ignored -> new TreeSet<>());
        if (value ? tags.add(tag) : tags.remove(tag)) setDirty();
    }
    public void setGlobalTag(String tag, boolean value) { if (value ? globalTags.add(tag) : globalTags.remove(tag)) setDirty(); }
    public void setVillagerTag(UUID villager, UUID player, String tag, boolean value) {
        var tags = villagerTags.computeIfAbsent(villager.toString(), ignored -> new TreeSet<>());
        if (value ? tags.add(player + "_" + tag) : tags.remove(player + "_" + tag)) setDirty();
    }
    public void setActionData(UUID player, String key, String value) {
        actionData.computeIfAbsent(player.toString(), ignored -> new TreeMap<>()).put(key, value);
        setDirty();
    }
    public Set<String> playerTags(UUID player) { return Set.copyOf(playerTags.getOrDefault(player.toString(), Set.of())); }
}
