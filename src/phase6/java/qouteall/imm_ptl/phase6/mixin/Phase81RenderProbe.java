package qouteall.imm_ptl.phase6.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.phase6.Phase81Probe;

@Mixin(LevelRenderer.class)
public class Phase81RenderProbe {
    @Inject(method="render",at=@At("HEAD"))
    private void phase81_audio(CallbackInfo ci){Phase81Probe.renderSample();}
}
