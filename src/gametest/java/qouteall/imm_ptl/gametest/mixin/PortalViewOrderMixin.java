package qouteall.imm_ptl.gametest.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Test-only order perturbation; never included in the shipped mod. */
@Mixin(value = PortalViewRenderer.class, remap = false)
public class PortalViewOrderMixin {
    @Inject(method = "renderViews", at = @At("HEAD"))
    private static void beforeShaderViews(CallbackInfo ci) {
        if (System.getProperty("imm_ptl.test.selection", "").startsWith("phase5"))
            qouteall.imm_ptl.gametest.IrisTestSupport.beforeViews();
    }

    @Inject(method = "renderViews", at = @At("RETURN"))
    private static void afterShaderViews(CallbackInfo ci) {
        if (System.getProperty("imm_ptl.test.selection", "").startsWith("phase5"))
            qouteall.imm_ptl.gametest.IrisTestSupport.afterViews();
    }
    @Inject(method = "collectPortalsToRender", at = @At("RETURN"), cancellable = true)
    private static void reverse(ClientLevel level, Camera camera, CallbackInfoReturnable<List<Portal>> cir) {
        if (Boolean.getBoolean("imm_ptl.test.reverseViews")) {
            List<Portal> reversed = new ArrayList<>(cir.getReturnValue());
            Collections.reverse(reversed);
            cir.setReturnValue(reversed);
        }
    }
}
