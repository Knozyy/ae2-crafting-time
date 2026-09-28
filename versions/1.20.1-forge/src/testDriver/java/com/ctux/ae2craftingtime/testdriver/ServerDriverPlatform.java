package com.ctux.ae2craftingtime.testdriver;

final class ServerDriverPlatform {
    static boolean isResourceChunkForced(net.minecraft.server.level.ServerLevel level, int x, int z) {
        return level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x, z));
    }
    static appeng.api.stacks.AEKey bucketlessResourceKey() {
        return ResourceFixtureFluid.key();
    }
    static java.util.Map<String, Object> resourceFacts(ResourceFixtureControl.Case resourceCase) {
        return resourceCase == ResourceFixtureControl.Case.BUCKETLESS ? ResourceFixtureFluid.facts()
                : java.util.Map.of("storageValidated", true,
                        "chemical", resourceCase.name().contains("GEN") || resourceCase.name().startsWith("CHEMICAL"));
    }
    static byte[] encodeResourceKey(appeng.api.stacks.AEKey key, net.minecraft.server.level.ServerPlayer player) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var output = new java.io.DataOutputStream(bytes)) {
                net.minecraft.nbt.NbtIo.write(key.toTagGeneric(), output);
            }
            return bytes.toByteArray();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("cannot encode resource key", error);
        }
    }
    static boolean isModLoaded(String id) {
        return net.minecraftforge.fml.ModList.get().isLoaded(id);
    }
    static void installResourceStorage(appeng.blockentity.storage.DriveBlockEntity drive, boolean chemical) {
        var inventory = drive.getInternalInventory();
        if (inventory.getStackInSlot(1).isEmpty()) {
            inventory.setItemDirect(1, appeng.core.definitions.AEItems.FLUID_CELL_1K.stack());
            drive.onChangeInventory(inventory, 1);
        }
        if (chemical && inventory.getStackInSlot(2).isEmpty()) {
            if (!isModLoaded("appmek")) throw new IllegalStateException("AppMek resource fixture requires appmek");
            inventory.setItemDirect(2, AppliedMekanisticsFixture.resourceCell());
            drive.onChangeInventory(inventory, 2);
        }
        drive.getMainNode().getGrid().getStorageService().invalidateCache();
    }

    static WirelessTerminalFixture wcwtTerminal() {
        return new WcwtTerminalFixture();
    }

    static net.minecraft.world.item.ItemStack processingPattern(appeng.api.stacks.GenericStack input,
            appeng.api.stacks.GenericStack output) {
        return appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                new appeng.api.stacks.GenericStack[] {input}, new appeng.api.stacks.GenericStack[] {output});
    }
    static net.minecraft.world.item.ItemStack processingPattern(java.util.List<appeng.api.stacks.GenericStack> inputs,
            appeng.api.stacks.GenericStack output) {
        return appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(appeng.api.stacks.GenericStack[]::new), new appeng.api.stacks.GenericStack[] {output});
    }
    static net.minecraft.world.item.ItemStack processingPattern(java.util.List<appeng.api.stacks.GenericStack> inputs,
            java.util.List<appeng.api.stacks.GenericStack> outputs) {
        return appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(appeng.api.stacks.GenericStack[]::new),
                outputs.toArray(appeng.api.stacks.GenericStack[]::new));
    }
    static appeng.api.crafting.IPatternDetails substitutePattern(appeng.api.crafting.IPatternDetails original,
            appeng.api.stacks.AEKey substitute) {
        return new appeng.api.crafting.IPatternDetails() {
            public appeng.api.stacks.AEItemKey getDefinition() { return original.getDefinition(); }
            public appeng.api.stacks.GenericStack[] getOutputs() { return original.getOutputs(); }
            public IInput[] getInputs() {
                var input = original.getInputs()[0];
                return new IInput[] { new IInput() {
                    public appeng.api.stacks.GenericStack[] getPossibleInputs() {
                        return new appeng.api.stacks.GenericStack[] { input.getPossibleInputs()[0],
                                new appeng.api.stacks.GenericStack(substitute, input.getPossibleInputs()[0].amount()) };
                    }
                    public long getMultiplier() { return input.getMultiplier(); }
                    public boolean isValid(appeng.api.stacks.AEKey key, net.minecraft.world.level.Level level) {
                        return substitute.equals(key) || input.isValid(key, level);
                    }
                    public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey key) { return input.getRemainingKey(key); }
                } };
            }
        };
    }
    private ServerDriverPlatform() {}
}
