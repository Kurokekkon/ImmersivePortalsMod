package qouteall.imm_ptl.phase6.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.phase6.Phase81Probe;

@Mixin(GameRenderer.class)
public class Phase81FrameProbe {
    @Inject(method="render",at=@At("HEAD"))
    private void phase81_frame(CallbackInfo ci){Phase81Probe.nextFrame();}
}
