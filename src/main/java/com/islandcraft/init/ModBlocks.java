package com.islandcraft.init;

import com.islandcraft.IslandCraftMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            IslandCraftMod.MODID);

    public static final RegistryObject<Block> ICBF_CANNON_BLOCK = BLOCKS.register("icbf_cannon_block",
            () -> new com.islandcraft.init.CannonBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
