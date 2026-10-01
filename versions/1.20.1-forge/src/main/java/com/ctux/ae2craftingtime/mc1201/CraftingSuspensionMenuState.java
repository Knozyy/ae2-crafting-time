package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.CraftingSuspension;

public interface CraftingSuspensionMenuState {
    CraftingSuspension.Snapshot ae2craftingtime$suspensionSnapshot();
    void ae2craftingtime$acceptSuspension(CraftingSuspension.Snapshot snapshot);
}
