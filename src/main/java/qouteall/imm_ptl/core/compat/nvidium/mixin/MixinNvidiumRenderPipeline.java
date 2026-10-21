package qouteall.imm_ptl.core.compat.nvidium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.cortex.nvidium.RenderPipeline;
import me.cortex.nvidium.gl.buffers.IDeviceMappedBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.portal_view.PortalViewCamera;

/** Nvidium 0.4.4-beta7's temporal command buffers are a single-camera history. */
@Mixin(value = RenderPipeline.class, remap = false)
public class MixinNvidiumRenderPipeline {
    @Shadow private int prevRegionCount;
    @Shadow @Final private IDeviceMappedBuffer sectionVisibility;
    @Unique private boolean ip_previousWasPortal;
    @Unique private boolean ip_freshCamera;

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void ip_beginCamera(CallbackInfo ci) {
        boolean portal = Minecraft.getInstance().gameRenderer.mainCamera() instanceof PortalViewCamera;
        ip_freshCamera = portal || ip_previousWasPortal;
        ip_previousWasPortal = portal;
        if (!ip_freshCamera) return;

        // Do not draw the previous camera's command list into this camera's target.
        // With a zero history, Nvidium's current-frame temporal pass draws every
        // currently visible section, rather than only newly revealed sections.
        prevRegionCount = 0;
        int barriers = GL43C.GL_SHADER_STORAGE_BARRIER_BIT | GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
        GL42C.glMemoryBarrier(barriers);
        GL45C.nglClearNamedBufferData(sectionVisibility.getId(), GL30C.GL_R8UI,
            GL30C.GL_RED_INTEGER, GL11C.GL_UNSIGNED_BYTE, 0L);
        GL42C.glMemoryBarrier(barriers);
    }

    @ModifyExpressionValue(method = "renderFrame", at = @At(value = "FIELD",
        target = "Lme/cortex/nvidium/config/NvidiumConfig;enable_temporal_coherence:Z"), require = 2)
    private boolean ip_drawFreshVisibility(boolean configured) {
        // Required for a cold camera even if the user turned temporal coherence off.
        // This changes only this render invocation, never the user's global config.
        return configured || ip_freshCamera;
    }
}
