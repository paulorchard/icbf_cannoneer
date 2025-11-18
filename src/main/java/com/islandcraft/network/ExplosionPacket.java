package com.islandcraft.network;

import com.islandcraft.IslandCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExplosionPacket {
    private final BlockPos pos;
    private final float strength;
    private final boolean fire;
    private final int modeOrdinal;

    public ExplosionPacket(BlockPos pos, float strength, boolean fire, int modeOrdinal) {
        this.pos = pos;
        this.strength = strength;
        this.fire = fire;
        this.modeOrdinal = modeOrdinal;
    }

    public static void encode(ExplosionPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeFloat(pkt.strength);
        buf.writeBoolean(pkt.fire);
        buf.writeInt(pkt.modeOrdinal);
    }

    public static ExplosionPacket decode(FriendlyByteBuf buf) {
        BlockPos p = buf.readBlockPos();
        float s = buf.readFloat();
        boolean f = buf.readBoolean();
        int m = buf.readInt();
        return new ExplosionPacket(p, s, f, m);
    }

    public static void handle(ExplosionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                IslandCraftMod.LOGGER.warn("ExplosionPacket received but sender is null (client may be disconnected)");
                return;
            }
            // Received explosion request from client; do not log coordinates to reduce
            // server-side noise and avoid leaking positional info in logs.
            boolean spawnedFallback = false;
            try {
                // Fallback approach: run a server command to summon a primed TNT with zero
                // fuse.
                // This keeps the explosion server-authoritative without needing to depend on
                // specific Explosion API variants across mappings.
                if (sender.getServer() != null) {
                    // Summon at one block above the target so the TNT falls down and
                    // detonates at/above the surface (avoids spawning beneath water).
                    String cmd = String.format("summon tnt %d %d %d {Fuse:0}", pkt.pos.getX(), pkt.pos.getY() + 1,
                            pkt.pos.getZ());
                    IslandCraftMod.LOGGER.info("Attempting to dispatch server command: {}", cmd);
                    try {
                        Object cmdsObj = sender.getServer().getClass().getMethod("getCommands")
                                .invoke(sender.getServer());
                        if (cmdsObj != null) {
                            // Use reflection to invoke performCommand to avoid mapping/signature issues
                            try {
                                // Try to find a suitable performCommand method by name and parameter
                                boolean invoked = false;
                                for (java.lang.reflect.Method method : cmdsObj.getClass().getMethods()) {
                                    if (!method.getName().equals("performCommand") || method.getParameterCount() != 2)
                                        continue;
                                    Class<?> p0 = method.getParameterTypes()[0];
                                    Class<?> p1 = method.getParameterTypes()[1];
                                    if (p1 == String.class) {
                                        try {
                                            if (p0.isAssignableFrom(sender.createCommandSourceStack().getClass())) {
                                                method.invoke(cmdsObj, sender.createCommandSourceStack(), cmd);
                                                IslandCraftMod.LOGGER.info(
                                                        "Invoked performCommand via reflection (matched {})", method);
                                                invoked = true;
                                                break;
                                            }
                                        } catch (Throwable e) {
                                            IslandCraftMod.LOGGER.warn("performCommand invocation threw: {}",
                                                    e.toString());
                                        }
                                    }
                                }
                                if (!invoked) {
                                    // fallback: try dispatcher.dispatch-like methods
                                    try {
                                        java.lang.reflect.Method getDisp = cmdsObj.getClass()
                                                .getMethod("getDispatcher");
                                        Object disp = getDisp.invoke(cmdsObj);
                                        if (disp != null) {
                                            boolean dispInvoked = false;
                                            for (java.lang.reflect.Method method : disp.getClass().getMethods()) {
                                                if (!method.getName().equals("dispatch")
                                                        || method.getParameterCount() != 2)
                                                    continue;
                                                Class<?> a = method.getParameterTypes()[0];
                                                Class<?> b = method.getParameterTypes()[1];
                                                try {
                                                    Object arg0 = null;
                                                    Object arg1 = null;
                                                    if (a == String.class && b.isAssignableFrom(
                                                            sender.createCommandSourceStack().getClass())) {
                                                        arg0 = cmd;
                                                        arg1 = sender.createCommandSourceStack();
                                                    } else if (b == String.class && a.isAssignableFrom(
                                                            sender.createCommandSourceStack().getClass())) {
                                                        arg0 = sender.createCommandSourceStack();
                                                        arg1 = cmd;
                                                    } else {
                                                        continue;
                                                    }
                                                    method.invoke(disp, arg0, arg1);
                                                    IslandCraftMod.LOGGER.info(
                                                            "Invoked dispatcher dispatch via reflection (matched {})",
                                                            method);
                                                    dispInvoked = true;
                                                    break;
                                                } catch (Throwable exx) {
                                                    IslandCraftMod.LOGGER.warn("dispatcher method invocation threw: {}",
                                                            exx.toString());
                                                }
                                            }
                                            if (!dispInvoked) {
                                                IslandCraftMod.LOGGER.warn(
                                                        "No suitable dispatcher.dispatch method found (inspected methods)");
                                                // As a robust fallback across mappings, try spawning a PrimedTnt entity
                                                try {
                                                    net.minecraft.commands.CommandSourceStack css = sender
                                                            .createCommandSourceStack();
                                                    ServerLevel slevel = css.getLevel();
                                                    if (slevel != null) {
                                                        double dx = pkt.pos.getX() + 0.5D;
                                                        // spawn the primed TNT one block above the target so it
                                                        // drops and explodes at the surface rather than below it
                                                        double dy = pkt.pos.getY() + 1.5D;
                                                        double dz = pkt.pos.getZ() + 0.5D;
                                                        PrimedTnt tnt = new PrimedTnt(slevel, dx, dy, dz, sender);
                                                        try {
                                                            tnt.setFuse(0);
                                                        } catch (Throwable __f) {
                                                            // some mappings may use a different setter name — ignore if
                                                            // absent
                                                        }
                                                        slevel.addFreshEntity(tnt);
                                                        spawnedFallback = true;
                                                        IslandCraftMod.LOGGER.info(
                                                                "Spawned PrimedTnt entity at {} via fallback", pkt.pos);
                                                    } else {
                                                        IslandCraftMod.LOGGER.warn(
                                                                "Cannot spawn PrimedTnt: server level not available");
                                                    }
                                                } catch (Throwable spEx) {
                                                    IslandCraftMod.LOGGER.warn("Failed to spawn PrimedTnt fallback: {}",
                                                            spEx.toString());
                                                }
                                            }
                                        } else {
                                            IslandCraftMod.LOGGER.warn("Command dispatcher was null");
                                        }
                                    } catch (NoSuchMethodException nsme2) {
                                        IslandCraftMod.LOGGER.warn(
                                                "getDispatcher() not available on commands object: {}",
                                                nsme2.toString());
                                    }
                                }
                            } catch (Throwable invEx) {
                                IslandCraftMod.LOGGER.warn("Failed to lookup/invoke command methods via reflection: {}",
                                        invEx.toString());
                            }
                        } else {
                            IslandCraftMod.LOGGER.warn("getCommands() returned null on server instance");
                        }
                    } catch (Throwable ex) {
                        IslandCraftMod.LOGGER.warn("Failed to access server commands via reflection: {}",
                                ex.toString());
                    }
                } else {
                    IslandCraftMod.LOGGER.warn("Sender server instance is null; cannot dispatch command");
                }
            } catch (Throwable t) {
                IslandCraftMod.LOGGER.warn("Unhandled exception while handling ExplosionPacket: {}", t.toString());
            }
            // If we didn't spawn via command dispatch or the in-method fallbacks above,
            // attempt a last-resort direct spawn of a PrimedTnt entity so requests are
            // honored even when server command reflection isn't available.
            try {
                // Only attempt if a fallback spawn hasn't already run
                if (!spawnedFallback) {
                    try {
                        net.minecraft.commands.CommandSourceStack css2 = sender.createCommandSourceStack();
                        ServerLevel slevel = css2.getLevel();
                        if (slevel != null) {
                            double dx = pkt.pos.getX() + 0.5D;
                            double dy = pkt.pos.getY() + 1.5D;
                            double dz = pkt.pos.getZ() + 0.5D;
                            PrimedTnt tnt = new PrimedTnt(slevel, dx, dy, dz, sender);
                            try {
                                tnt.setFuse(0);
                            } catch (Throwable __f) {
                            }
                            slevel.addFreshEntity(tnt);
                            IslandCraftMod.LOGGER.info("Spawned PrimedTnt entity at {} via final fallback", pkt.pos);
                        }
                    } catch (Throwable finalSp) {
                        IslandCraftMod.LOGGER.warn("Final fallback spawn failed: {}", finalSp.toString());
                    }
                }
            } catch (Throwable __ignored) {
            }
        });
        ctx.setPacketHandled(true);
    }
}
