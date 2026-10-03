package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandardWindowFocusTest {
    private final HWND window = new HWND(Pointer.createConstant(1));
    private final HWND other = new HWND(Pointer.createConstant(2));
    private final List<String> calls = new ArrayList<>();

    private User32 user(HWND foreground, int foregroundThread, boolean attach) {
        return (User32) Proxy.newProxyInstance(User32.class.getClassLoader(), new Class<?>[]{User32.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "GetForegroundWindow" -> { calls.add("foreground"); yield foreground; }
                    case "GetWindowThreadProcessId" -> {
                        assertEquals(other, args[0]);
                        assertNull(args[1]);
                        calls.add("foreground-thread");
                        yield foregroundThread;
                    }
                    case "AttachThreadInput" -> {
                        assertEquals(new DWORD(10), args[0]);
                        assertEquals(new DWORD(foregroundThread), args[1]);
                        calls.add((boolean) args[2] ? "attach" : "detach");
                        yield attach;
                    }
                    default -> throw new AssertionError("Unexpected native call: " + method.getName());
                });
    }

    private DWORD currentThread() {
        calls.add("current-thread");
        return new DWORD(10);
    }

    @Test void foregroundWindowNeedsNoThreadLookupOrFocusRequest() {
        assertTrue(StandardAe2Scenario.focusNativeWindow(window, user(window, 20, true),
                () -> { fail("Already foreground"); return null; }, () -> fail("Already foreground")));
        assertEquals(List.of("foreground"), calls);
    }

    @Test void sameThreadRequestsFocusWithoutAttachingInput() {
        assertFalse(StandardAe2Scenario.focusNativeWindow(window, user(other, 10, true),
                this::currentThread, () -> calls.add("focus")));
        assertEquals(List.of("foreground", "current-thread", "foreground-thread", "focus"), calls);
    }

    @Test void differentThreadDetachesOnlyWhenTheAttachmentSucceeded() {
        for (boolean attached : new boolean[]{true, false}) {
            calls.clear();
            assertFalse(StandardAe2Scenario.focusNativeWindow(window, user(other, 20, attached),
                    this::currentThread, () -> calls.add("focus")));
            assertEquals(attached
                    ? List.of("foreground", "current-thread", "foreground-thread", "attach", "focus", "detach")
                    : List.of("foreground", "current-thread", "foreground-thread", "attach", "focus"), calls);
        }
    }

    @Test void aFailedFocusRequestStillReleasesTheAttachedInputThread() {
        var failure = new IllegalStateException("focus failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.focusNativeWindow(window, user(other, 20, true),
                        this::currentThread, () -> { calls.add("focus"); throw failure; })));
        assertEquals(List.of("foreground", "current-thread", "foreground-thread", "attach", "focus", "detach"), calls);
    }
}
