package qouteall.imm_ptl.phase6.mixin;

import qouteall.imm_ptl.phase6.Control;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Measures every actual IP chunk construction, including after Polymer's one-time warning. */
@Mixin(ClientboundLevelChunkPacketData.class)
public class ChunkPacketContextProbe {
    private static long missing,present;
    private static final Map<String,Long> profiles=new TreeMap<>(),dimensions=new TreeMap<>();
    @Inject(method="<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V",at=@At("HEAD"))
    private static void probe(LevelChunk chunk,CallbackInfo ci){
        boolean ours=StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals("qouteall.imm_ptl.core.chunk_loading.PlayerChunkLoading")));
        if(!ours)return;
        var context=PacketContext.get();
        if(context==null)missing++;
        else{
            present++;var profile=context.get(PacketContext.GAME_PROFILE);
            profiles.merge(profile==null?"NO_PROFILE":profile.id().toString(),1L,Long::sum);
        }
        dimensions.merge(chunk.getLevel().dimension().identifier().toString(),1L,Long::sum);
        Control.write("chunk-context-probe.json",Map.of("pid",ProcessHandle.current().pid(),"missing",missing,"present",present,"profiles",profiles,"dimensions",dimensions));
    }
}
