package com.islandcraft.init;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ModServerEvents {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent evt) {
        try {
            ModCannonManager.loadFromServer(evt.getServer());
        } catch (Throwable t) {
            System.err.println("Failed to load cannon saveddata on server start: " + t);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent evt) {
        try {
            ModCannonManager.saveToServer(evt.getServer());
        } catch (Throwable t) {
            System.err.println("Failed to save cannon saveddata on server stop: " + t);
        }
    }
}
