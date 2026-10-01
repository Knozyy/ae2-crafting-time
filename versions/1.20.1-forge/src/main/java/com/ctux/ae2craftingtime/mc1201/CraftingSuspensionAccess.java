package com.ctux.ae2craftingtime.mc1201;

import java.util.UUID;

/** Implemented only by AE2's standard Forge crafting logic. */
public interface CraftingSuspensionAccess {
    boolean ae2craftingtime$suspended();
    UUID ae2craftingtime$jobId();
    boolean ae2craftingtime$setSuspended(boolean desired);
}
