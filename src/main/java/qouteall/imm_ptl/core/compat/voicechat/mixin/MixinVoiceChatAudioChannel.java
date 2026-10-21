package qouteall.imm_ptl.core.compat.voicechat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Voice Chat's audio thread resolves PlayerSoundPacket senders through Minecraft.level.
 * Portal drawing temporarily changes that singleton, but never the local player's level.
 * Use the player's physical world for both the primary and fallback speaker lookup;
 * leave CameraTracker, plugin overrides, positional audio and transport untouched.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.AudioChannel", remap = false)
public class MixinVoiceChatAudioChannel {
    @ModifyExpressionValue(
        method = "writeToSpeaker",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;")
    )
    private ClientLevel ip_physicalSpeakerWorld(ClientLevel temporaryLevel) {
        var player = Minecraft.getInstance().player;
        return player == null ? temporaryLevel : (ClientLevel) player.level();
    }
}
