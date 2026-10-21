package qouteall.imm_ptl.phase6.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.phase6.Phase81Probe;

@Pseudo
@Mixin(targets="de.maxhenkel.voicechat.voice.client.camera.CameraTracker",remap=false)
public class Phase81CameraProbe {
    @Inject(method="update",at=@At("TAIL"))
    private void phase81_camera(CallbackInfo ci){Phase81Probe.cameraUpdated(this);}
}
