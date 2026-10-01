package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.core.CraftingSuspension;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.CraftingSuspensionAccess;
import com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingCpuLogic.class)
public abstract class CraftingSuspensionLogicMixin implements CraftingSuspensionAccess {
    @Shadow(remap = false) @Final private CraftingCPUCluster cluster;
    @Unique private final CraftingSuspension ae2craftingtime$suspension = new CraftingSuspension();
    @Unique private static final String ae2craftingtime$tag = "ae2craftingtime:suspended";

    @Override
    public boolean ae2craftingtime$suspended() {
        return ae2craftingtime$supported() && ae2craftingtime$suspension.suspended();
    }

    @Override
    public UUID ae2craftingtime$jobId() {
        if (!ae2craftingtime$supported() || !((CraftingCpuLogic) (Object) this).hasJob())
            return CraftingSuspension.NO_JOB;
        var link = ((CraftingCpuLogic) (Object) this).getLastLink();
        return link == null ? CraftingSuspension.NO_JOB : link.getCraftingID();
    }

    @Override
    public boolean ae2craftingtime$setSuspended(boolean desired) {
        if (!ae2craftingtime$supported()) return false;
        if (ae2craftingtime$suspension.set(desired,
                ServerOptionsRuntime.enabled(OptionFeature.CRAFTING_SUSPENSION),
                ((CraftingCpuLogic) (Object) this).hasJob())) {
            cluster.markDirty();
            ProfilerBridge.setSuspended(cluster, desired, cluster.getLevel().getGameTime(),
                    cluster.getLevel().getServer());
            return true;
        }
        return false;
    }

    @Unique
    private boolean ae2craftingtime$supported() {
        return ((Object) this).getClass() == CraftingCpuLogic.class
                && cluster.getClass() == CraftingCPUCluster.class;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false, cancellable = true)
    private void ae2craftingtime$pauseDispatch(int operations, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        if (ae2craftingtime$suspended()) cir.setReturnValue(0);
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$reconcile(IEnergyService energyService, CraftingService craftingService,
            CallbackInfo ci) {
        if (!ae2craftingtime$supported()) return;
        if (ae2craftingtime$suspension.reconcile(
                ServerOptionsRuntime.enabled(OptionFeature.CRAFTING_SUSPENSION),
                ((CraftingCpuLogic) (Object) this).hasJob())) cluster.markDirty();
        if (ProfilerBridge.isSuspended(cluster) != ae2craftingtime$suspension.suspended())
            ProfilerBridge.setSuspended(cluster, ae2craftingtime$suspension.suspended(),
                    cluster.getLevel().getGameTime(), cluster.getLevel().getServer());
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$newJob(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (ae2craftingtime$supported() && cir.getReturnValue().successful()) {
            ae2craftingtime$suspension.read(false, true);
            ProfilerBridge.setSuspended(cluster, false, cluster.getLevel().getGameTime(),
                    cluster.getLevel().getServer());
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$finish(boolean success, CallbackInfo ci) {
        if (ae2craftingtime$supported()) {
            ae2craftingtime$suspension.read(false, false);
            ProfilerBridge.setSuspended(cluster, false, cluster.getLevel().getGameTime(),
                    cluster.getLevel().getServer());
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$read(CompoundTag data, CallbackInfo ci) {
        ae2craftingtime$suspension.read(ae2craftingtime$supported()
                && data.contains("job", Tag.TAG_COMPOUND)
                && data.getCompound("job").contains(ae2craftingtime$tag, Tag.TAG_BYTE)
                && data.getCompound("job").getBoolean(ae2craftingtime$tag),
                ((CraftingCpuLogic) (Object) this).hasJob());
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$write(CompoundTag data, CallbackInfo ci) {
        if (ae2craftingtime$supported() && data.contains("job", Tag.TAG_COMPOUND))
            data.getCompound("job").putBoolean(ae2craftingtime$tag, ae2craftingtime$suspension.suspended());
    }
}
