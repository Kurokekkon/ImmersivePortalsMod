package qouteall.imm_ptl.core.compat.iris_portal_view;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import qouteall.imm_ptl.core.compat.iris_portal_view.mixin.IrisPipelineManagerAccessor;
import qouteall.imm_ptl.core.compat.iris_portal_view.mixin.IrisPipelineAccessor;
import qouteall.imm_ptl.core.compat.iris_portal_view.mixin.IrisRenderSystemAccessor;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Render-thread-only camera ownership. No pipeline creation on a steady-state frame. */
public final class IrisViewPipelines {
    private record Entry(NamespacedId dimension, IrisRenderingPipeline pipeline) {}
    private static final LinkedHashMap<UUID, Entry> VIEWS = new LinkedHashMap<>(4, .75f, true);
    private static @Nullable IrisRenderingPipeline active;

    public static @Nullable WorldRenderingPipeline current() { return active; }
    public static int size() { return VIEWS.size(); }

    public static Runnable begin(UUID id) {
        if (Iris.getCurrentPack().isEmpty()) return () -> {};
        if (active != null) throw new IllegalStateException("Nested Iris portal view");
        var manager = Iris.getPipelineManager();
        var previous = manager.getPipelineNullable();
        var access = (IrisPipelineManagerAccessor) manager;
        // These are singleton scratch values, unlike each pipeline's owned temporal resources.
        Runnable restoreCaptured = preserveGlobals();
        try {
            NamespacedId dimension = Iris.getCurrentDimension();
            Entry entry = VIEWS.get(id);
            if (entry != null && !entry.dimension.equals(dimension)) { release(id); entry = null; }
            if (entry == null) {
                while (VIEWS.size() >= PortalViewRenderer.MAX_VIEWS) release(VIEWS.keySet().iterator().next());
                var pipeline = new IrisRenderingPipeline(Iris.getCurrentPack().orElseThrow().getProgramSet(dimension));
                entry = new Entry(dimension, pipeline);
                VIEWS.put(id, entry);
            }
            active = entry.pipeline;
            access.ip_setPipeline(active);
        }
        catch (RuntimeException | Error failure) {
            active = null;
            access.ip_setPipeline(previous);
            restoreCaptured.run();
            throw failure;
        }
        return () -> {
            active = null;
            access.ip_setPipeline(previous);
            restoreCaptured.run();
        };
    }

    public static void release(UUID id) {
        Entry removed = VIEWS.remove(id);
        if (removed != null) {
            // Iris' wrapper cache outlives its pipelines. Remove only this camera's
            // programs and release wrapper VAOs; the Iris pipeline owns the programs.
            var programs = ((IrisPipelineAccessor) removed.pipeline).ip_loadedShaders();
            try {
                for (var wrappers : IrisRenderSystemAccessor.ip_overrides().values()) {
                    for (var program : programs) {
                        var formats = wrappers.remove(program);
                        if (formats != null) for (var wrapper : formats.values()) {
                            ((GlRenderPipeline) wrapper.backendRenderPipeline()).vertexArray().close();
                        }
                    }
                }
            }
            finally { removed.pipeline.destroy(); }
        }
    }

    public static void clear() {
        if (active != null) throw new IllegalStateException("Reload during Iris portal rendering");
        RuntimeException error = null;
        for (UUID id : VIEWS.keySet().toArray(UUID[]::new)) {
            try { release(id); }
            catch (RuntimeException e) { if (error == null) error=e; else error.addSuppressed(e); }
        }
        if (error != null) throw error;
    }

    public static Runnable preserveGlobals() {
        var s = CapturedRenderingState.INSTANCE;
        var model = new Matrix4f(s.getGbufferModelView());
        var projection = new Matrix4f(s.getGbufferProjection());
        var fog = new org.joml.Vector3d(s.getFogColor());
        float density=s.getFogDensity(), tick=s.getTickDelta(), realTick=s.getRealTickDelta();
        float darkness=s.getDarknessLightFactor(), alpha=s.getCurrentAlphaTest(), cloud=s.getCloudTime();
        int block=s.getCurrentRenderedBlockEntity(), entity=s.getCurrentRenderedEntity(), item=s.getCurrentRenderedItem();
        var shadowModel=ShadowRenderer.MODELVIEW;
        var shadowProjection=ShadowRenderer.PROJECTION;
        var shadowFrustum=ShadowRenderer.FRUSTUM;
        var shadowEntities=ShadowRenderer.visibleBlockEntities;
        int resolution=ShadowRenderer.RESOLUTION, distance=ShadowRenderer.renderDistance;
        boolean shadowActive=ShadowRenderer.ACTIVE;
        return () -> {
            s.setGbufferModelView(model); s.setGbufferProjection(projection);
            s.setFogColor((float)fog.x,(float)fog.y,(float)fog.z); s.setFogDensity(density);
            s.setTickDelta(tick); s.setRealTickDelta(realTick); s.setDarknessLightFactor(darkness);
            s.setCurrentAlphaTest(alpha); s.setCloudTime(cloud);
            s.setCurrentBlockEntity(block); s.setCurrentEntity(entity); s.setCurrentRenderedItem(item);
            ShadowRenderer.MODELVIEW=shadowModel; ShadowRenderer.PROJECTION=shadowProjection;
            ShadowRenderer.FRUSTUM=shadowFrustum; ShadowRenderer.visibleBlockEntities=shadowEntities;
            ShadowRenderer.RESOLUTION=resolution; ShadowRenderer.renderDistance=distance; ShadowRenderer.ACTIVE=shadowActive;
        };
    }
}
