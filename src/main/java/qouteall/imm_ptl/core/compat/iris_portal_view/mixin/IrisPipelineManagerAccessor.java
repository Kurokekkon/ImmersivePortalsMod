package qouteall.imm_ptl.core.compat.iris_portal_view.mixin;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PipelineManager.class, remap = false)
public interface IrisPipelineManagerAccessor {
    @Accessor("pipeline") void ip_setPipeline(WorldRenderingPipeline pipeline);
}
