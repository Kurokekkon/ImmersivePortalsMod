package qouteall.imm_ptl.phase6.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.mixin.client.portal_view.GameRendererAccessor;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;
import qouteall.imm_ptl.phase6.Phase81Probe;

@Mixin(PortalViewRenderer.class)
public class Phase81ViewProbe {
    @Inject(method="renderView",at=@At("TAIL"))
    private static void phase81_updated(@Coerce Object view,RenderTarget target,GameRendererAccessor renderer,GameRenderState state,CallbackInfo ci){Phase81Probe.rendered(view);}
    @Inject(method="drawPortalArea",at=@At("HEAD"))
    private static void phase81_drawn(@Coerce Object view,RenderTarget target,CameraRenderState camera,CallbackInfo ci){Phase81Probe.drawn(view);}
}
