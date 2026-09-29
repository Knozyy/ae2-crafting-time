package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.PacketLimits;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.mc1201.ProviderLocateRecords.LocateRecord;
import com.ctux.ae2craftingtime.mc1201.ProviderLocateRecords.StoredStart;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;

final class PersistedProviderTag {
    private static final int MAX_NAME_LENGTH = 512;

    static List<StoredStart> readStarts(ListTag starts) {
        return readStarts(starts, null);
    }

    static List<StoredStart> readStarts(ListTag starts, Object registries) {
        var persisted = new ArrayList<StoredStart>();
        for (var startTag : starts) {
            if (!(startTag instanceof CompoundTag start)) {
                continue;
            }
            ProfileKey key;
            UUID owner;
            try {
                key = new ProfileKey(start.getStringOr("networkId", ""),
                        PacketLimits.checkedOutputId(start.getStringOr("key", "")));
                owner = UUID.fromString(start.getStringOr("owner", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            var positions = new ArrayList<BlockPos>();
            for (var posTag : start.getListOrEmpty("positions")) {
                if (positions.size() >= PacketLimits.MAX_HIGHLIGHT_POSITIONS) {
                    break;
                }
                if (posTag instanceof LongTag longTag) {
                    positions.add(BlockPos.of(longTag.longValue()));
                }
            }
            var name = start.getStringOr("name", "");
            if (name.length() > MAX_NAME_LENGTH) {
                continue;
            }
            var dimension = start.getStringOr("dimension", "");
            var displayTag = start.get("displayKey") instanceof CompoundTag compound ? compound : null;
            var displayKey = ProviderDisplayKeyTag.read(displayTag, registries);
            persisted.add(new StoredStart(key, owner, dimension, positions,
                    name.isBlank() ? key.outputId() : name, displayKey));
        }
        return persisted;
    }

    static ListTag writeStarts(List<StoredStart> starts) {
        return writeStarts(starts, null);
    }

    static ListTag writeStarts(List<StoredStart> starts, Object registries) {
        var startTags = new ListTag();
        for (var start : starts) {
            if (start == null || start.key() == null || start.owner() == null) {
                continue;
            }
            var tag = new CompoundTag();
            tag.putString("networkId", start.key().networkId());
            tag.putString("key", start.key().outputId());
            tag.putString("owner", start.owner().toString());
            tag.putString("dimension", start.dimensionId() == null ? "" : start.dimensionId());
            var posTags = new ListTag();
            if (start.positions() != null) {
                for (var pos : start.positions()) {
                    if (pos == null || posTags.size() >= PacketLimits.MAX_HIGHLIGHT_POSITIONS) {
                        continue;
                    }
                    posTags.add(LongTag.valueOf(pos.asLong()));
                }
            }
            tag.put("positions", posTags);
            var name = start.outputName() == null ? "" : start.outputName();
            tag.putString("name", name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
            var displayTag = ProviderDisplayKeyTag.write(start.displayKey(), registries);
            if (displayTag != null) {
                tag.put("displayKey", displayTag);
            }
            startTags.add(tag);
        }
        return startTags;
    }

    private PersistedProviderTag() {
    }

    static List<LocateRecord> readRecords(ListTag tags) {
        var persisted = new ArrayList<LocateRecord>();
        for (var recordTag : tags) {
            if (!(recordTag instanceof CompoundTag record)) {
                continue;
            }
            UUID id;
            UUID owner;
            try {
                id = UUID.fromString(record.getStringOr("id", ""));
                owner = UUID.fromString(record.getStringOr("owner", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            String outputId;
            try {
                outputId = PacketLimits.checkedOutputId(record.getStringOr("outputId", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            var positions = new ArrayList<BlockPos>();
            for (var posTag : record.getListOrEmpty("positions")) {
                if (positions.size() >= PacketLimits.MAX_HIGHLIGHT_POSITIONS) {
                    break;
                }
                if (posTag instanceof LongTag longTag) {
                    positions.add(BlockPos.of(longTag.longValue()));
                }
            }
            var name = record.getStringOr("name", "");
            if (name.length() > MAX_NAME_LENGTH) {
                continue;
            }
            var dimension = record.getStringOr("dimension", "");
            var networkId = record.getStringOr("networkId", "");
            if (networkId.length() > PacketLimits.MAX_OUTPUT_ID_LENGTH) continue;
            var tick = record.getLongOr("tick", 0L);
            persisted.add(new LocateRecord(id, owner, dimension, positions,
                    name.isBlank() ? outputId : name, outputId, tick, networkId));
            if (persisted.size() >= 256) {
                break;
            }
        }
        return persisted;
    }

    static ListTag writeRecords(List<LocateRecord> records) {
        var recordTags = new ListTag();
        for (var record : records) {
            if (record == null || record.id() == null || record.owner() == null) {
                continue;
            }
            var tag = new CompoundTag();
            tag.putString("id", record.id().toString());
            tag.putString("owner", record.owner().toString());
            tag.putString("dimension", record.dimensionId() == null ? "" : record.dimensionId());
            tag.putString("networkId", record.networkId() == null ? "" : record.networkId());
            var posTags = new ListTag();
            if (record.positions() != null) {
                for (var pos : record.positions()) {
                    if (pos == null || posTags.size() >= PacketLimits.MAX_HIGHLIGHT_POSITIONS) {
                        continue;
                    }
                    posTags.add(LongTag.valueOf(pos.asLong()));
                }
            }
            tag.put("positions", posTags);
            var name = record.outputName() == null ? "" : record.outputName();
            tag.putString("name", name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
            tag.putString("outputId", record.outputId() == null ? "" : record.outputId());
            tag.putLong("tick", record.createdTick());
            recordTags.add(tag);
            if (recordTags.size() >= 256) {
                break;
            }
        }
        return recordTags;
    }
}
