package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.PersistedOutputSamples;
import com.ctux.ae2craftingtime.core.PersistedOutputStatus;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

public final class Ae2CraftingTimeSavedData extends SavedData {
    public static final String FILE_ID = "ae2-crafting-time";
    public static final SavedDataType<Ae2CraftingTimeSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("ae2craftingtime", FILE_ID),
            Ae2CraftingTimeSavedData::new,
            new Codec<Ae2CraftingTimeSavedData>() {
                @Override
                public <T> DataResult<T> encode(Ae2CraftingTimeSavedData data, DynamicOps<T> ops, T prefix) {
                    return CompoundTag.CODEC.encode(data.save(ops), ops, prefix);
                }

                @Override
                public <T> DataResult<Pair<Ae2CraftingTimeSavedData, T>> decode(DynamicOps<T> ops, T input) {
                    return CompoundTag.CODEC.decode(ops, input)
                            .map(decoded -> Pair.of(load(decoded.getFirst(), ops), decoded.getSecond()));
                }
            },
            DataFixTypes.LEVEL);

    private final com.ctux.ae2craftingtime.core.SavedSampleHistory samples = new com.ctux.ae2craftingtime.core.SavedSampleHistory();
    private List<ProviderLocateRecords.StoredStart> providerStarts = List.of();
    private List<ProviderLocateRecords.LocateRecord> providerRecords = List.of();
    private List<PersistedOutputStatus> statuses = List.of();

    private static Ae2CraftingTimeSavedData load(CompoundTag tag, DynamicOps<?> ops) {
        var data = new Ae2CraftingTimeSavedData();
        if (tag.getIntOr("version", PersistedSamplesTag.VERSION) == PersistedSamplesTag.VERSION) {
            data.samples.replace(PersistedSamplesTag.readOutputs(tag.getListOrEmpty("outputs")));
        }
        data.providerStarts = PersistedProviderTag.readStarts(tag.getListOrEmpty("providers"), ops);
        data.providerRecords = PersistedProviderTag.readRecords(tag.getListOrEmpty("locateRecords"));
        data.statuses = PersistedStatusTag.readStatuses(tag.getListOrEmpty("statuses"));
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

    private CompoundTag save(DynamicOps<?> ops) {
        if (Ae2CraftingTimeConfig.SPEC.isLoaded()) {
            ProfilerBridge.flushCompletedSamples();
        }
        var tag = new CompoundTag();
        tag.putInt("version", PersistedSamplesTag.VERSION);
        tag.put("outputs", PersistedSamplesTag.writeOutputs(samples()));
        tag.put("providers", PersistedProviderTag.writeStarts(providerStarts, ops));
        tag.put("locateRecords", PersistedProviderTag.writeRecords(providerRecords));
        tag.put("statuses", PersistedStatusTag.writeStatuses(statuses));
        return tag;
    }
}
