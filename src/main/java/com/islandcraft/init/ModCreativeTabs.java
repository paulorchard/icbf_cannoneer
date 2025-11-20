package com.islandcraft.init;

import com.islandcraft.IslandCraftMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.islandcraft.init.ModItems;

/**
 * Registers a custom creative tab for the mod. The tab uses the vanilla
 * spyglass
 * icon and the literal title "IslandCraft". This implementation performs a
 * direct registry registration during mod initialization which works reliably
 * for development and small mods. If you prefer the
 * DeferredRegister/RegisterEvent
 * pattern you can replace this with an event-driven registration.
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, IslandCraftMod.MODID);

    public static final RegistryObject<CreativeModeTab> ISLANDCRAFT_TAB = CREATIVE_TABS.register("islandcraft",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("IslandCraft"))
                    .icon(() -> new ItemStack(Items.SPYGLASS))
                    .displayItems((features, output) -> {
                        // Populate the tab with the vanilla spyglass for now
                        try {
                            output.accept(Items.SPYGLASS);
                        } catch (Throwable t) {
                            try {
                                output.accept(new ItemStack(Items.SPYGLASS));
                            } catch (Throwable __) {
                                com.islandcraft.IslandCraftMod.LOGGER.warn("Failed to add spyglass to creative tab: {}",
                                        t.toString());
                            }
                        }
                        // Add our iron cannon item if available
                        try {
                            output.accept(ModItems.ICBF_IRON_CANNON.get());
                        } catch (Throwable t) {
                            try {
                                output.accept(new ItemStack(ModItems.ICBF_IRON_CANNON.get()));
                            } catch (Throwable __) {
                                com.islandcraft.IslandCraftMod.LOGGER
                                        .info("icbf_iron_cannon not available for creative tab yet: {}", t.toString());
                            }
                        }
                    })
                    .build());

    public static void register(IEventBus bus) {
        CREATIVE_TABS.register(bus);
    }
}
