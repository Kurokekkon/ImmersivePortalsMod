package qouteall.imm_ptl.core.compat.sodium_compatibility;

import com.mojang.logging.LogUtils;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.util.GameRendererStorage;
import net.caffeinemc.mods.sodium.client.util.FogStorage;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.caffeinemc.mods.sodium.mixin.core.render.world.FrustumAccessor;
import net.minecraft.client.Camera;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;

/**
 * Only loaded when Sodium is present. Uses Sodium's public classes only (no mixins into Sodium).
 */
@Environment(EnvType.CLIENT)
public class SodiumInterfaceOnPresent extends SodiumInterface.Invoker {
    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean projectionWarned = false;

    @Override
    public boolean isSodiumPresent() {
        return true;
    }

    @Override
    public void markSpriteActive(TextureAtlasSprite sprite) {
        SpriteUtil.INSTANCE.markSpriteActive(sprite);
    }

    @Override
    public void onClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {
        ChunkTrackerHolder.get(world).onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
    }

    @Override
    public void onClientChunkUnloaded(ClientLevel world, int chunkX, int chunkZ) {
        // like Sodium's ClientChunkCache.drop hook
        ChunkTrackerHolder.get(world).onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
    }

    @Override
    public boolean supportsSeveralCamerasPerRenderer() {
        // Safe only with PortalViewRenderer's extract-then-draw scheduling below.
        // Active Iris packs use bounded camera-owned pipelines and temporal targets.
        return true;
    }

    @Override
    public boolean requiresSequentialCameraRendering() {
        return true;
    }

    @Override
    public Runnable mainCameraRestorer() {
        Minecraft mc = Minecraft.getInstance();
        LevelRendererExtension extension = (LevelRendererExtension) mc.levelRenderer;
        var renderer = extension.sodium$getWorldRenderer();
        var matrices = extension.sodium$getMatrices();
        Camera camera = mc.gameRenderer.mainCamera();
        var frustum = camera.getCullFrustum();
        var viewport = ((ViewportProvider) frustum).sodium$createViewport();
        var fog = ((FogStorage) mc.gameRenderer).sodium$getFogParameters();
        Matrix4f cullMatrix = new Matrix4f(((FrustumAccessor) frustum).sodium$getMatrix());
        boolean smartCull = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.smartCull;
        return () -> {
            try {
                renderer.setupTerrain(camera, viewport, fog, smartCull, false, cullMatrix);
            }
            finally {
                extension.sodium$setMatrices(matrices);
                // Vanilla ends the main renderer once at the real frame boundary.
                // Remote renderers need the same once-per-frame rotation, never
                // one rotation for every portal sharing a renderer.
                for (var remote : ClientWorldLoader.WORLD_RENDERER_MAP.values()) {
                    if (remote != mc.levelRenderer) {
                        ((LevelRendererExtension) remote).sodium$getWorldRenderer().endFrame();
                    }
                }
            }
        };
    }

    @Override
    public @Nullable Matrix4f swapTerrainProjection(Matrix4fc projection) {
        // GameRendererStorage.sodium$getProjectionMatrix returns the matrix that Sodium copies the projection
        // into in GameRenderer.renderLevel; it is read when LevelRenderer.render prepares the chunk draws
        Matrix4fc stored = ((GameRendererStorage) Minecraft.getInstance().gameRenderer).sodium$getProjectionMatrix();
        if (!(stored instanceof Matrix4f mutable)) {
            if (!projectionWarned) {
                projectionWarned = true;
                LOGGER.error("Cannot set Sodium's terrain projection ({}); portal views may be drawn unclipped", stored.getClass());
            }
            return null;
        }
        Matrix4f previous = new Matrix4f(mutable);
        mutable.set(projection);
        return previous;
    }
}
