package org.millenaire.fabric.firepit;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

public final class FirePitContent {
    public static final Identifier BLOCK_ID = Identifier.fromNamespaceAndPath("millenaire", "fire_pit");
    public static final Identifier MENU_ID = Identifier.fromNamespaceAndPath("millenaire", "fire_pit");

    public static Block FIRE_PIT;
    public static Item FIRE_PIT_ITEM;
    public static BlockEntityType<FirePitBlockEntity> BLOCK_ENTITY_TYPE;
    public static ExtendedMenuType<FirePitMenu, BlockPos> MENU_TYPE;

    private FirePitContent() {
    }

    public static void registerCommon() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, BLOCK_ID);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BLOCK_ID);

        FIRE_PIT = Registry.register(BuiltInRegistries.BLOCK, BLOCK_ID,
                new FirePitBlock(BlockBehaviour.Properties.of()
                        .setId(blockKey)
                        .strength(0.2F)
                        .sound(SoundType.WOOD)
                        .noOcclusion()
                        .lightLevel(state -> state.getValue(FirePitBlock.LIT) ? 15 : 0)));
        FIRE_PIT_ITEM = Registry.register(BuiltInRegistries.ITEM, BLOCK_ID,
                new BlockItem(FIRE_PIT, new Item.Properties().setId(itemKey)));
        BLOCK_ENTITY_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, BLOCK_ID,
                new BlockEntityType<>(FirePitBlockEntity::new, Set.of(FIRE_PIT)));
        MENU_TYPE = Registry.register(BuiltInRegistries.MENU, MENU_ID,
                new ExtendedMenuType<>((containerId, inventory, pos) -> new FirePitMenu(containerId, inventory, pos),
                        BlockPos.STREAM_CODEC));
    }
}
