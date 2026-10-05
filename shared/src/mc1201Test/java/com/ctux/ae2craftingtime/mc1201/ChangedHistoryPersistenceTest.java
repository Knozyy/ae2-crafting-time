package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.*;
import com.ctux.ae2craftingtime.core.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangedHistoryPersistenceTest {
    @Test
    void savedDataAppliesUpdatesAndDeletionsWithoutTouchingOtherOutputs() {
        var data = new Ae2CraftingTimeSavedData();
        var first = new PersistedOutputSamples(new ProfileKey("test:first"), ProfileUnit.ITEM,
                List.of(new PersistedCraftSample(1, 10)));
        var second = new PersistedOutputSamples(new ProfileKey("test:second"), ProfileUnit.ITEM,
                List.of(new PersistedCraftSample(2, 20)));
        data.replaceFrom(List.of(first, second));
        var snapshot = data.samples();
        data.setDirty(false);
        data.updateSamples(List.of());
        assertFalse(data.isDirty());
        data.updateSamples(List.of(new PersistedOutputSamples(first.key(), first.unit(), List.of())));
        assertTrue(data.isDirty());
        assertEquals(List.of(second), data.samples());
        assertSame(second, data.samples().get(0));
        assertEquals(List.of(first, second), snapshot);
    }
}
