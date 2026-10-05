package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.PersistedOutputSamples;
import com.ctux.ae2craftingtime.core.PersistedOutputStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;

public final class Ae2CraftingTimeSavedData extends SavedData {
    public static final String FILE_ID = "ae2-crafting-time";

    private final com.ctux.ae2craftingtime.core.SavedSampleHistory samples = new com.ctux.ae2craftingtime.core.SavedSampleHistory();
    private List<ProviderLocateRecords.StoredStart> providerStarts = List.of();
    private List<ProviderLocateRecords.LocateRecord> providerRecords = List.of();
    private List<PersistedOutputStatus> statuses = List.of();

    public static Ae2CraftingTimeSavedData load(CompoundTag tag) {
        var data = new Ae2CraftingTimeSavedData();
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") == PersistedSamplesTag.VERSION) {
            data.samples.replace(PersistedSamplesTag.readOutputs(tag.getList("outputs", Tag.TAG_COMPOUND)));
        }
        if (tag.contains("providers", Tag.TAG_LIST)) {
            data.providerStarts = PersistedProviderTag.readStarts(tag.getList("providers", Tag.TAG_COMPOUND));
        }
        if (tag.contains("locateRecords", Tag.TAG_LIST)) {
            data.providerRecords = PersistedProviderTag.readRecords(tag.getList("locateRecords", Tag.TAG_COMPOUND));
        }
        if (tag.contains("statuses", Tag.TAG_LIST)) {
            data.statuses = PersistedStatusTag.readStatuses(tag.getList("statuses", Tag.TAG_COMPOUND));
        }
        return data;
    }

    public void replaceFrom(List<PersistedOutputSamples> samples) {
        this.samples.replace(samples);
        setDirty();
    }

    public void updateSamples(List<PersistedOutputSamples> changes) {
        samples.update(changes);
        if (!changes.isEmpty()) setDirty();
    }

    public void replaceProviderStarts(List<ProviderLocateRecords.StoredStart> starts) {
        this.providerStarts = starts == null ? List.of() : List.copyOf(starts);
        setDirty();
    }

    public void replaceProviderRecords(List<ProviderLocateRecords.LocateRecord> records) {
        this.providerRecords = records == null ? List.of() : List.copyOf(records);
        setDirty();
    }

    public void replaceStatuses(List<PersistedOutputStatus> statuses) {
        this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
        setDirty();
    }

    public List<PersistedOutputSamples> samples() {
        return samples.snapshot();
    }

    public List<ProviderLocateRecords.StoredStart> providerStarts() {
        return providerStarts;
    }

    public List<ProviderLocateRecords.LocateRecord> providerRecords() {
        return providerRecords;
    }

    public List<PersistedOutputStatus> statuses() {
        return statuses;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ProfilerBridge.flushCompletedSamples();
        tag.putInt("version", PersistedSamplesTag.VERSION);
        tag.put("outputs", PersistedSamplesTag.writeOutputs(samples()));
        tag.put("providers", PersistedProviderTag.writeStarts(providerStarts));
        tag.put("locateRecords", PersistedProviderTag.writeRecords(providerRecords));
        tag.put("statuses", PersistedStatusTag.writeStatuses(statuses));
        return tag;
    }
}
