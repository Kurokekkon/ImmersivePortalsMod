package qouteall.imm_ptl.phase6.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.phase6.Phase81Probe;
import java.util.UUID;

@Pseudo
@Mixin(targets="de.maxhenkel.voicechat.voice.client.AudioChannel",remap=false)
public class Phase81AudioProbe {
    @WrapOperation(method="writeToSpeaker",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"))
    private Player phase81_lookup(ClientLevel level,UUID id,Operation<Player> original){Player player=original.call(level,id);Phase81Probe.lookup(level,player);return player;}
}
