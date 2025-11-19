package com.islandcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExplosionNotifyPacket {
    private final BlockPos pos;
    private final float strength;
    private final boolean fire;

    public ExplosionNotifyPacket(BlockPos pos, float strength, boolean fire) {
        this.pos = pos;
        this.strength = strength;
        this.fire = fire;
    }

    public static void encode(ExplosionNotifyPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeFloat(pkt.strength);
        buf.writeBoolean(pkt.fire);
    }

    public static ExplosionNotifyPacket decode(FriendlyByteBuf buf) {
        BlockPos p = buf.readBlockPos();
        float s = buf.readFloat();
        boolean f = buf.readBoolean();
        return new ExplosionNotifyPacket(p, s, f);
    }

    public static void handle(ExplosionNotifyPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // Client-only packet: run on the client thread
        ctx.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null)
                    return;
                // Play a local explosion sound and spawn explosion particles at the target
                if (mc.level != null) {
                    double x = pkt.pos.getX() + 0.5D;
                    double y = pkt.pos.getY() + 0.5D;
                    double z = pkt.pos.getZ() + 0.5D;
                    // Explosion particle
                    mc.level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0.0D, 0.0D, 0.0D);
                    // Some smoke/large smoke
                    for (int i = 0; i < 8; i++) {
                        double dx = (mc.level.random.nextDouble() - 0.5D) * 0.5D;
                        double dy = (mc.level.random.nextDouble() - 0.5D) * 0.5D;
                        double dz = (mc.level.random.nextDouble() - 0.5D) * 0.5D;
                        mc.level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, dx, dy, dz);
                    }
                    // Play explosion sound for the local player at full volume so they notice it
                    mc.level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 1.0f, false);
                }
            } catch (Throwable t) {
                // swallow client-side exceptions to avoid crashing the game
            }
        });
        ctx.setPacketHandled(true);
    }
}
