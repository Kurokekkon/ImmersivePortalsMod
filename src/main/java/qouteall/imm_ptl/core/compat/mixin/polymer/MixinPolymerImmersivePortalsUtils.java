package qouteall.imm_ptl.core.compat.mixin.polymer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;

import java.util.List;

/** Polymer 0.18.4's 26.3 bridge is shipped as null/false/no-context stubs. */
@Pseudo
@Mixin(targets = "eu.pb4.polymer.virtualentity.impl.compat.ImmersivePortalsUtils", remap = false)
public abstract class MixinPolymerImmersivePortalsUtils {
    @Inject(method = "getPlayerTracking", at = @At("HEAD"), cancellable = true)
    private static void ip_getPlayerTracking(LevelChunk chunk, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        var pos = chunk.getPos();
        cir.setReturnValue(ImmPtlChunkTracking.getPlayersViewingChunk(
            chunk.getLevel().dimension(), pos.x(), pos.z(), false
        ));
    }

    @Inject(method = "isPlayerTracking", at = @At("HEAD"), cancellable = true)
    private static void ip_isPlayerTracking(ServerPlayer player, LevelChunk chunk, CallbackInfoReturnable<Boolean> cir) {
        var pos = chunk.getPos();
        cir.setReturnValue(ImmPtlChunkTracking.isPlayerWatchingChunk(
            player, chunk.getLevel().dimension(), pos.x(), pos.z()
        ));
    }

    @Inject(method = "callRedirected", at = @At("HEAD"), cancellable = true)
    private static void ip_callRedirected(ServerLevel world, Runnable runnable, CallbackInfo ci) {
        PacketRedirection.withForceRedirect(world, runnable);
        ci.cancel();
    }
}
