package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderPlateStateTest {
    @Test
    void reconnectReplaysOnlyTheRequestedOwnersDeliveredPlates() {
        var state = new ProviderPlateState<Integer, String>(4);
        var other = new ProviderPlateState.Recipient(UUID.randomUUID(), KEY, "overworld");
        var contribution = new ProviderPlateState.Contribution<Integer, String>(List.of(1), "iron");
        state.update(new Object(), Map.of(RECIPIENT, contribution, other, contribution));
        state.pending().forEach(state::delivered);
        state.forgetOwner(OWNER);
        assertEquals(List.of(new ProviderPlateState.Change<>(RECIPIENT, contribution)), state.pending());
    }
    private static final UUID OWNER = UUID.randomUUID();
    private static final ProfileKey KEY = new ProfileKey("grid", "item");
    private static final ProviderPlateState.Recipient RECIPIENT =
            new ProviderPlateState.Recipient(OWNER, KEY, "overworld");

    @Test
    void allEightWarningsAreEligibleWithoutChatOrMenuState() {
        var blocked = new java.util.HashMap<ProfileKey, CraftingBlockReason>();
        int index = 0;
        for (var reason : CraftingBlockReason.values()) {
            blocked.put(new ProfileKey("grid", "blocked-" + index++), reason);
        }
        var delayed = new ProfileKey("grid", "delayed");
        var noSpace = new ProfileKey("grid", "no-space");
        var keys = ProviderWarningKeys.combine(Set.of(delayed), blocked, Set.of(noSpace));
        assertEquals(8, keys.size());
        assertTrue(keys.contains(delayed));
        assertTrue(keys.contains(noSpace));
        assertTrue(keys.containsAll(blocked.keySet()));
        assertTrue(ProviderWarningKeys.combine(Set.of(), Map.of(), Set.of()).isEmpty());
        assertTrue(ProviderWarningKeys.combine(null, null, null).isEmpty());
    }

    @Test
    void overlappingScopesHoldPlateUntilFinalOwnerContributionEnds() {
        var state = new ProviderPlateState<Integer, String>(4);
        var first = new Object();
        var second = new Object();
        state.update(first, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(1), "iron")));
        var added = state.pending().get(0);
        state.delivered(added);
        state.update(second, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(1), "iron")));
        assertTrue(state.pending().isEmpty());
        state.update(first, Map.of());
        assertTrue(state.pending().isEmpty());
        state.update(second, Map.of());
        var removed = state.pending().get(0);
        assertEquals(RECIPIENT, removed.recipient());
        assertEquals(null, removed.plate());
        state.delivered(removed);
        assertTrue(state.pending().isEmpty());
    }

    @Test
    void offlineChangesWaitAndReconnectOnlyReplaysCurrentValidatedTarget() {
        var state = new ProviderPlateState<Integer, String>(4);
        var scope = new Object();
        state.update(scope, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(1), null)));
        // No delivered call while the recipient is offline.
        assertEquals(List.of(1), state.pending().get(0).plate().positions());
        state.update(scope, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(2), null)));
        state.forgetOwner(OWNER);
        assertEquals(List.of(2), state.pending().get(0).plate().positions());
        state.delivered(state.pending().get(0));
        state.update(scope, Map.of()); // Provider invalidated or warning recovered.
        assertEquals(null, state.pending().get(0).plate());
        state.clearAll(); // Server restart cannot replay a remembered status.
        assertTrue(state.pending().isEmpty());
    }

    @Test
    void ownerAndDimensionStayIndependentAndClearKeyPreservesOtherOutput() {
        var state = new ProviderPlateState<Integer, String>(2);
        var scope = new Object();
        var otherOwner = new ProviderPlateState.Recipient(UUID.randomUUID(), KEY, "overworld");
        var otherDimension = new ProviderPlateState.Recipient(OWNER, KEY, "nether");
        var otherOutput = new ProviderPlateState.Recipient(OWNER, new ProfileKey("grid", "other"), "overworld");
        var contribution = new ProviderPlateState.Contribution<Integer, String>(List.of(1), null);
        state.update(scope, Map.of(RECIPIENT, contribution, otherOwner, contribution,
                otherDimension, contribution, otherOutput, contribution));
        assertEquals(4, state.pending().size());
        state.pending().forEach(state::delivered);
        state.clearKey(scope, KEY);
        assertEquals(3, state.pending().size());
        assertTrue(state.pending().stream().allMatch(change -> change.plate() == null));
    }

    @Test
    void unionBoundsPositionsAndRetainsAvailableIcon() {
        var state = new ProviderPlateState<Integer, String>(2);
        var a = new Object();
        var b = new Object();
        state.update(null, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(1), null)));
        assertTrue(state.pending().isEmpty());
        state.clearKey(a, KEY);
        state.update(a, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(1, 2), null)));
        state.update(b, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(2, 3), "iron")));
        var plate = state.pending().get(0).plate();
        assertEquals(2, plate.positions().size());
        assertEquals("iron", plate.displayKey());
        state.update(a, null);
        assertEquals(List.of(2, 3), state.pending().get(0).plate().positions());

        var emptyPositions = new ProviderPlateState.Contribution<Integer, String>(null, "iron");
        assertTrue(emptyPositions.positions().isEmpty());
        state.update(a, Map.of(RECIPIENT, emptyPositions));
        assertEquals("iron", state.pending().get(0).plate().displayKey());
        state.update(a, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(1), null)));
        state.update(b, Map.of(RECIPIENT, new ProviderPlateState.Contribution<>(List.of(2), null)));
        assertEquals(null, state.pending().get(0).plate().displayKey());
    }
}
