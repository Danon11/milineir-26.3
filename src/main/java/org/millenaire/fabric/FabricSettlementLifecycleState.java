package org.millenaire.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/** Persistent construction, resource and population state for each placed settlement. */
public final class FabricSettlementLifecycleState extends SavedData {
    private static final Codec<Project.Status> STATUS = Codec.STRING.xmap(Project.Status::valueOf, Project.Status::name);
    private static final Codec<Project> PROJECT = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Project::id),
            Codec.STRING.fieldOf("plan").forGetter(Project::plan),
            Codec.intRange(1, 1_000_000).fieldOf("required_work").forGetter(Project::requiredWork),
            Codec.intRange(0, 1_000_000).fieldOf("material_cost").forGetter(Project::materialCost),
            Codec.intRange(0, 1_000_000).fieldOf("progress").forGetter(Project::progress),
            STATUS.fieldOf("status").forGetter(Project::status)
    ).apply(i, Project::new));
    private static final Codec<Lifecycle> LIFECYCLE = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("key").forGetter(Lifecycle::key),
            Codec.intRange(0, 1_000_000).fieldOf("population").forGetter(Lifecycle::population),
            Codec.intRange(0, 1_000_000).fieldOf("capacity").forGetter(Lifecycle::capacity),
            Codec.intRange(0, 1_000_000).fieldOf("food").forGetter(Lifecycle::food),
            Codec.intRange(0, 1_000_000).fieldOf("materials").forGetter(Lifecycle::materials),
            Codec.intRange(-1_000_000, 1_000_000).fieldOf("reputation").forGetter(Lifecycle::reputation),
            Codec.LONG.fieldOf("last_tick").forGetter(Lifecycle::lastTick),
            PROJECT.listOf().optionalFieldOf("projects", List.of()).forGetter(Lifecycle::projects)
    ).apply(i, Lifecycle::new));
    private static final Codec<FabricSettlementLifecycleState> CODEC = RecordCodecBuilder.create(i -> i.group(
            LIFECYCLE.listOf().optionalFieldOf("settlements", List.of()).forGetter(state -> List.copyOf(state.values.values()))
    ).apply(i, FabricSettlementLifecycleState::new));
    private static final SavedDataType<FabricSettlementLifecycleState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("millenaire", "settlement_lifecycle"),
            FabricSettlementLifecycleState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Lifecycle> values = new LinkedHashMap<>();

    public FabricSettlementLifecycleState() {}
    FabricSettlementLifecycleState(List<Lifecycle> lifecycles) {
        for (Lifecycle lifecycle : lifecycles) {
            if (values.putIfAbsent(lifecycle.key(), lifecycle) != null)
                throw new IllegalArgumentException("Duplicate settlement lifecycle key: " + lifecycle.key());
        }
    }

    public static FabricSettlementLifecycleState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    static Codec<FabricSettlementLifecycleState> codec() { return CODEC; }
    public List<Lifecycle> lifecycles() { return List.copyOf(values.values()); }
    public Optional<Lifecycle> find(String key) { return Optional.ofNullable(values.get(key)); }

    public Lifecycle ensure(FabricSettlementState.Settlement settlement) {
        Objects.requireNonNull(settlement);
        String key = key(settlement);
        Lifecycle current = values.get(key);
        if (current != null) return current;
        Lifecycle created = new Lifecycle(key, 0, Math.max(1, settlement.buildings().size() * 4), 0, 0, 0, 0, List.of());
        values.put(key, created);
        setDirty();
        return created;
    }

    public void addResources(String key, int food, int materials) {
        Lifecycle current = require(key);
        replace(current.withResources(Math.addExact(current.food(), food), Math.addExact(current.materials(), materials)));
    }

    public void setPopulation(String key, int population) {
        Lifecycle current = require(key);
        if (population < 0 || population > current.capacity()) throw new IllegalArgumentException("Population exceeds capacity");
        replace(current.withPopulation(population));
    }

    public void queue(String key, String projectId, String plan, int requiredWork, int materialCost) {
        Lifecycle current = require(key);
        Project project = new Project(projectId, plan, requiredWork, materialCost, 0, Project.Status.QUEUED);
        if (current.projects().stream().anyMatch(existing -> existing.id().equals(projectId)))
            throw new IllegalArgumentException("Project already exists: " + projectId);
        List<Project> projects = new ArrayList<>(current.projects());
        projects.add(project);
        replace(current.withProjects(projects));
    }

    /** Advances at most one work unit per settlement tick and starts only affordable projects. */
    public void tick() {
        boolean changed = false;
        for (Lifecycle current : List.copyOf(values.values())) {
            List<Project> projects = new ArrayList<>(current.projects());
            int active = -1;
            for (int index = 0; index < projects.size(); index++) {
                Project project = projects.get(index);
                if (project.status() == Project.Status.COMPLETE) continue;
                if (project.status() == Project.Status.QUEUED) {
                    if (current.materials() < project.materialCost()) break;
                    projects.set(index, project.withStatus(Project.Status.ACTIVE));
                    active = index;
                    current = current.withResources(current.food(), current.materials() - project.materialCost());
                    changed = true;
                } else active = index;
                break;
            }
            if (active >= 0) {
                Project project = projects.get(active);
                if (project.status() == Project.Status.ACTIVE) {
                    int progress = project.progress() + 1;
                    projects.set(active, progress >= project.requiredWork()
                            ? project.withProgress(project.requiredWork()).withStatus(Project.Status.COMPLETE)
                            : project.withProgress(progress));
                    changed = true;
                }
            }
            Lifecycle updated = current.withProjects(projects).withLastTick(current.lastTick() + 1);
            if (!updated.equals(values.get(updated.key()))) { values.put(updated.key(), updated); changed = true; }
        }
        if (changed) setDirty();
    }

    public static String key(FabricSettlementState.Settlement settlement) {
        return settlement.dimension() + "@" + settlement.origin().x() + "," + settlement.origin().y() + "," + settlement.origin().z();
    }

    private Lifecycle require(String key) { Lifecycle lifecycle = values.get(key); if (lifecycle == null) throw new IllegalArgumentException("Unknown settlement: " + key); return lifecycle; }
    private void replace(Lifecycle lifecycle) { values.put(lifecycle.key(), lifecycle); setDirty(); }

    public record Lifecycle(String key, int population, int capacity, int food, int materials, int reputation,
                            long lastTick, List<Project> projects) {
        public Lifecycle {
            if (key == null || key.isBlank() || population < 0 || capacity < 1 || population > capacity || food < 0 || materials < 0)
                throw new IllegalArgumentException("Invalid settlement lifecycle");
            projects = List.copyOf(projects);
        }
        Lifecycle withResources(int food, int materials) { return new Lifecycle(key, population, capacity, food, materials, reputation, lastTick, projects); }
        Lifecycle withPopulation(int population) { return new Lifecycle(key, population, capacity, food, materials, reputation, lastTick, projects); }
        Lifecycle withProjects(List<Project> projects) { return new Lifecycle(key, population, capacity, food, materials, reputation, lastTick, projects); }
        Lifecycle withLastTick(long tick) { return new Lifecycle(key, population, capacity, food, materials, reputation, tick, projects); }
    }

    public record Project(String id, String plan, int requiredWork, int materialCost, int progress, Status status) {
        public Project {
            if (id == null || id.isBlank() || plan == null || plan.isBlank() || requiredWork < 1 || materialCost < 0
                    || progress < 0 || progress > requiredWork || status == null) throw new IllegalArgumentException("Invalid settlement project");
        }
        Project withStatus(Status status) { return new Project(id, plan, requiredWork, materialCost, progress, status); }
        Project withProgress(int progress) { return new Project(id, plan, requiredWork, materialCost, progress, status); }
        public enum Status { QUEUED, ACTIVE, COMPLETE }
    }
}
