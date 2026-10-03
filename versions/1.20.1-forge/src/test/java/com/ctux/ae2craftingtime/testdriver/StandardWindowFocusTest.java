package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandardWindowFocusTest {
    private final HWND window = new HWND(Pointer.createConstant(1));
    private final HWND other = new HWND(Pointer.createConstant(2));
    private final List<String> calls = new ArrayList<>();

    private boolean focus(HWND foreground, int foregroundThread, boolean attached, Runnable request) {
        return StandardAe2Scenario.focusNativeWindow(window,
                () -> { calls.add("foreground"); return foreground; }, this::currentThread,
                handle -> {
                    assertEquals(other, handle);
                    calls.add("foreground-thread");
                    return foregroundThread;
                }, (current, target) -> {
                    assertEquals(new DWORD(10), current);
                    assertEquals(new DWORD(foregroundThread), target);
                    calls.add("attach");
                    return attached;
                }, request, (current, target) -> {
                    assertEquals(new DWORD(10), current);
                    assertEquals(new DWORD(foregroundThread), target);
                    calls.add("detach");
                });
    }

    private DWORD currentThread() {
        calls.add("current-thread");
        return new DWORD(10);
    }

    @Test void foregroundWindowNeedsNoThreadLookupOrFocusRequest() {
        assertTrue(focus(window, 20, true, () -> fail("Already foreground")));
        assertEquals(List.of("foreground"), calls);
    }

    @Test void sameThreadRequestsFocusWithoutAttachingInput() {
        assertFalse(focus(other, 10, true, () -> calls.add("focus")));
        assertEquals(List.of("foreground", "current-thread", "foreground-thread", "focus"), calls);
    }

    @Test void differentThreadDetachesOnlyWhenTheAttachmentSucceeded() {
        for (boolean attached : new boolean[]{true, false}) {
            calls.clear();
            assertFalse(focus(other, 20, attached, () -> calls.add("focus")));
            assertEquals(attached
                    ? List.of("foreground", "current-thread", "foreground-thread", "attach", "focus", "detach")
                    : List.of("foreground", "current-thread", "foreground-thread", "attach", "focus"), calls);
        }
    }

    @Test void aFailedFocusRequestStillReleasesTheAttachedInputThread() {
        var failure = new IllegalStateException("focus failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> focus(other, 20, true, () -> { calls.add("focus"); throw failure; })));
        assertEquals(List.of("foreground", "current-thread", "foreground-thread", "attach", "focus", "detach"), calls);
    }
}
