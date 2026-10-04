package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import com.ctux.ae2craftingtime.core.OptionFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Checks readiness on actual unpowered and powered-empty disposable AE2 grids. */
final class NativeRecurrenceGridBoundary {
    private Future<Boolean> pending;
    private long started;
    private Object screen;
    private Object menu;
    private Map<?, ?> checksBefore;
    private byte[] configBefore;
    private Object recurrence;
    private IManagedGridNode node;
    private ServerPlayer player;
    private BlockPos powerPos;
    private BlockState originalBlock;
    private Set<OptionFeature> disabledBefore;
    private boolean connected;
    private boolean cellPlaced;
    private final ArrayList<Map<String, Object>> states = new ArrayList<>();

    boolean tick(Minecraft minecraft, Class<?> type, Object template, Map<?, ?> checks, Path output) throws Exception {
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        if (started == 0) {
            started = System.nanoTime();
            screen = minecraft.screen;
            menu = minecraft.player.containerMenu;
            checksBefore = Map.copyOf(checks);
            configBefore = Files.readAllBytes(config);
        }
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Native recurrence grid deadline exceeded");
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertEquals(checksBefore, checks);
        assertArrayEquals(configBefore, Files.readAllBytes(config));
        var server = minecraft.getSingleplayerServer();
        assertNotNull(server);
        if (pending == null) {
            var fixture = field(type, "fixture", template);
            var uuid = minecraft.player.getUUID();
            pending = server.submit(() -> {
                try {
                    player = server.getPlayerList().getPlayer(uuid);
                    assertNotNull(player);
                    return advance(fixture);
                } catch (Throwable error) {
                    try { cleanup(); } catch (Throwable secondary) { error.addSuppressed(secondary); }
                    throw new IllegalStateException("Native recurrence grid check failed", error);
                }
            });
        }
        if (!pending.isDone()) return false;
        boolean done = pending.get();
        pending = null;
        if (!done) return false;
        Files.writeString(output.resolve("recurrence-grid-readiness.json"), new com.google.gson.Gson().toJson(Map.of(
                "scope", "actual isolated native grid states; menu screenshot does not prove server-only grid state",
                "states", states)));
        return true;
    }

    private boolean advance(Object fixture) throws Exception {
        if (recurrence == null) {
            disabledBefore = com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current().features().disabled();
            powerPos = ((BlockPos) field(fixture.getClass(), "terminal", fixture)).above(20);
            originalBlock = player.level().getBlockState(powerPos);
            assertTrue(originalBlock.isAir(), "Temporary energy cell must replace verified air only");
            assertNull(player.level().getBlockEntity(powerPos));
            var type = Class.forName("com.ctux.ae2craftingtime.testdriver.RecurrentPlanFixture");
            var constructor = type.getDeclaredConstructor(fixture.getClass());
            constructor.setAccessible(true);
            recurrence = constructor.newInstance(fixture);
            node = GridHelper.createManagedNode(recurrence, (owner, reason) -> {})
                    .setInWorldNode(false).addService(ICraftingProvider.class, (ICraftingProvider) recurrence);
            node.create(player.level(), powerPos);
            set(type, "node", recurrence, node);
            assertFalse(node.isActive());
            assertEquals(false, prepare());
            states.add(Map.of("state", "unpowered", "nodeActive", node.isActive()));
            assertTrue(player.level().setBlockAndUpdate(powerPos,
                    appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState()));
            cellPlaced = true;
            return false;
        }
        var power = assertInstanceOf(appeng.blockentity.grid.AENetworkBlockEntity.class,
                player.level().getBlockEntity(powerPos));
        if (!connected) {
            if (power.getMainNode().getNode() == null) return false;
            GridHelper.createConnection(node.getNode(), power.getMainNode().getNode());
            connected = true;
            return false;
        }
        if (!node.isActive()) return false;
        assertFalse(node.getGrid().getCraftingService().isCraftable(appeng.api.stacks.AEItemKey.of(
                net.minecraft.world.item.Items.SMOOTH_STONE)));
        assertEquals(false, prepare());
        states.add(Map.of("state", "powered-no-patterns", "nodeActive", node.isActive(), "craftable", false));
        cleanup();
        states.add(Map.of("state", "cleaned", "nodeRemoved", true, "blockRestored", true, "featuresRestored", true));
        return true;
    }

    private Object prepare() throws Exception {
        var prepare = recurrence.getClass().getDeclaredMethod("prepare", ServerPlayer.class, String.class);
        prepare.setAccessible(true);
        return prepare.invoke(recurrence, player, "");
    }

    private void cleanup() throws Exception {
        try {
            if (recurrence != null) {
                var close = recurrence.getClass().getDeclaredMethod("close");
                close.setAccessible(true);
                close.invoke(recurrence);
                assertNull(node.getNode());
            }
        } finally {
            if (cellPlaced) {
                player.level().setBlockAndUpdate(powerPos, originalBlock);
                assertEquals(originalBlock, player.level().getBlockState(powerPos));
                assertNull(player.level().getBlockEntity(powerPos));
                cellPlaced = false;
            }
        }
        if (disabledBefore != null) assertEquals(disabledBefore,
                com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current().features().disabled());
    }

    void close(Minecraft minecraft) throws Exception {
        var server = minecraft.getSingleplayerServer();
        if (server != null) server.submit(() -> {
            try { cleanup(); } catch (Exception error) { throw new IllegalStateException(error); }
        }).get(5, TimeUnit.SECONDS);
    }
}
