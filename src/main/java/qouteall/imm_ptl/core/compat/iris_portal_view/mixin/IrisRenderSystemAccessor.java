package qouteall.imm_ptl.core.compat.iris_portal_view.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
import java.util.Map;

/** Iris injects this program-keyed wrapper cache into RenderSystem. */
@Mixin(value = RenderSystem.class, remap = false)
public interface IrisRenderSystemAccessor {
    @Accessor("iris$overrides")
    static Map<CompiledRenderPipeline, Map<GlProgram, Map<List<VertexFormat>, FrontendRenderPipeline>>> ip_overrides() {
        throw new AssertionError("Mixin accessor");
    }
}
