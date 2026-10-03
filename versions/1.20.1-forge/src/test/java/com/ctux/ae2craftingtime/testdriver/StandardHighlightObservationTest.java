package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.mc1201.ProviderHighlightClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardHighlightObservationTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String STONE = "minecraft:stone";
    private static final String SMOOTH = "minecraft:smooth_stone";
    private final BlockPos terminal = new BlockPos(10, 20, 30);
    private StandardAe2Scenario scenario;

    @BeforeEach void startWithAnEmptySession() throws Exception {
        ProviderHighlightClient.onSessionEnd();
        scenario = new StandardAe2Scenario("waiting-status", "world", Path.of("unused"), false);
        var field = StandardAe2Scenario.class.getDeclaredField("fixture");
        field.setAccessible(true);
        ((StandardCraftFixture) field.get(scenario)).bindTerminal(terminal);
    }

    @AfterEach void clearSession() { ProviderHighlightClient.onSessionEnd(); }

    @Test void platesRequireBothTheRequestedOutputAndProviderPosition() throws Exception {
        assertEquals(false, call("hasPlate", STONE, 4));
        ProviderHighlightClient.showPlate(DIMENSION, List.of(terminal.east(6)), STONE);
        assertEquals(false, call("hasPlate", SMOOTH, 6));
        assertEquals(false, call("hasPlate", STONE, 4));
        assertEquals(true, call("hasPlate", STONE, 6));
        ProviderHighlightClient.showPlate(DIMENSION, List.of(terminal.east(4), terminal.east(6)), STONE);
        assertEquals(true, call("hasPlate", STONE, 4));
        assertEquals(false, call("hasPlate", STONE, 8));
        clearSession();
        assertEquals(false, call("hasPlate", STONE, 4));
    }

    @Test void edgesRequireBothTheRequestedOutputAndProviderPosition() throws Exception {
        assertEquals(false, call("hasEdge", STONE, 4));
        ProviderHighlightClient.show(DIMENSION, List.of(terminal.east(6)), 60, STONE);
        assertEquals(false, call("hasEdge", SMOOTH, 6));
        assertEquals(false, call("hasEdge", STONE, 4));
        assertEquals(true, call("hasEdge", STONE, 6));
        ProviderHighlightClient.show(DIMENSION, List.of(terminal.east(4), terminal.east(6)), 60, STONE);
        assertEquals(true, call("hasEdge", STONE, 4));
        assertEquals(false, call("hasEdge", STONE, 8));
        clearSession();
        assertEquals(false, call("hasEdge", STONE, 4));
    }

    @Test void rememberedAndRenderedOutputsStayBoundToTheirProvider() throws Exception {
        for (var query : List.of("plateOutputsAt", "renderOutputsAt")) assertEquals(Set.of(), call(query, 4));
        ProviderHighlightClient.showPlate(DIMENSION, List.of(terminal.east(4)), STONE);
        ProviderHighlightClient.showPlate(DIMENSION, List.of(terminal.east(6)), SMOOTH);
        for (var query : List.of("plateOutputsAt", "renderOutputsAt")) {
            assertEquals(Set.of(STONE), call(query, 4));
            assertEquals(Set.of(SMOOTH), call(query, 6));
            assertEquals(Set.of(), call(query, 8));
        }
    }

    @Test void beamsRequireAChatLocateAndTheMatchingProvider() throws Exception {
        assertEquals(false, call("hasBeam", 4));
        ProviderHighlightClient.show(DIMENSION, List.of(terminal.east(4)), 60, STONE);
        assertEquals(false, call("hasBeam", 4));
        ProviderHighlightClient.show("network", DIMENSION, List.of(terminal.east(6)), 60, SMOOTH, true);
        assertEquals(false, call("hasBeam", 4));
        assertEquals(true, call("hasBeam", 6));
        ProviderHighlightClient.show("network", DIMENSION, List.of(terminal.east(4)), 60, STONE, true);
        assertEquals(true, call("hasBeam", 4));
        assertEquals(false, call("hasBeam", 8));
        clearSession();
        assertEquals(false, call("hasBeam", 4));
    }

    private Object call(String name, Object... arguments) throws Exception {
        var types = java.util.Arrays.stream(arguments)
                .map(value -> value instanceof String ? String.class : int.class).toArray(Class<?>[]::new);
        var method = StandardAe2Scenario.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(scenario, arguments);
    }
}
