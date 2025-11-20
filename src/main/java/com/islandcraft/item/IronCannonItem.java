package com.islandcraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;

public class IronCannonItem extends Item {
    public IronCannonItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (!(level instanceof ServerLevel))
            return InteractionResult.FAIL;

        BlockPos base = ctx.getClickedPos().relative(ctx.getClickedFace());
        Direction dir = player == null ? Direction.NORTH : player.getDirection();

        // Build target positions for a 1 (width) x 4 (length) x 2 (height) cannon.
        // bottom layer: indices 0..3 (rear..front) at base.y
        // top layer: indices 0..3 (above indices 0..3)
        java.util.List<BlockPos> bottom = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++)
            bottom.add(base.relative(dir, i));

        java.util.List<BlockPos> top = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++)
            top.add(base.relative(dir, i).above());

        // Check space for all member positions before placing anything
        BlockPlaceContext placeCtx = new BlockPlaceContext(ctx);
        // bottom[3] should be empty by design, so do not require it to be replaced (we
        // won't place there)
        for (int i = 0; i < 3; i++)
            if (!isReplaceable(level, bottom.get(i), placeCtx))
                return InteractionResult.FAIL;
        for (BlockPos p : top)
            if (!isReplaceable(level, p, placeCtx))
                return InteractionResult.FAIL;

        // Place blocks. We'll use vanilla blocks for visuals:
        // bottom[0] = dark oak stairs (rear), bottom[1..2] = dark oak planks, bottom[3]
        // = upside-down oak stair (front)
        // top[0..2] = iron blocks (covering positions 1..3 above)
        try {
            // rear stair (x0 y0) - rotate 180 degrees from previous orientation
            level.setBlock(bottom.get(0), Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, dir), 3);

            // mid bottom: x1 = plank
            level.setBlock(bottom.get(1), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);

            // bottom[3] (x3 y0) : intentionally leave empty (no block)

            // top iron blocks above indices 1..3 (x0 y1 should be empty per request)
            for (int i = 1; i < 4; i++)
                level.setBlock(top.get(i), Blocks.IRON_BLOCK.defaultBlockState(), 3);

            // x2 bottom: dark oak stair upside-down with tall face towards the player
            // (reverted)
            level.setBlock(bottom.get(2), Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, dir)
                    .setValue(StairBlock.HALF, Half.TOP), 3);

            // Register all member positions with ModCannonManager (master = rear)
            try {
                java.util.Set<BlockPos> members = new java.util.HashSet<>();
                // register bottom 0..2 (do not register bottom[3] as it's empty)
                for (int i = 0; i < 3; i++)
                    members.add(bottom.get(i).immutable());
                // register top 1..3 (do not register top[0] as it's intentionally empty)
                for (int i = 1; i < 4; i++)
                    members.add(top.get(i).immutable());
                com.islandcraft.init.ModCannonManager.registerCannon(bottom.get(0).immutable(), members);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            // If anything goes wrong, attempt to roll back placed blocks conservatively
            try {
                for (BlockPos p : bottom)
                    if (level.getBlockState(p).getBlock() != Blocks.AIR)
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                for (BlockPos p : top)
                    if (level.getBlockState(p).getBlock() != Blocks.AIR)
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            } catch (Throwable __) {
            }
            return InteractionResult.FAIL;
        }

        // consume item unless in creative
        if (player != null && !player.isCreative()) {
            ItemStack stack = ctx.getItemInHand();
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    private boolean isReplaceable(Level level, BlockPos pos, BlockPlaceContext ctx) {
        return level.isEmptyBlock(pos) || level.getBlockState(pos).canBeReplaced(ctx);
    }

}
