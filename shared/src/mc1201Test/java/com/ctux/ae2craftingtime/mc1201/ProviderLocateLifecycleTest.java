package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.mc1201.ProviderLocateRecords.LocateRecord;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Chat-link lifecycle: active links survive reload via persisted records and
 * per-output fallbacks, while finished and cancelled links expire instead of
 * recreating red or targeting a replacement block.
 */
class ProviderLocateLifecycleTest {
    @AfterEach
    void clearRecords() {
        ProviderLocateRecords.clearAll();
    }

    @Test
    void activeRecordsSurviveSnapshotRestore() {
        var owner = UUID.randomUUID();
        var record = ProviderLocateRecords.create(owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3)), "Iron", "minecraft:iron_ingot", 100L);

        var snapshot = ProviderLocateRecords.snapshotRecords();
        assertEquals(1, snapshot.size());

        ProviderLocateRecords.clearAll();
        assertTrue(ProviderLocateRecords.ownedBy(owner, record.id()).isEmpty());

        ProviderLocateRecords.restoreRecords(snapshot);
        var restored = ProviderLocateRecords.ownedBy(owner, record.id()).orElseThrow();
        assertEquals(List.of(new BlockPos(1, 2, 3)), restored.positions());
        assertEquals("minecraft:iron_ingot", restored.outputId());
    }

    @Test
    void finishRemovesOnlyOwnersFinishedOutputs() {
        var owner = UUID.randomUUID();
        var otherOwner = UUID.randomUUID();
        var iron = new ProfileKey("net|1,2,3", "minecraft:iron_ingot");
        var copper = new ProfileKey("net|1,2,3", "minecraft:copper_plate");
        ProviderLocateRecords.create(owner, "minecraft:overworld", List.of(new BlockPos(1, 2, 3)), "Iron",
                "minecraft:iron_ingot", 1L);
        var copperRecord = ProviderLocateRecords.create(owner, "minecraft:overworld",
                List.of(new BlockPos(4, 5, 6)), "Copper", "minecraft:copper_plate", 2L);
        var foreignRecord = ProviderLocateRecords.create(otherOwner, "minecraft:overworld",
                List.of(new BlockPos(7, 8, 9)), "Iron", "minecraft:iron_ingot", 3L);

        ProviderLocateRecords.removeRecordsForKeys(List.of(iron), owner);

        assertTrue(ProviderLocateRecords.snapshotRecords().stream()
                .noneMatch(entry -> entry.outputId().equals("minecraft:iron_ingot")
                        && owner.equals(entry.owner())));
        assertTrue(ProviderLocateRecords.ownedBy(owner, copperRecord.id()).isPresent());
        assertTrue(ProviderLocateRecords.ownedBy(otherOwner, foreignRecord.id()).isPresent());

        ProviderLocateRecords.removeRecordsForKeys(List.of(copper), otherOwner);
        assertTrue(ProviderLocateRecords.ownedBy(owner, copperRecord.id()).isPresent());
    }

    @Test
    void startsForOutputFindsEveryNetworkFallback() {
        var owner = UUID.randomUUID();
        var keyA = new ProfileKey("minecraft:overworld|1,2,3", "minecraft:iron_ingot");
        var keyB = new ProfileKey("minecraft:overworld|9,9,9", "minecraft:iron_ingot");
        ProviderLocateRecords.noteStart(keyA, owner, "minecraft:overworld", List.of(new BlockPos(1, 2, 3)),
                "Iron");
        ProviderLocateRecords.noteStart(keyB, owner, "minecraft:overworld", List.of(new BlockPos(4, 5, 6)),
                "Iron");
        ProviderLocateRecords.noteStart(new ProfileKey("net", "minecraft:copper_plate"), owner,
                "minecraft:overworld", List.of(new BlockPos(7, 8, 9)), "Copper");

        var matches = ProviderLocateRecords.startsForOutput(owner, "minecraft:iron_ingot");
        assertEquals(2, matches.size());

        assertTrue(ProviderLocateRecords.startsForOutput(UUID.randomUUID(), "minecraft:iron_ingot").isEmpty());
        assertTrue(ProviderLocateRecords.startsForOutput(owner, "minecraft:unknown").isEmpty());
        assertTrue(ProviderLocateRecords.startsForOutput(owner, null).isEmpty());
    }

    @Test
    void recordsRoundTripThroughPersistenceTag() {
        var owner = UUID.randomUUID();
        var record = new LocateRecord(UUID.randomUUID(), owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3), new BlockPos(-4, 5, -6)), "Iron", "minecraft:iron_ingot",
                42L, "grid-a");
        var tags = PersistedProviderTag.writeRecords(List.of(record));
        assertEquals(1, tags.size());
        var restored = PersistedProviderTag.readRecords(tags);
        assertEquals(List.of(record), restored);
    }

    @Test
    void sameOutputRecordsKeepNetworkIdentityAndInvalidationIsScoped() {
        var owner = UUID.randomUUID();
        var output = "minecraft:iron_ingot";
        var a = new ProfileKey("grid-a", output);
        var b = new ProfileKey("grid-b", output);
        ProviderLocateRecords.noteStart(a, owner, "minecraft:overworld", List.of(), "Iron");
        ProviderLocateRecords.noteStart(b, owner, "minecraft:overworld", List.of(), "Iron");
        var recordA = ProviderLocateRecords.create(owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3)), "Iron", output, 1L, a.networkId());
        var recordB = ProviderLocateRecords.create(owner, "minecraft:overworld",
                List.of(new BlockPos(4, 5, 6)), "Iron", output, 1L, b.networkId());
        assertEquals(2, ProviderLocateRecords.snapshotStarts().size());
        ProviderLocateRecords.replaceStart(a, owner, "minecraft:overworld", List.of(), "Iron");
        assertTrue(ProviderLocateRecords.ownedBy(owner, recordA.id()).isEmpty());
        assertEquals(List.of(b), ProviderLocateRecords.snapshotStarts().stream()
                .map(ProviderLocateRecords.StoredStart::key).toList());
        assertEquals(b.networkId(), ProviderLocateRecords.ownedBy(owner, recordB.id()).orElseThrow().networkId());
        ProviderLocateRecords.removeRecordsForKeys(List.of(b), owner);
        assertTrue(ProviderLocateRecords.ownedBy(owner, recordB.id()).isEmpty());
    }

    @Test
    void identicalProfileKeysRetainTwoOwnersAndTheirIndependentChatLinks() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var key = new ProfileKey("same-grid", "minecraft:iron_ingot");
        var firstPos = new BlockPos(1, 2, 3);
        var secondPos = new BlockPos(4, 5, 6);
        ProviderLocateRecords.noteStart(key, first, "minecraft:overworld", List.of(firstPos), "Iron");
        ProviderLocateRecords.noteStart(key, second, "minecraft:overworld", List.of(secondPos), "Iron");
        var firstLink = ProviderLocateRecords.create(first, "minecraft:overworld", List.of(firstPos),
                "Iron", key.outputId(), 1L, key.networkId());
        var secondLink = ProviderLocateRecords.create(second, "minecraft:overworld", List.of(secondPos),
                "Iron", key.outputId(), 1L, key.networkId());

        assertTrue(ProviderLocateRecords.startFor(key).isEmpty()); // Ambiguous without owner.
        assertEquals(List.of(firstPos), ProviderLocateRecords.startFor(key, first).orElseThrow().positions());
        assertEquals(List.of(secondPos), ProviderLocateRecords.startFor(key, second).orElseThrow().positions());
        assertEquals(2, ProviderLocateRecords.snapshotStarts().size());
        var savedStarts = ProviderLocateRecords.snapshotStarts();
        var savedLinks = ProviderLocateRecords.snapshotRecords();
        ProviderLocateRecords.clearAll();
        ProviderLocateRecords.restoreStarts(savedStarts);
        ProviderLocateRecords.restoreRecords(savedLinks);
        assertEquals(List.of(firstPos), ProviderLocateRecords.startFor(key, first).orElseThrow().positions());
        assertEquals(List.of(secondPos), ProviderLocateRecords.startFor(key, second).orElseThrow().positions());
        assertTrue(ProviderLocateRecords.ownedBy(first, firstLink.id()).isPresent());
        assertTrue(ProviderLocateRecords.ownedBy(second, secondLink.id()).isPresent());
        ProviderLocateRecords.replaceStart(key, first, "minecraft:overworld", List.of(), "Iron");
        assertTrue(ProviderLocateRecords.ownedBy(first, firstLink.id()).isEmpty());
        assertTrue(ProviderLocateRecords.ownedBy(second, secondLink.id()).isPresent());
        assertEquals(List.of(secondPos), ProviderLocateRecords.startsForOutput(second, key.outputId()).get(0).positions());
    }

    @Test
    void oldRecordCannotBorrowSoleUnrelatedNetworkProvider() {
        var owner = UUID.randomUUID();
        var output = "minecraft:iron_ingot";
        var record = new LocateRecord(UUID.randomUUID(), owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3)), "Iron", output, 1L);
        var unrelated = new ProviderLocateRecords.StoredStart(new ProfileKey("other-grid", output), owner,
                "minecraft:overworld", List.of(new BlockPos(4, 5, 6)), "Iron", null);
        assertTrue(!ProviderLocateRecords.matchesLegacyStart(record, unrelated));
        var associated = new ProviderLocateRecords.StoredStart(unrelated.key(), owner,
                "minecraft:overworld", record.positions(), "Iron", null);
        assertTrue(ProviderLocateRecords.matchesLegacyStart(record, associated));
        var empty = new ProviderLocateRecords.StoredStart(unrelated.key(), owner,
                "minecraft:overworld", List.of(), "Iron", null);
        assertTrue(!ProviderLocateRecords.matchesLegacyStart(record, empty));
    }

    @Test
    void recordsPersistenceSkipsInvalidEntries() {
        var owner = UUID.randomUUID();
        var valid = new LocateRecord(UUID.randomUUID(), owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3)), "Iron", "minecraft:iron_ingot", 7L);
        var tags = PersistedProviderTag.writeRecords(List.of(valid));

        var badId = new CompoundTag();
        badId.putString("id", "not-a-uuid");
        badId.putString("owner", owner.toString());
        badId.putString("dimension", "minecraft:overworld");
        badId.put("positions", new ListTag());
        badId.putString("name", "Bad");
        badId.putString("outputId", "minecraft:iron_ingot");
        badId.putLong("tick", 1L);
        tags.add(badId);

        var badOwner = new CompoundTag();
        badOwner.putString("id", UUID.randomUUID().toString());
        badOwner.putString("owner", "not-a-uuid");
        badOwner.putString("dimension", "minecraft:overworld");
        badOwner.put("positions", new ListTag());
        badOwner.putString("name", "Bad");
        badOwner.putString("outputId", "minecraft:iron_ingot");
        badOwner.putLong("tick", 2L);
        tags.add(badOwner);

        var badOutput = new CompoundTag();
        badOutput.putString("id", UUID.randomUUID().toString());
        badOutput.putString("owner", owner.toString());
        badOutput.putString("dimension", "minecraft:overworld");
        badOutput.put("positions", new ListTag());
        badOutput.putString("name", "Bad");
        badOutput.putString("outputId", "not an id");
        badOutput.putLong("tick", 3L);
        tags.add(badOutput);

        var longName = new CompoundTag();
        longName.putString("id", UUID.randomUUID().toString());
        longName.putString("owner", owner.toString());
        longName.putString("dimension", "minecraft:overworld");
        longName.put("positions", new ListTag());
        longName.putString("name", "x".repeat(600));
        longName.putString("outputId", "minecraft:stone");
        longName.putLong("tick", 4L);
        tags.add(longName);
        tags.add(new CompoundTag());

        assertEquals(List.of(valid), PersistedProviderTag.readRecords(tags));
        assertTrue(PersistedProviderTag.readRecords(new ListTag()).isEmpty());
    }

    @Test
    void missingDimensionDefaultsToEmptyForStartsAndRecords() {
        var owner = UUID.randomUUID();
        var key = new ProfileKey("net", "minecraft:iron_ingot");

        var startTag = new CompoundTag();
        startTag.putString("networkId", "net");
        startTag.putString("key", "minecraft:iron_ingot");
        startTag.putString("owner", owner.toString());
        startTag.put("positions", new ListTag());
        startTag.putString("name", "Iron");
        var startTags = new ListTag();
        startTags.add(startTag);
        var starts = PersistedProviderTag.readStarts(startTags);
        assertEquals(1, starts.size());
        assertEquals("", starts.get(0).dimensionId());
        assertEquals(key, starts.get(0).key());

        var recordTag = new CompoundTag();
        recordTag.putString("id", UUID.randomUUID().toString());
        recordTag.putString("owner", owner.toString());
        recordTag.put("positions", new ListTag());
        recordTag.putString("name", "Iron");
        recordTag.putString("outputId", "minecraft:iron_ingot");
        recordTag.putLong("tick", 5L);
        var recordTags = new ListTag();
        recordTags.add(recordTag);
        var records = PersistedProviderTag.readRecords(recordTags);
        assertEquals(1, records.size());
        assertEquals("", records.get(0).dimensionId());
        assertEquals("minecraft:iron_ingot", records.get(0).outputId());
    }

    @Test
    void brokenSingleRecordRemovalKeepsOthersAndToleratesNulls() {
        var owner = UUID.randomUUID();
        var keep = ProviderLocateRecords.create(owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3)), "Iron", "minecraft:iron_ingot", 1L);
        var broken = ProviderLocateRecords.create(owner, "minecraft:overworld",
                List.of(new BlockPos(4, 5, 6)), "Copper", "minecraft:copper_plate", 2L);

        ProviderLocateRecords.removeRecord(broken.id());
        assertTrue(ProviderLocateRecords.ownedBy(owner, broken.id()).isEmpty());
        assertTrue(ProviderLocateRecords.ownedBy(owner, keep.id()).isPresent());

        ProviderLocateRecords.removeRecord(null);
        ProviderLocateRecords.removeRecordsForKeys(null, owner);
        ProviderLocateRecords.removeRecordsForKeys(List.of(), owner);
        ProviderLocateRecords.removeRecordsForKeys(null, null);
        assertTrue(ProviderLocateRecords.ownedBy(owner, keep.id()).isPresent());
    }

    @Test
    void foreignRecordLookupExpiresWithoutLeaking() {
        var owner = UUID.randomUUID();
        var foreign = UUID.randomUUID();
        var record = ProviderLocateRecords.create(owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3)), "Iron", "minecraft:iron_ingot", 1L);

        assertTrue(ProviderLocateRecords.ownedBy(foreign, record.id()).isEmpty());
        assertTrue(ProviderLocateRecords.ownedBy(null, record.id()).isEmpty());
        assertTrue(ProviderLocateRecords.ownedBy(owner, null).isEmpty());
        assertTrue(ProviderLocateRecords.ownedBy(owner, UUID.randomUUID()).isEmpty());
        assertTrue(ProviderLocateRecords.ownedBy(owner, record.id()).isPresent());
    }

    @Test
    void cancelledStartsForgetKeysWithoutTouchingOthers() {
        var owner = UUID.randomUUID();
        var iron = new ProfileKey("minecraft:overworld|1,2,3", "minecraft:iron_ingot");
        var copper = new ProfileKey("minecraft:overworld|1,2,3", "minecraft:copper_plate");
        ProviderLocateRecords.noteStart(iron, owner, "minecraft:overworld",
                List.of(new BlockPos(1, 2, 3)), "Iron");
        ProviderLocateRecords.noteStart(copper, owner, "minecraft:overworld",
                List.of(new BlockPos(4, 5, 6)), "Copper");

        ProviderLocateRecords.removeStarts(List.of(iron));
        assertTrue(ProviderLocateRecords.startFor(iron).isEmpty());
        assertTrue(ProviderLocateRecords.startFor(copper).isPresent());

        ProviderLocateRecords.removeStarts(null);
        ProviderLocateRecords.removeStarts(List.of());
        assertTrue(ProviderLocateRecords.startFor(copper).isPresent());
    }
}
