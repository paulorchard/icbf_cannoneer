package com.islandcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import java.lang.reflect.Method;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.GameRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.islandcraft.IslandCraftMod;

@Mod.EventBusSubscriber(modid = IslandCraftMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    // Timed detection + movement/rotation gating to reduce tracing frequency
    private static long lastTraceNanos = 0L;
    private static final long TRACE_INTERVAL_NS = 100_000_000L; // 100 ms default
    private static BlockHitResult cachedHit = null;
    private static Vec3 cachedHitLocation = null;
    private static double lastPlayerX = Double.NaN, lastPlayerY = Double.NaN, lastPlayerZ = Double.NaN;
    private static float lastYaw = Float.NaN, lastPitch = Float.NaN;
    private static final double MOVE_THRESHOLD_SQ = 0.0009; // ~0.03 blocks movement (squared)
    private static final float ROT_THRESHOLD_DEG = 1.5f; // degrees
    private static Vec3 lastLook = null;
    private static final Map<BlockState, List<AABB>> aabbCache = new WeakHashMap<>();
    // Tick-scheduled tracing parameters
    private static int traceIntervalTicks = 6; // run detection every N ticks by default (raised to reduce steady
                                               // traces)
    private static int tickCounter = 0;
    private static final double EYE_MOVE_THRESHOLD_SQ = 0.09; // ~0.3 blocks squared (reduce jitter retraces)

    // Token-bucket limiter to cap traces/sec
    private static final double TOKEN_REFILL_PER_SEC = 6.0; // refill rate (tokens per second)
    private static final double TOKEN_BUCKET_MAX = 12.0; // allow small bursts
    private static double tokenBucket = TOKEN_BUCKET_MAX;
    private static long lastTokenRefillNanos = System.nanoTime();

    // Last block position to detect coarse movement across tile boundaries
    private static BlockPos lastCachedBlockPos = null;

    // Telemetry: traces attempted and successful hits per second / per minute
    private static long telemetryLastSecond = System.nanoTime() / 1_000_000_000L;
    private static final int TELEMETRY_RING_SECONDS = 60;
    private static final int[] telemetryRingTraces = new int[TELEMETRY_RING_SECONDS];
    private static final int[] telemetryRingHits = new int[TELEMETRY_RING_SECONDS];
    private static int telemetryRingIndex = 0;
    private static int telemetryCurrentSecondTraces = 0;
    private static int telemetryCurrentSecondHits = 0;
    private static final long[] telemetryRingTraceNs = new long[TELEMETRY_RING_SECONDS];
    private static long telemetryTotalTraceNsCurrentSecond = 0L;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null)
            return;

        // Only consider tracing while the player is using a spyglass and scoped
        if (!player.isUsingItem() || !player.getUseItem().is(Items.SPYGLASS))
            return;
        boolean scoped = false;
        try {
            Object res = GameRenderer.class.getMethod("isScoping").invoke(mc.gameRenderer);
            if (res instanceof Boolean)
                scoped = (Boolean) res;
        } catch (Throwable ignored) {
        }
        if (!scoped && player.getTicksUsingItem() < 6)
            return;

        // Refill token bucket
        long now = System.nanoTime();
        double deltaSec = (now - lastTokenRefillNanos) / 1e9d;
        if (deltaSec > 0) {
            tokenBucket = Math.min(TOKEN_BUCKET_MAX, tokenBucket + deltaSec * TOKEN_REFILL_PER_SEC);
            lastTokenRefillNanos = now;
        }

        // Telemetry second rollover: every second push current second counts into ring
        long nowSec = now / 1_000_000_000L;
        if (nowSec != telemetryLastSecond) {
            // advance ring index and store last second's counts
            telemetryRingIndex = (telemetryRingIndex + 1) % TELEMETRY_RING_SECONDS;
            telemetryRingTraces[telemetryRingIndex] = telemetryCurrentSecondTraces;
            telemetryRingHits[telemetryRingIndex] = telemetryCurrentSecondHits;
            telemetryRingTraceNs[telemetryRingIndex] = telemetryTotalTraceNsCurrentSecond;

            int perMinTraces = 0, perMinHits = 0;
            for (int v : telemetryRingTraces)
                perMinTraces += v;
            for (int v : telemetryRingHits)
                perMinHits += v;
            long perMinTraceNs = 0L;
            for (long v : telemetryRingTraceNs)
                perMinTraceNs += v;

            long avgNsLastSec = telemetryCurrentSecondTraces > 0
                    ? telemetryTotalTraceNsCurrentSecond / telemetryCurrentSecondTraces
                    : 0L;
            IslandCraftMod.LOGGER.info(
                    "Trace telemetry — lastSecTraces={} lastSecHits={} lastSecTotalTraceNs={} lastSecAvgNs={} perMinTraces={} perMinHits={} perMinTotalTraceNs={}",
                    telemetryCurrentSecondTraces, telemetryCurrentSecondHits, telemetryTotalTraceNsCurrentSecond,
                    avgNsLastSec,
                    perMinTraces, perMinHits, perMinTraceNs);

            // reset counters for new second
            telemetryCurrentSecondTraces = 0;
            telemetryCurrentSecondHits = 0;
            telemetryTotalTraceNsCurrentSecond = 0L;
            telemetryLastSecond = nowSec;
        }

        // Coarse gating: tick interval
        tickCounter++;
        boolean onInterval = (tickCounter % traceIntervalTicks) == 0;

        // Movement/rotation gating (quantized)
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        boolean moved = false;
        if (lastCachedBlockPos == null || !lastCachedBlockPos.equals(player.blockPosition())) {
            moved = true;
        } else {
            double dx = eye.x - lastPlayerX;
            double dy = eye.y - lastPlayerY;
            double dz = eye.z - lastPlayerZ;
            moved = (dx * dx + dy * dy + dz * dz) > EYE_MOVE_THRESHOLD_SQ;
        }

        boolean rotated = false;
        if (lastLook == null) {
            rotated = true;
        } else {
            double dot = lastLook.x * look.x + lastLook.y * look.y + lastLook.z * look.z;
            double cosThresh = Math.cos(Math.toRadians(ROT_THRESHOLD_DEG));
            if (dot < cosThresh)
                rotated = true;
        }

        // Decide to run trace: require token and at least one trigger
        if (tokenBucket < 1.0 && !(moved || rotated)) {
            return; // no budget and no strong trigger
        }

        if (!(onInterval || moved || rotated)) {
            return; // no reason to retrace this tick
        }

        // Consume token (if available)
        if (tokenBucket >= 1.0) {
            tokenBucket -= 1.0;
        } else {
            // if no token but we got here due to movement/rotation, try once
        }

        // Perform the world trace and update the cached result
        try {
            double maxRange = 100.0D;
            Vec3 traceEnd = eye.add(look.scale(maxRange));
            // Telemetry: note we're attempting a trace (and time it)
            long traceStart = System.nanoTime();
            telemetryCurrentSecondTraces++;
            HitResult raw = null;
            try {
                raw = mc.level.clip(
                        new ClipContext(eye, traceEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
            } catch (Throwable t) {
                raw = mc.hitResult;
            }
            // record trace timing
            try {
                long traceNs = System.nanoTime() - traceStart;
                telemetryTotalTraceNsCurrentSecond += traceNs;
            } catch (Throwable ignored) {
            }
            if (!(raw instanceof BlockHitResult)) {
                cachedHit = null;
                cachedHitLocation = null;
            } else {
                BlockHitResult bhr = (BlockHitResult) raw;
                cachedHit = bhr;
                cachedHitLocation = bhr.getLocation();
                // Telemetry: successful block hit
                telemetryCurrentSecondHits++;
                lastTraceNanos = now;
                lastPlayerX = eye.x;
                lastPlayerY = eye.y;
                lastPlayerZ = eye.z;
                lastYaw = player.getYRot();
                lastPitch = player.getXRot();
                lastLook = look;
                lastCachedBlockPos = bhr.getBlockPos();
            }
        } catch (Throwable t) {
            // swallow — tracing is best-effort
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Note: we avoid strict stage checks to prevent mapping issues.
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null)
            return;

        // Only when player is using the spyglass and is scoped (zoomed in)
        if (!player.isUsingItem())
            return;
        if (!player.getUseItem().is(Items.SPYGLASS))
            return;
        // Determine if the player is actually zoomed in (scoped). We prefer
        // GameRenderer.isScoping when available,
        // but also require a minimum use tick count as a fallback (6 ticks is the
        // typical spyglass zoom delay).
        boolean scoped = false;
        try {
            GameRenderer gr = mc.gameRenderer;
            // Verbose debug: print player usage state and hit result summary
            try {
                IslandCraftMod.LOGGER.trace("Render event: isUsingItem={}, useItem={}, ticksUsing={}",
                        player.isUsingItem(), player.getUseItem().getItem(), player.getTicksUsingItem());
                IslandCraftMod.LOGGER.trace("Current hitResult class: {}",
                        mc.hitResult == null ? "null" : mc.hitResult.getClass().getSimpleName());
            } catch (Exception e) {
                // ignore logging errors
            }
            try {
                Object res = GameRenderer.class.getMethod("isScoping").invoke(gr);
                if (res instanceof Boolean)
                    scoped = (Boolean) res;
            } catch (NoSuchMethodException nsme) {
                // method not present in these mappings
            }
        } catch (Throwable t) {
            // ignore
        }
        // fallback: require at least 6 ticks of use to consider fully zoomed
        if (!scoped && player.getTicksUsingItem() < 6)
            return;

        // Use cached detection result populated by the tick handler. Avoid doing
        // expensive traces during render; tracing is scheduled on ticks.
        if (cachedHit == null) {
            IslandCraftMod.LOGGER.trace("No cached hit available; skipping render");
            return;
        }
        BlockHitResult bhr = cachedHit;
        Vec3 hitLoc = cachedHitLocation;
        double maxRange = 100.0D;
        Vec3 playerEye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();

        BlockPos pos = bhr.getBlockPos();
        Direction face = bhr.getDirection();

        // Distance check (max 100 blocks) measured from the player's eye
        // position (trace origin). Using the camera position can be offset and
        // produced incorrect >=6.0 caps previously.
        double hitDist = playerEye.distanceTo(hitLoc);
        IslandCraftMod.LOGGER.trace("Not scoped yet: scoped={} ticksUsing={}", scoped, player.getTicksUsingItem());
        if (hitDist > maxRange) {
            IslandCraftMod.LOGGER.trace("Trace hit beyond maxRange: {} > {} -- skipping", hitDist, maxRange);
            return;
        }

        // Skip air hits -- ensure the block at the hit position is not air.
        try {
            if (mc.level.getBlockState(pos).isAir()) {
                IslandCraftMod.LOGGER.trace("Trace hit pos {} is air; skipping render", pos);
                return;
            }
        } catch (Exception e) {
            // If level access fails for some reason, bail out to avoid rendering in empty
            // space
            IslandCraftMod.LOGGER.trace("Failed to check block at {}: {}", pos, e.toString());
            return;
        }

        // Heuristic: if the block's collision/visual shape is very thin (e.g.
        // lily pads, carpets), prefer rendering only the top face to avoid
        // surrounding vertical-face highlights. We consider the block "thin"
        // when its max AABB height is less than 0.25 blocks.
        try {
            VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
            double maxHeight = 0.0d;
            for (AABB box : shape.toAabbs()) {
                double h = box.maxY - box.minY;
                if (h > maxHeight)
                    maxHeight = h;
            }
            if (maxHeight > 0.0d && maxHeight < 0.25d) {
                IslandCraftMod.LOGGER.trace("Thin block detected at {} height={} - forcing UP face only", pos,
                        maxHeight);
                face = Direction.UP;
            }
        } catch (Throwable t) {
            // If shape inspection fails, continue with default face behavior
        }

        // Detailed debug: log trace/ hit coordinates and distance
        try {
            IslandCraftMod.LOGGER.trace(
                    "Spyglass scoped hit at {} face {} playerEye={} hitLoc={} blockCenter={} hitDist={}", pos, face,
                    playerEye, hitLoc, new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D), hitDist);
        } catch (Exception ignore) {
            IslandCraftMod.LOGGER.trace("Spyglass scoped hit at {} face {} hitDist {}", pos, face, hitDist);
        }

        // Render a filled translucent red quad on the face, with a small offset to
        // avoid z-fighting
        PoseStack ps = event.getPoseStack();
        ps.pushPose();

        // Do not manually translate the PoseStack by camera position — we compute
        // camera-relative coordinates for vertices instead.
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double dist = hitDist;
        if (dist > 100.0) {
            IslandCraftMod.LOGGER.trace("Hit too far: {} > 100", dist);
            return;
        }

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double eps = 1.0 / 256.0; // small offset

        float r = 1f;
        float g = 0f;
        float b = 0f;
        float a = 0.20f;

        double x0, y0, z0, x1, y1, z1;
        switch (face) {
            case UP:
                y0 = y + 1.0 + eps;
                y1 = y + 1.0 + eps;
                x0 = x;
                z0 = z;
                x1 = x + 1.0;
                z1 = z + 1.0;
                break;
            case DOWN:
                y0 = y - eps;
                y1 = y - eps;
                x0 = x + 1.0;
                z0 = z;
                x1 = x;
                z1 = z + 1.0;
                break;
            case NORTH:
                z0 = z - eps;
                z1 = z - eps;
                x0 = x + 1.0;
                y0 = y;
                x1 = x;
                y1 = y + 1.0;
                break;
            case SOUTH:
                z0 = z + 1.0 + eps;
                z1 = z + 1.0 + eps;
                x0 = x;
                y0 = y;
                x1 = x + 1.0;
                y1 = y + 1.0;
                break;
            case WEST:
                x0 = x - eps;
                x1 = x - eps;
                z0 = z + 1.0;
                y0 = y;
                z1 = z;
                y1 = y + 1.0;
                break;
            case EAST:
            default:
                x0 = x + 1.0 + eps;
                x1 = x + 1.0 + eps;
                z0 = z;
                y0 = y;
                z1 = z + 1.0;
                y1 = y + 1.0;
                break;
        }

        // Enable blending and use standard alpha blending. Keep depth testing
        // enabled but disable depth writes so the translucent overlay is
        // properly blended with world geometry while avoiding depth buffer
        // corruption.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.enableDepthTest();
        } catch (Throwable ignored) {
        }
        RenderSystem.depthMask(false);
        // Disable face culling so quads facing away from the current winding
        // still render (we render translucent overlays and want both sides).
        try {
            RenderSystem.disableCull();
        } catch (Throwable ignored) {
        }

        try {
            Object poseEntry = ps.last();
            Object mat = null;
            try {
                mat = poseEntry.getClass().getMethod("pose").invoke(poseEntry);
            } catch (NoSuchMethodException nsme) {
                // try alternate method name
                mat = poseEntry.getClass().getMethod("pose", (Class<?>[]) null).invoke(poseEntry);
            }

            BufferBuilder buf = Tesselator.getInstance().getBuilder();
            // Ensure we use the position-color shader for untextured colored geometry
            try {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            } catch (Throwable ignored) {
            }
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            // Use the VertexConsumer API directly (typesafe) to avoid reflection issues
            try {
                VertexConsumer vc = buf;

                // Render the center block and its 4 cardinal neighbors (a plus-shaped cross).
                // Orientation depends on the hit face:
                // - UP/DOWN: cross in XZ plane (neighbors east/west/north/south)
                // - NORTH/SOUTH: cross in XY plane (neighbors east/west/up/down)
                // - EAST/WEST: cross in ZY plane (neighbors north/south/up/down)
                int[][] offsets3;
                if (face == Direction.UP || face == Direction.DOWN) {
                    offsets3 = new int[][] { { 0, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
                } else if (face == Direction.NORTH || face == Direction.SOUTH) {
                    offsets3 = new int[][] { { 0, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 } };
                } else {
                    // EAST or WEST
                    offsets3 = new int[][] { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 } };
                }

                for (int[] off : offsets3) {
                    int dx = off[0];
                    int dy = off[1];
                    int dz = off[2];
                    BlockPos targetPos = pos.offset(dx, dy, dz);

                    // Skip if target is air
                    try {
                        if (mc.level.getBlockState(targetPos).isAir())
                            continue;
                    } catch (Throwable t) {
                        continue;
                    }

                    BlockState bs = mc.level.getBlockState(targetPos);
                    java.util.List<AABB> aabbs = aabbCache.computeIfAbsent(bs,
                            (state) -> state.getShape(mc.level, targetPos).toAabbs());
                    boolean usedAabbs = false;

                    if (aabbs.size() > 0) {
                        for (AABB box : aabbs) {
                            double minX = targetPos.getX() + box.minX;
                            double minY = targetPos.getY() + box.minY;
                            double minZ = targetPos.getZ() + box.minZ;
                            double maxX = targetPos.getX() + box.maxX;
                            double maxY = targetPos.getY() + box.maxY;
                            double maxZ = targetPos.getZ() + box.maxZ;

                            double[][] quad = new double[4][3];
                            switch (face) {
                                case UP: {
                                    double yc = maxY + eps;
                                    quad[0] = new double[] { minX, yc, minZ };
                                    quad[1] = new double[] { maxX, yc, minZ };
                                    quad[2] = new double[] { maxX, yc, maxZ };
                                    quad[3] = new double[] { minX, yc, maxZ };
                                    break;
                                }
                                case DOWN: {
                                    double yc = minY - eps;
                                    quad[0] = new double[] { minX, yc, maxZ };
                                    quad[1] = new double[] { maxX, yc, maxZ };
                                    quad[2] = new double[] { maxX, yc, minZ };
                                    quad[3] = new double[] { minX, yc, minZ };
                                    break;
                                }
                                case NORTH: {
                                    double zc = minZ - eps;
                                    quad[0] = new double[] { maxX, minY, zc };
                                    quad[1] = new double[] { maxX, maxY, zc };
                                    quad[2] = new double[] { minX, maxY, zc };
                                    quad[3] = new double[] { minX, minY, zc };
                                    break;
                                }
                                case SOUTH: {
                                    double zc = maxZ + eps;
                                    quad[0] = new double[] { minX, minY, zc };
                                    quad[1] = new double[] { minX, maxY, zc };
                                    quad[2] = new double[] { maxX, maxY, zc };
                                    quad[3] = new double[] { maxX, minY, zc };
                                    break;
                                }
                                case WEST: {
                                    double xc = minX - eps;
                                    quad[0] = new double[] { xc, minY, minZ };
                                    quad[1] = new double[] { xc, maxY, minZ };
                                    quad[2] = new double[] { xc, maxY, maxZ };
                                    quad[3] = new double[] { xc, minY, maxZ };
                                    break;
                                }
                                case EAST:
                                default: {
                                    double xc = maxX + eps;
                                    quad[0] = new double[] { xc, minY, maxZ };
                                    quad[1] = new double[] { xc, maxY, maxZ };
                                    quad[2] = new double[] { xc, maxY, minZ };
                                    quad[3] = new double[] { xc, minY, minZ };
                                    break;
                                }
                            }

                            for (int i = 0; i < 4; i++) {
                                double rx = quad[i][0] - camPos.x;
                                double ry = quad[i][1] - camPos.y;
                                double rz = quad[i][2] - camPos.z;
                                vc.vertex(rx, ry, rz).color(r, g, b, a).endVertex();
                            }
                            usedAabbs = true;
                        }
                    }

                    if (!usedAabbs) {
                        double[][] cornersLoc = new double[4][3];
                        switch (face) {
                            case UP: {
                                double yc = targetPos.getY() + 1.0 + eps;
                                cornersLoc[0] = new double[] { targetPos.getX(), yc, targetPos.getZ() };
                                cornersLoc[1] = new double[] { targetPos.getX() + 1.0, yc, targetPos.getZ() };
                                cornersLoc[2] = new double[] { targetPos.getX() + 1.0, yc, targetPos.getZ() + 1.0 };
                                cornersLoc[3] = new double[] { targetPos.getX(), yc, targetPos.getZ() + 1.0 };
                                break;
                            }
                            case DOWN: {
                                double yc = targetPos.getY() - eps;
                                cornersLoc[0] = new double[] { targetPos.getX(), yc, targetPos.getZ() + 1.0 };
                                cornersLoc[1] = new double[] { targetPos.getX() + 1.0, yc, targetPos.getZ() + 1.0 };
                                cornersLoc[2] = new double[] { targetPos.getX() + 1.0, yc, targetPos.getZ() };
                                cornersLoc[3] = new double[] { targetPos.getX(), yc, targetPos.getZ() };
                                break;
                            }
                            case NORTH: {
                                double zc = targetPos.getZ() - eps;
                                cornersLoc[0] = new double[] { targetPos.getX() + 1.0, targetPos.getY(), zc };
                                cornersLoc[1] = new double[] { targetPos.getX() + 1.0, targetPos.getY() + 1.0, zc };
                                cornersLoc[2] = new double[] { targetPos.getX(), targetPos.getY() + 1.0, zc };
                                cornersLoc[3] = new double[] { targetPos.getX(), targetPos.getY(), zc };
                                break;
                            }
                            case SOUTH: {
                                double zc = targetPos.getZ() + 1.0 + eps;
                                cornersLoc[0] = new double[] { targetPos.getX(), targetPos.getY(), zc };
                                cornersLoc[1] = new double[] { targetPos.getX(), targetPos.getY() + 1.0, zc };
                                cornersLoc[2] = new double[] { targetPos.getX() + 1.0, targetPos.getY() + 1.0, zc };
                                cornersLoc[3] = new double[] { targetPos.getX() + 1.0, targetPos.getY(), zc };
                                break;
                            }
                            case WEST: {
                                double xc = targetPos.getX() - eps;
                                cornersLoc[0] = new double[] { xc, targetPos.getY(), targetPos.getZ() };
                                cornersLoc[1] = new double[] { xc, targetPos.getY() + 1.0, targetPos.getZ() };
                                cornersLoc[2] = new double[] { xc, targetPos.getY() + 1.0, targetPos.getZ() + 1.0 };
                                cornersLoc[3] = new double[] { xc, targetPos.getY(), targetPos.getZ() + 1.0 };
                                break;
                            }
                            case EAST:
                            default: {
                                double xc = targetPos.getX() + 1.0 + eps;
                                cornersLoc[0] = new double[] { xc, targetPos.getY(), targetPos.getZ() + 1.0 };
                                cornersLoc[1] = new double[] { xc, targetPos.getY() + 1.0, targetPos.getZ() + 1.0 };
                                cornersLoc[2] = new double[] { xc, targetPos.getY() + 1.0, targetPos.getZ() };
                                cornersLoc[3] = new double[] { xc, targetPos.getY(), targetPos.getZ() };
                                break;
                            }
                        }

                        for (int i = 0; i < 4; i++) {
                            double rx = cornersLoc[i][0] - camPos.x;
                            double ry = cornersLoc[i][1] - camPos.y;
                            double rz = cornersLoc[i][2] - camPos.z;
                            vc.vertex(rx, ry, rz).color(r, g, b, a).endVertex();
                        }
                    }
                }
            } catch (Exception ex) {
                IslandCraftMod.LOGGER.trace("Fallback (reflection) - failed to use VertexConsumer: {}", ex.toString());
            }

            Tesselator.getInstance().end();
        } catch (Exception ex) {
            // If reflection approach fails, log and skip rendering this frame
            IslandCraftMod.LOGGER.warn("Failed to render spyglass highlight (reflection):", ex);
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        try {
            RenderSystem.enableCull();
        } catch (Throwable ignored) {
        }

        ps.popPose();
    }
}
