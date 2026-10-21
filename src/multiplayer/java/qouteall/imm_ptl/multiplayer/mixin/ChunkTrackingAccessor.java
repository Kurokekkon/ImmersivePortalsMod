package qouteall.imm_ptl.multiplayer.mixin;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.chunk_loading.PlayerChunkLoading;

@Mixin(value = ImmPtlChunkTracking.class, remap = false)
public interface ChunkTrackingAccessor {
    @Accessor("playerInfoMap")
    static Object2ObjectOpenHashMap<ServerPlayer, PlayerChunkLoading> players() { throw new AssertionError(); }
}
