package qouteall.imm_ptl.core.compat.iris_portal_view.mixin;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_portal_view.IrisViewPipelines;

@Mixin(value = PipelineManager.class, remap = false)
public class MixinIrisPipelineManager {
    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true)
    private void ip_cameraPipeline(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> ci) {
        var selected = IrisViewPipelines.current();
        if (selected != null) ci.setReturnValue(selected);
    }

    @Inject(method = "destroyPipeline", at = @At("HEAD"))
    private void ip_releaseCameraPipelines(CallbackInfo ci) {
        IrisViewPipelines.clear();
    }
}
