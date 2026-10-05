package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.*;
import com.ctux.ae2craftingtime.core.ProfileKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ProviderPersistenceChangesTest {
    @Test
    void readsStayCleanAndMutationsIncludeOwnedClickFallbacks() {
        ProviderLocateRecords.clearAll();
        assertTrue(ProviderLocateRecords.takeDirty());
        var key = new ProfileKey("grid", "test:output");
        var owner = UUID.randomUUID();
        ProviderLocateRecords.noteStart(key, owner, List.of(), "output");
        assertTrue(ProviderLocateRecords.takeDirty());
        assertTrue(ProviderLocateRecords.snapshotStarts().isEmpty());
        ProviderLocateRecords.startFor(key, owner);
        ProviderLocateRecords.snapshotRecords();
        assertFalse(ProviderLocateRecords.takeDirty());
        var record = ProviderLocateRecords.create(owner, "dimension", List.of(BlockPos.ZERO),
                "output", key.outputId(), 10, key.networkId());
        assertTrue(ProviderLocateRecords.takeDirty());
        assertEquals(1, ProviderLocateRecords.snapshotStarts().size());
        ProviderLocateRecords.removeRecord(record.id());
        assertTrue(ProviderLocateRecords.takeDirty());
        assertTrue(ProviderLocateRecords.snapshotStarts().isEmpty());
        ProviderLocateRecords.clearAll();
    }
}
