package com.islandcraft.init;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Runtime registry that tracks placed cannons and their member positions.
 * Used to teardown the entire cannon when any member is broken.
 */
public final class ModCannonManager {
    private ModCannonManager() {
    }

    // Map member position -> master position
    private static final Map<BlockPos, BlockPos> MEMBER_TO_MASTER = new ConcurrentHashMap<>();
    // Map master position -> members set
    private static final Map<BlockPos, Set<BlockPos>> MASTER_TO_MEMBERS = new ConcurrentHashMap<>();

    // Optional SavedData link — used only to mark data dirty (no immediate save)
    private static ModCannonSavedData savedData = null;

    public static void setSavedData(ModCannonSavedData data) {
        savedData = data;
    }

    private static void markDirty() {
        if (savedData != null) {
            savedData.setDirty(); // rely on world autosave for actual disk write
        }
    }

    public static void registerCannon(BlockPos master, Collection<BlockPos> members) {
        if (master == null || members == null || members.isEmpty())
            return;
        Set<BlockPos> copy = Collections.synchronizedSet(new HashSet<>(members));
        MASTER_TO_MEMBERS.put(master.immutable(), copy);
        for (BlockPos m : members) {
            MEMBER_TO_MASTER.put(m.immutable(), master.immutable());
        }
        markDirty();
    }

    public static BlockPos getMasterForMember(BlockPos member) {
        return MEMBER_TO_MASTER.get(member);
    }

    public static Set<BlockPos> getMembersForMaster(BlockPos master) {
        Set<BlockPos> s = MASTER_TO_MEMBERS.get(master);
        return s == null ? Collections.emptySet() : new HashSet<>(s);
    }

    public static void removeCannon(BlockPos master) {
        Set<BlockPos> members = MASTER_TO_MEMBERS.remove(master);
        if (members != null) {
            for (BlockPos m : members)
                MEMBER_TO_MASTER.remove(m);
        }
        markDirty();
    }

    public static void removeCannonByMember(BlockPos member) {
        BlockPos master = MEMBER_TO_MASTER.get(member);
        if (master != null)
            removeCannon(master);
        // markDirty() already called by removeCannon
    }

    public static void loadFromServer(MinecraftServer server) {
        if (server == null)
            return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null)
            return;
        try {
            ModCannonSavedData data = overworld.getDataStorage().computeIfAbsent(ModCannonSavedData::load,
                    ModCannonSavedData::new, "icbf_cannons");
            Map<BlockPos, Set<BlockPos>> map = data.getMap();
            MASTER_TO_MEMBERS.clear();
            MEMBER_TO_MASTER.clear();
            for (Map.Entry<BlockPos, Set<BlockPos>> e : map.entrySet()) {
                MASTER_TO_MEMBERS.put(e.getKey(), Collections.synchronizedSet(new HashSet<>(e.getValue())));
                for (BlockPos m : e.getValue())
                    MEMBER_TO_MASTER.put(m, e.getKey());
            }
        } catch (Throwable t) {
            System.err.println("Failed to load cannons from SavedData: " + t);
        }
    }

    public static void saveToServer(MinecraftServer server) {
        if (server == null)
            return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null)
            return;
        try {
            ModCannonSavedData data = overworld.getDataStorage().computeIfAbsent(ModCannonSavedData::load,
                    ModCannonSavedData::new, "icbf_cannons");
            data.setMap(new HashMap<>(MASTER_TO_MEMBERS));
            data.setDirty();
        } catch (Throwable t) {
            System.err.println("Failed to save cannons to SavedData: " + t);
        }
    }
}
