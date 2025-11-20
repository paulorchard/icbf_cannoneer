package com.islandcraft.init;

import com.islandcraft.IslandCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CannonBreakHandler {

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        Level level = (Level) event.getLevel();
        if (level == null || level.isClientSide())
            return;
        BlockPos pos = event.getPos();

        BlockPos master = ModCannonManager.getMasterForMember(pos);
        if (master == null)
            return;

        // This is a registered cannon member — intercept the break to handle unified
        // teardown/drop.
        // Cancel the default break behavior immediately to prevent vanilla drops/XP.
        try {
            event.setCanceled(true);
        } catch (Throwable ignored) {
        }

        // Harvesting rules:
        // - Creative: remove all parts, no drop
        // - Player with pickaxe of tier > IRON (diamond/netherite): damage tool, remove
        // all parts, spawn single cannon item
        // - Otherwise: remove all parts, no drop
        try {
            IslandCraftMod.LOGGER.info("Cannon part broken at {} — harvesting/tearing down cannon at {}", pos, master);

            // Determine player and held item
            net.minecraft.world.entity.player.Player player;
            try {
                player = event.getPlayer();
            } catch (Throwable __) {
                player = null;
            }

            boolean spawnDrop = false;

            if (player != null && player.isCreative()) {
                spawnDrop = false;
            } else if (player != null) {
                net.minecraft.world.item.ItemStack held = player.getMainHandItem();
                if (held != null && held.getItem() instanceof TieredItem) {
                    try {
                        TieredItem ti = (TieredItem) held.getItem();
                        int tierLevel = ti.getTier().getLevel();
                        // iron level is 2; require greater than iron
                        if (tierLevel > 2) {
                            spawnDrop = true;
                            final net.minecraft.world.InteractionHand hand = player.getUsedItemHand();
                            try {
                                held.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
                            } catch (Throwable ignored) {
                            }
                        } else {
                            spawnDrop = false;
                        }
                    } catch (Throwable ignored) {
                        spawnDrop = false;
                    }
                } else {
                    spawnDrop = false;
                }
            }

            IslandCraftMod.LOGGER.info("Cannon harvest decision at {} by {}: spawnDrop={}", pos,
                    player == null ? "<unknown>" : player.getName().getString(), spawnDrop);

            // Remove all registered parts by replacing them with air (no drops)
            java.util.Set<BlockPos> members = ModCannonManager.getMembersForMaster(master);
            for (BlockPos member : members) {
                try {
                    level.setBlock(member, Blocks.AIR.defaultBlockState(), 3);
                } catch (Throwable ignored) {
                }
            }

            // spawn controlled drop if appropriate
            if (spawnDrop && level instanceof ServerLevel) {
                try {
                    IslandCraftMod.LOGGER.info("Spawning icbf_iron_cannon drop at {}", master);
                    net.minecraft.world.item.ItemStack drop = new net.minecraft.world.item.ItemStack(
                            com.islandcraft.init.ModItems.ICBF_IRON_CANNON.get());
                    net.minecraft.world.entity.item.ItemEntity ent = new net.minecraft.world.entity.item.ItemEntity(
                            (ServerLevel) level,
                            master.getX() + 0.5, master.getY() + 0.5, master.getZ() + 0.5, drop);
                    ((ServerLevel) level).addFreshEntity(ent);
                } catch (Throwable t) {
                    IslandCraftMod.LOGGER.warn("Failed to spawn icbf_iron_cannon drop: {}", t.toString());
                }
            } else {
                IslandCraftMod.LOGGER.info("No cannon drop spawned at {} (spawnDrop={})", master, spawnDrop);
            }

        } catch (Throwable t) {
            IslandCraftMod.LOGGER.warn("Error tearing down cannon: {}", t.toString());
        } finally {
            ModCannonManager.removeCannon(master);
        }
    }
}
