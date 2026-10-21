package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerChunkSender.class)
public interface IEPlayerChunkSender {
    @Invoker("sendChunk")
    static void ip_sendChunk(ServerGamePacketListenerImpl listener, ServerLevel world, LevelChunk chunk) {
        throw new AssertionError();
    }
}
