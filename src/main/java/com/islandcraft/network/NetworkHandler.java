package com.islandcraft.network;

import com.islandcraft.IslandCraftMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    public static void register() {
        ResourceLocation name = new ResourceLocation(IslandCraftMod.MODID, "network");
        CHANNEL = NetworkRegistry.newSimpleChannel(name, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        int id = 0;
        CHANNEL.registerMessage(id++, ExplosionPacket.class, ExplosionPacket::encode, ExplosionPacket::decode,
            ExplosionPacket::handle);
        CHANNEL.registerMessage(id++, ExplosionNotifyPacket.class, ExplosionNotifyPacket::encode,
            ExplosionNotifyPacket::decode, ExplosionNotifyPacket::handle);
        IslandCraftMod.LOGGER.info("Network channel registered");
    }
}
