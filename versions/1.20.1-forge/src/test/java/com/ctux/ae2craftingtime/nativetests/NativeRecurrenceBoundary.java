package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;

/** Recurrence readiness rejects actual non-plan menus and deliberately missing native payloads. */
final class NativeRecurrenceBoundary {
    private Future<Map<String,Object>> pending;
    private long started;
    private boolean clientChecked;
    private Object originalScreen;
    private Object originalMenu;
    private Map<?, ?> originalChecks;
    private byte[] originalConfig;
    private boolean configExists;

    boolean tick(Minecraft minecraft, Class<?> type, Object template, Map<?,?> checks, Path output,
            boolean inventory) throws Exception {
        if (started==0) {
            started=System.nanoTime();
            originalScreen=minecraft.screen;
            originalMenu=minecraft.player.containerMenu;
            originalChecks=Map.copyOf(checks);
            configExists=Files.exists(configPath(minecraft));
            if (configExists) originalConfig=Files.readAllBytes(configPath(minecraft));
        }
        assertTrue(System.nanoTime()-started<30_000_000_000L,"Native recurrence guard exceeded its deadline");
        assertSame(originalScreen,minecraft.screen);
        assertSame(originalMenu,minecraft.player.containerMenu);
        assertEquals(originalChecks,checks);
        assertEquals(configExists,Files.exists(configPath(minecraft)));
        if (configExists) assertArrayEquals(originalConfig,Files.readAllBytes(configPath(minecraft)));
        var recurrence=field(type,"recurrenceFixture",template);
        var fixtureType=recurrence.getClass();
        var validate=fixtureType.getDeclaredMethod("validate",net.minecraft.server.level.ServerPlayer.class);
        validate.setAccessible(true);
        var screen=minecraft.screen;
        var menu=minecraft.player.containerMenu;
        var before=Map.copyOf(checks);
        if (!inventory && !clientChecked) {
            assertInstanceOf(appeng.client.gui.me.crafting.CraftConfirmScreen.class,screen);
            var clientMenu=(appeng.menu.me.crafting.CraftConfirmMenu)menu;
            var plan=clientMenu.getPlan();
            assertNotNull(plan);
            var ready=fixtureType.getDeclaredMethod("clientReady",appeng.menu.me.crafting.CraftConfirmMenu.class);
            ready.setAccessible(true);
            try {
                set(appeng.menu.me.crafting.CraftConfirmMenu.class,"plan",clientMenu,null);
                assertNull(clientMenu.getPlan());
                assertEquals(false,ready.invoke(recurrence,clientMenu));
            } finally { set(appeng.menu.me.crafting.CraftConfirmMenu.class,"plan",clientMenu,plan); }
            assertSame(plan,clientMenu.getPlan());
            clientChecked=true;
        }
        if (pending==null) {
            var server=minecraft.getSingleplayerServer();
            assertNotNull(server);
            var uuid=minecraft.player.getUUID();
            pending=server.submit(() -> {
                try {
                var player=server.getPlayerList().getPlayer(uuid);
                assertNotNull(player);
                var nativeMenu=player.containerMenu;
                if (inventory) {
                    assertSame(player.inventoryMenu,nativeMenu);
                    assertEquals(false,validate.invoke(recurrence,player));
                } else {
                    assertInstanceOf(appeng.menu.me.crafting.CraftConfirmMenu.class,nativeMenu);
                    var confirm=(appeng.menu.me.crafting.CraftConfirmMenu)nativeMenu;
                    var original=confirm.getPlan();
                    var result=field(appeng.menu.me.crafting.CraftConfirmMenu.class,"result",confirm);
                    assertNotNull(original);
                    assertNotNull(result);
                    try {
                        set(appeng.menu.me.crafting.CraftConfirmMenu.class,"result",confirm,null);
                        assertEquals(false,validate.invoke(recurrence,player));
                        set(appeng.menu.me.crafting.CraftConfirmMenu.class,"result",confirm,result);
                        set(appeng.menu.me.crafting.CraftConfirmMenu.class,"plan",confirm,null);
                        assertEquals(false,validate.invoke(recurrence,player));
                    } finally {
                        set(appeng.menu.me.crafting.CraftConfirmMenu.class,"result",confirm,result);
                        set(appeng.menu.me.crafting.CraftConfirmMenu.class,"plan",confirm,original);
                    }
                    assertSame(result,field(appeng.menu.me.crafting.CraftConfirmMenu.class,"result",confirm));
                    assertSame(original,confirm.getPlan());
                }
                assertSame(nativeMenu,player.containerMenu);
                return Map.<String,Object>of("menuClass",nativeMenu.getClass().getName(),"menuId",nativeMenu.containerId,
                        "checks",inventory ? 1 : 3);
                } catch (Exception error) {
                    throw new IllegalStateException("Native recurrence input check failed",error);
                }
            });
        }
        if (!pending.isDone()) return false;
        var result=pending.get();
        assertSame(screen,minecraft.screen);
        assertSame(menu,minecraft.player.containerMenu);
        assertEquals(before,checks);
        Files.writeString(output.resolve(inventory ? "recurrence-non-plan-menu.json" : "recurrence-missing-plan-inputs.json"),
                new com.google.gson.Gson().toJson(Map.of("scope",inventory
                        ? "actual server inventory menu before ordinary preparation"
                        : "deliberately missing native plan payloads, restored without rendering; not screenshot evidence of missing plans",
                        "result",result)));
        return true;
    }

    private static Path configPath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
    }
}
