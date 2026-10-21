package qouteall.imm_ptl.core.compat.iris_portal_view.mixin;

import com.mojang.renderpearl.backend.opengl.GlProgram;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisPipelineAccessor {
    @Accessor("loadedShaders") Set<GlProgram> ip_loadedShaders();
}
