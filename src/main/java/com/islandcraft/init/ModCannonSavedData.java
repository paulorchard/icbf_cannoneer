package com.islandcraft.init;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * World-backed saved data for cannons. Stores master -> members mapping.
 */
public class ModCannonSavedData extends SavedData {
    private final Map<BlockPos, Set<BlockPos>> masterToMembers = new HashMap<>();

    public ModCannonSavedData() {
        super();
    }

    public static ModCannonSavedData load(CompoundTag nbt) {
        ModCannonSavedData data = new ModCannonSavedData();
        if (nbt == null)
            return data;
        ListTag cannons = nbt.getList("cannons", Tag.TAG_COMPOUND);
        for (int i = 0; i < cannons.size(); i++) {
            CompoundTag c = cannons.getCompound(i);
            CompoundTag m = c.getCompound("master");
            BlockPos master = new BlockPos(m.getInt("x"), m.getInt("y"), m.getInt("z"));
            ListTag members = c.getList("members", Tag.TAG_COMPOUND);
            Set<BlockPos> mems = new HashSet<>();
            for (int j = 0; j < members.size(); j++) {
                CompoundTag mm = members.getCompound(j);
                BlockPos bp = new BlockPos(mm.getInt("x"), mm.getInt("y"), mm.getInt("z"));
                mems.add(bp);
            }
            data.masterToMembers.put(master, mems);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag cannons = new ListTag();
        for (Map.Entry<BlockPos, Set<BlockPos>> e : masterToMembers.entrySet()) {
            CompoundTag entry = new CompoundTag();
            CompoundTag m = new CompoundTag();
            BlockPos master = e.getKey();
            m.putInt("x", master.getX());
            m.putInt("y", master.getY());
            m.putInt("z", master.getZ());
            entry.put("master", m);
            ListTag members = new ListTag();
            for (BlockPos bp : e.getValue()) {
                CompoundTag mm = new CompoundTag();
                mm.putInt("x", bp.getX());
                mm.putInt("y", bp.getY());
                mm.putInt("z", bp.getZ());
                members.add(mm);
            }
            entry.put("members", members);
            cannons.add(entry);
        }
        nbt.put("cannons", cannons);
        return nbt;
    }

    public Map<BlockPos, Set<BlockPos>> getMap() {
        return masterToMembers;
    }

    public void setMap(Map<BlockPos, Set<BlockPos>> map) {
        this.masterToMembers.clear();
        this.masterToMembers.putAll(map);
        setDirty();
    }
}
