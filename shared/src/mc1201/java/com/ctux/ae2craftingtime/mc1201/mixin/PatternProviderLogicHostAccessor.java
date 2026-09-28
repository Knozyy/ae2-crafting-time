package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicHostAccessor {
    @Accessor(value = "host", remap = false)
    PatternProviderLogicHost ae2craftingtime$getHost();
}
