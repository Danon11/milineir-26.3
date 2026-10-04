package org.millenaire.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.fabric.crops.MaizeCropBlock;
import org.millenaire.fabric.crops.MaizeSeedsItem;
import org.millenaire.fabric.crops.GenericCropBlock;
import org.millenaire.fabric.crops.GrapeVineBlock;
import org.millenaire.fabric.crops.FruitLeavesBlock;
import org.millenaire.fabric.firepit.FirePitContent;
import org.millenaire.fabric.paint.PaintBucketItem;
import org.millenaire.fabric.paint.PaintableBlocks;
import org.millenaire.fabric.trees.LegacySaplingBlock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registers the manifest-backed content that does not yet have dedicated gameplay implementations. */
public final class LegacyContentRegistry {
    private static final String MOD_ID = "millenaire";
    private static final String BLOCK_MANIFEST = "/data/millenaire/legacy_blocks.txt";
    private static final String ITEM_MANIFEST = "/data/millenaire/legacy_items.txt";
    private static final String ID_MAP = "/data/millenaire/legacy_id_map.json";
    private static final String FIRE_PIT = "fire_pit";
    private static final List<String> PAINT_COLORS = List.of(
            "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "lime",
            "magenta", "orange", "pink", "purple", "red", "silver", "white", "yellow");
    private static final List<String> DERIVED_BLOCKS = derivedBlocks();
    private static final List<String> DERIVED_ITEMS = derivedItems();
    private static final Identifier CREATIVE_TAB_ID = Identifier.fromNamespaceAndPath(MOD_ID, "legacy_content");
    private static final Set<String> WALL_BLOCKS = Set.of(
            "wall_mud_brick",
            "wall_sandstone_carved",
            "wall_sandstone_ochre_carved",
            "wall_sandstone_red_carved");

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Item> BLOCK_ITEMS = new LinkedHashMap<>();
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final Map<String, LegacyOrigin> ORIGINS = new LinkedHashMap<>();
    private static boolean registered;

    private LegacyContentRegistry() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        List<String> blockNames = readManifest(BLOCK_MANIFEST);
        List<String> itemNames = readManifest(ITEM_MANIFEST);
        Set<String> manifestBlocks = Set.copyOf(blockNames);
        Set<String> manifestItems = Set.copyOf(itemNames);
        for (String name : DERIVED_BLOCKS) {
            id(name);
            if (manifestBlocks.contains(name)) {
                throw new IllegalStateException("Derived block is already listed in the manifest: " + name);
            }
            if (manifestItems.contains(name)) {
                throw new IllegalStateException("Derived block is already listed as an item: " + name);
            }
        }
        for (String name : DERIVED_ITEMS) {
            id(name);
            if (manifestBlocks.contains(name) || manifestItems.contains(name)) {
                throw new IllegalStateException("Derived item is already present in a manifest: " + name);
            }
        }
        readOrigins(blockNames, itemNames, DERIVED_BLOCKS, DERIVED_ITEMS);

        List<String> allBlockNames = new ArrayList<>(blockNames);
        allBlockNames.addAll(DERIVED_BLOCKS);
        for (String name : allBlockNames) {
            if (FIRE_PIT.equals(name)) {
                BLOCKS.put(name, FirePitContent.FIRE_PIT);
                BLOCK_ITEMS.put(name, FirePitContent.FIRE_PIT_ITEM);
                continue;
            }
            Identifier id = id(name);
            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
            Block block = Registry.register(BuiltInRegistries.BLOCK, id, createBlock(name, blockKey));
            BLOCKS.put(name, block);

            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
            Item blockItem = Registry.register(BuiltInRegistries.ITEM, id,
                    new BlockItem(block, new Item.Properties().setId(itemKey)));
            BLOCK_ITEMS.put(name, blockItem);
        }

        List<String> allItemNames = new ArrayList<>(itemNames);
        allItemNames.addAll(DERIVED_ITEMS);
        for (String name : allItemNames) {
            Identifier id = id(name);
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
            Item.Properties properties = new Item.Properties().setId(itemKey);
            if (name.startsWith("paint_bucket_") || name.equals("paintbucketwhite")) {
                properties.stacksTo(1).durability(2048);
            }
            Item item;
            if (name.equals("maize") || name.equals("cotton") || name.equals("rice") || name.equals("turmeric") || name.equals("grapes")) {
                String cropName = name.equals("maize") ? "crop_maize" : (name.equals("grapes") ? "crop_vine" : "crop_" + name);
                Block cropBlock = BLOCKS.get(cropName);
                if (!(cropBlock instanceof net.minecraft.world.level.block.CropBlock crop)) {
                    throw new IllegalStateException("The crop block must be registered before its seed item: " + cropName);
                }
                item = new MaizeSeedsItem(crop, properties);
            } else if (name.startsWith("paint_bucket_") || name.equals("paintbucketwhite")) {
                item = PaintBucketItem.createForName(name, properties);
            } else if (org.millenaire.fabric.equipment.LegacyTools.all().containsKey(name)) {
                item = new Item(org.millenaire.fabric.equipment.LegacyTools.configure(name, properties));
            } else if (org.millenaire.fabric.equipment.LegacyWeapons.all().containsKey(name)) {
                item = new Item(org.millenaire.fabric.equipment.LegacyWeapons.configure(name, properties));
            } else if (org.millenaire.fabric.equipment.LegacyBows.all().containsKey(name)) {
                item = org.millenaire.fabric.equipment.LegacyBows.create(name, properties);
            } else if (org.millenaire.fabric.equipment.LegacyArmor.all().containsKey(name)) {
                item = new Item(org.millenaire.fabric.equipment.LegacyArmor.configure(name, properties));
            } else if (org.millenaire.fabric.equipment.LegacySpecialItems.supports(name)) {
                item = new Item(org.millenaire.fabric.equipment.LegacySpecialItems.configure(name, properties));
            } else {
                item = new Item(properties);
            }
            Registry.register(BuiltInRegistries.ITEM, id, item);
            ITEMS.put(name, item);
        }

        registerCreativeTab();
        registered = true;
    }

    public static Map<String, Block> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    public static Map<String, Item> blockItems() {
        return Collections.unmodifiableMap(BLOCK_ITEMS);
    }

    /** Contains standalone items, including recipe-derived entries; block items are available through {@link #blockItems()}. */
    public static Map<String, Item> items() {
        return Collections.unmodifiableMap(ITEMS);
    }

    public static Map<String, LegacyOrigin> origins() {
        return Collections.unmodifiableMap(ORIGINS);
    }

    public static Block block(String name) {
        return BLOCKS.get(name);
    }

    /** Looks up either a manifest block item or a standalone manifest item. */
    public static Item item(String name) {
        Item blockItem = BLOCK_ITEMS.get(name);
        return blockItem != null ? blockItem : ITEMS.get(name);
    }

    public static LegacyOrigin origin(String name) {
        return ORIGINS.get(name);
    }

    public static int blockCount() {
        return BLOCKS.size();
    }

    public static int manifestBlockCount() {
        return registered ? BLOCKS.size() - DERIVED_BLOCKS.size() : 0;
    }

    public static int blockItemCount() {
        return BLOCK_ITEMS.size();
    }

    public static int derivedBlockCount() {
        return registered ? DERIVED_BLOCKS.size() : 0;
    }

    public static int derivedItemCount() {
        return registered ? DERIVED_ITEMS.size() : 0;
    }

    public static int standaloneItemCount() {
        return ITEMS.size();
    }

    public static int itemRegistryCount() {
        return BLOCK_ITEMS.size() + ITEMS.size();
    }

    private static Block createBlock(String name, ResourceKey<Block> key) {
        BlockBehaviour.Properties properties = blockProperties(name, key);
        if (name.equals("locked_chest") || name.equals("mainchest")) {
            // 1.12 setResistance(2000) gives an effective blast resistance of 1200.
            // The old mainchest content-creator marker was unbreakable in survival.
            return new org.millenaire.fabric.storage.VillageChestBlock(properties
                    .strength(name.equals("locked_chest") ? 50.0F : -1.0F, name.equals("locked_chest") ? 1200.0F : 0.0F)
                    .sound(SoundType.WOOD).noOcclusion());
        }
        if (name.equals("panel")) {
            return new org.millenaire.fabric.storage.VillagePanelBlock(properties.strength(1.0F).sound(SoundType.WOOD).noCollision().noOcclusion());
        }
        if (Set.of("pathdirt", "pathgravel", "pathslabs", "pathsandstone", "pathgravelslabs",
                "pathdirt_slab", "pathgravel_slab", "pathslabs_slab", "pathsandstone_slab", "pathgravelslabs_slab").contains(name)) {
            return org.millenaire.fabric.content.LegacyPathBlocks.create(name.endsWith("_slab"), properties.strength(0.8F));
        }
        if (name.equals("crop_maize")) {
            return new MaizeCropBlock(properties.noCollision().instabreak().sound(SoundType.CROP).randomTicks());
        }
        if (name.equals("crop_cotton")) {
            return new GenericCropBlock(properties.noCollision().instabreak().sound(SoundType.CROP).randomTicks(), "cotton", true, false);
        }
        if (name.equals("crop_rice")) {
            return new GenericCropBlock(properties.noCollision().instabreak().sound(SoundType.CROP).randomTicks(), "rice", true, false);
        }
        if (name.equals("crop_turmeric")) {
            return new GenericCropBlock(properties.noCollision().instabreak().sound(SoundType.CROP).randomTicks(), "turmeric", false, false);
        }
        if (name.equals("crop_vine")) {
            return new GrapeVineBlock(properties.noCollision().noOcclusion().instabreak().sound(SoundType.CROP).randomTicks());
        }
        if (name.equals("silk_worm")) {
            return new org.millenaire.fabric.content.LegacyProgressBlock(properties.noOcclusion().strength(2.0F).sound(SoundType.WOOD),
                    org.millenaire.fabric.content.LegacyProgressBlock.Kind.SILKWORM);
        }
        if (name.equals("snail_soil")) {
            return new org.millenaire.fabric.content.LegacyProgressBlock(properties.strength(0.6F).sound(SoundType.GRAVEL),
                    org.millenaire.fabric.content.LegacyProgressBlock.Kind.SNAIL_SOIL);
        }
        if (name.startsWith("sapling_")) {
            return new LegacySaplingBlock(name, properties.noCollision().randomTicks().strength(0.0F).sound(SoundType.GRASS));
        }
        String fruit = switch (name) {
            case "leaves_appletree" -> "ciderapple";
            case "leaves_olivetree" -> "olives";
            case "leaves_pistachio" -> "pistachios";
            case "cherry_leaves" -> "cherries";
            case "sakura_leaves" -> "cherry_blossom";
            default -> null;
        };
        if (fruit != null) {
            return new FruitLeavesBlock(properties.noOcclusion().randomTicks().strength(0.2F).sound(SoundType.GRASS),
                    () -> ITEMS.get(fruit));
        }
        if (isPaintedBlockName(name)) {
            return PaintableBlocks.create(name, Blocks.STONE.defaultBlockState(), properties);
        }
        if (name.equals("bed_straw") || name.equals("bed_charpoy")) {
            return new org.millenaire.fabric.content.LegacyDecorBlocks.LegacyBedBlock(properties.strength(0.2F).sound(SoundType.WOOD).noOcclusion());
        }
        if (name.equals("byzantine_tiles_slab")) {
            return new org.millenaire.fabric.content.LegacyDecorBlocks.AxisSlabBlock(properties);
        }
        if (Set.of("byzantine_tiles", "byzantine_stone_tiles", "byzantine_sandstone_tiles").contains(name)) {
            return new org.millenaire.fabric.content.LegacyDecorBlocks.AxisBlock(properties);
        }
        if (name.equals("inuitcarving")) {
            return new org.millenaire.fabric.content.LegacyDecorBlocks.FacingBlock(properties.noOcclusion());
        }
        if (name.equals("wooden_bars_rosette") || name.equals("woodenbarsrosette")) {
            return new org.millenaire.fabric.content.LegacyDecorBlocks.RosetteBarsBlock(properties.noOcclusion());
        }
        if (name.startsWith("slab_") || name.endsWith("_slab")) {
            return new SlabBlock(properties);
        }
        if (name.startsWith("stairs_") || name.endsWith("_stairs")) {
            return new LegacyStairBlock(Blocks.STONE.defaultBlockState(), properties);
        }
        if (WALL_BLOCKS.contains(name) || name.startsWith("wall_painted_brick_")) {
            return new WallBlock(properties);
        }
        if (name.startsWith("wooden_bars") || name.startsWith("woodenbars")) {
            return new LegacyBarsBlock(properties.noOcclusion());
        }
        return new Block(properties);
    }

    private static boolean isPaintedBlockName(String name) {
        return name.startsWith("painted_brick_")
                || name.startsWith("slab_painted_brick_")
                || name.startsWith("stairs_painted_brick_")
                || name.startsWith("wall_painted_brick_");
    }

    private static BlockBehaviour.Properties blockProperties(String name, ResourceKey<Block> key) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().setId(key);
        if (name.startsWith("leaves_") || name.endsWith("_leaves")) {
            return properties.strength(0.2F).sound(SoundType.WOOD).noOcclusion();
        }
        if (name.startsWith("sapling_")) {
            return properties.strength(0.0F).sound(SoundType.WOOD).noOcclusion();
        }
        if (name.startsWith("wood_") || name.startsWith("timberframe") || name.startsWith("stairs_thatch")) {
            return properties.strength(2.0F).sound(SoundType.WOOD);
        }
        return properties.strength(2.0F, 3.0F).sound(SoundType.STONE);
    }

    private static void registerCreativeTab() {
        Item icon = ITEMS.getOrDefault("denier", BLOCK_ITEMS.values().stream().findFirst().orElseThrow());
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 0)
                .title(Component.translatable("itemGroup.millenaire"))
                .icon(() -> new ItemStack(icon))
                .displayItems((parameters, output) -> {
                    BLOCK_ITEMS.values().forEach(output::accept);
                    ITEMS.values().forEach(output::accept);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATIVE_TAB_ID, tab);
    }

    private static List<String> readManifest(String path) {
        try (InputStream stream = resource(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> names = new ArrayList<>();
            Set<String> uniqueNames = new LinkedHashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                id(name);
                if (!uniqueNames.add(name)) {
                    throw new IllegalStateException("Duplicate entry '" + name + "' in " + path);
                }
                names.add(name);
            }
            return List.copyOf(names);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read legacy manifest " + path, exception);
        }
    }

    private static List<String> derivedBlocks() {
        List<String> names = new ArrayList<>(List.of(
                "slab_mudbrick",
                "slab_cookedbrick",
                "slab_timberframeplain",
                "slab_thatch"));
        for (String color : PAINT_COLORS) {
            if (!color.equals("white")) {
                names.add("painted_brick_" + color);
                names.add("painted_brick_decorated_" + color);
            }
            names.add("slab_painted_brick_" + color);
            names.add("stairs_painted_brick_" + color);
            names.add("wall_painted_brick_" + color);
        }
        return List.copyOf(names);
    }

    private static List<String> derivedItems() {
        List<String> names = new ArrayList<>(List.of("paintbucketwhite"));
        for (String color : PAINT_COLORS) {
            if (!color.equals("white")) {
                names.add("paint_bucket_" + color);
            }
        }
        return List.copyOf(names);
    }

    private static void readOrigins(List<String> blockNames, List<String> itemNames,
                                    List<String> derivedBlocks, List<String> derivedItems) {
        Set<String> blocks = Set.copyOf(blockNames);
        Set<String> items = Set.copyOf(itemNames);
        Set<String> derivedBlockSet = Set.copyOf(derivedBlocks);
        Set<String> derivedItemSet = Set.copyOf(derivedItems);
        try (InputStream stream = resource(ID_MAP);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonArray entries = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : entries) {
                JsonObject object = element.getAsJsonObject();
                String name = object.get("name").getAsString();
                String kind = object.get("kind").getAsString();
                String legacyId = object.get("legacy_id").getAsString();
                int metadata = Integer.parseInt(object.get("metadata").getAsString());
                if (!(kind.equals("block") || kind.equals("item"))) {
                    throw new IllegalStateException("Unknown legacy kind '" + kind + "' for " + name);
                }
                if (((blocks.contains(name) || derivedBlockSet.contains(name)) && !kind.equals("block"))
                        || ((items.contains(name) || derivedItemSet.contains(name)) && !kind.equals("item"))) {
                    throw new IllegalStateException("Legacy kind does not match manifest for " + name);
                }
                LegacyOrigin origin = new LegacyOrigin(name, kind, validateLegacyId(legacyId), metadata, true,
                        derivedBlockSet.contains(name) || derivedItemSet.contains(name));
                if (ORIGINS.putIfAbsent(name, origin) != null) {
                    throw new IllegalStateException("Duplicate legacy mapping for " + name);
                }
            }
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Could not read legacy ID map " + ID_MAP, exception);
        }

        for (String name : blockNames) {
            ORIGINS.putIfAbsent(name, new LegacyOrigin(name, "block", id(name).toString(), 0, false));
        }
        for (String name : itemNames) {
            ORIGINS.putIfAbsent(name, new LegacyOrigin(name, "item", id(name).toString(), 0, false));
        }
        for (String name : derivedBlocks) {
            ORIGINS.putIfAbsent(name, new LegacyOrigin(name, "block", id(name).toString(), 0, false, true));
        }
        for (String name : derivedItems) {
            String legacyId = name.equals("paintbucketwhite") ? "millenaire:paint_bucket_white" : id(name).toString();
            ORIGINS.putIfAbsent(name, new LegacyOrigin(name, "item", legacyId, 0, false, true));
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = LegacyContentRegistry.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing legacy content resource " + path);
        }
        return stream;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static String validateLegacyId(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalStateException("Expected a namespaced legacy ID, got '" + value + "'");
        }
        Identifier.fromNamespaceAndPath(value.substring(0, separator).toLowerCase(Locale.ROOT),
                value.substring(separator + 1).toLowerCase(Locale.ROOT));
        return value;
    }

    public record LegacyOrigin(String name, String kind, String legacyId, int metadata, boolean mapped,
                               boolean derived) {
        public LegacyOrigin(String name, String kind, String legacyId, int metadata, boolean mapped) {
            this(name, kind, legacyId, metadata, mapped, false);
        }
    }

    private static final class LegacyStairBlock extends StairBlock {
        private LegacyStairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
            super(baseState, properties);
        }
    }

    private static final class LegacyBarsBlock extends IronBarsBlock {
        private LegacyBarsBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }
    }

}
