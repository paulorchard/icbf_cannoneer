package com.islandcraft.init;

import com.islandcraft.IslandCraftMod;
import com.islandcraft.item.IronCannonItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            IslandCraftMod.MODID);

    public static final RegistryObject<Item> ICBF_IRON_CANNON = ITEMS.register("icbf_iron_cannon",
            () -> new IronCannonItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
