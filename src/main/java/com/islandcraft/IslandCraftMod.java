package com.islandcraft;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.islandcraft.client.ClientConfig;
import com.islandcraft.network.NetworkHandler;

@Mod(IslandCraftMod.MODID)
public class IslandCraftMod {
    public static final String MODID = "icbf_cannoneer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IslandCraftMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        // Register client config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Initialize network channel
        try {
            NetworkHandler.register();
        } catch (Throwable t) {
            LOGGER.warn("Failed to register network handler: {}", t.toString());
        }
    }
}
