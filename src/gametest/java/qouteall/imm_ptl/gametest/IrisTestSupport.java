package qouteall.imm_ptl.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.compat.iris_portal_view.IrisViewPipelines;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;
import java.nio.file.Files;
import java.util.UUID;

public class IrisTestSupport {
    private record Snapshot(Object pipeline,Object camera,Object target,Object lightmap,Object projection,
        Object fog,Object globals,org.joml.Matrix4f model,org.joml.Matrix4f irisProjection,
        Object shadowModel,Object shadowProjection,Object shadowFrustum,boolean shadowActive,
        java.util.concurrent.atomic.AtomicLong mainUpdates,long updateCount,
        org.joml.Vector3d capturedFog,float density,float darkness,float tick,float cloud) {}
    private static Snapshot snapshot;
    private static long started,frames,nanos;
    private static float actualDarkness;
    private static final java.util.Map<IrisRenderingPipeline,java.util.concurrent.atomic.AtomicLong> updates=new java.util.WeakHashMap<>();
    public static void beforeViews() {
        if(Iris.getCurrentPack().isEmpty() || PortalViewRenderer.getViewCountThisFrame()==0) {snapshot=null;return;}
        var mc=net.minecraft.client.Minecraft.getInstance();
        var access=(qouteall.imm_ptl.core.mixin.client.portal_view.GameRendererAccessor)mc.gameRenderer;
        var state=net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE;
        // Test-only canary: extraction must not overwrite an already drawn main view's scratch state.
        actualDarkness=state.getDarknessLightFactor();
        if(System.getProperty("imm_ptl.test.selection", "").equals("phase5_state_probe")) state.setDarknessLightFactor(.137f);
        var main=(IrisRenderingPipeline)Iris.getPipelineManager().getPipelineNullable();
        var counter=updates.computeIfAbsent(main,p -> {
            var count=new java.util.concurrent.atomic.AtomicLong();p.getFrameUpdateNotifier().addListener(count::incrementAndGet);return count;
        });
        snapshot=new Snapshot(main,mc.gameRenderer.mainCamera(),
            access.ip_getMainRenderTarget(),access.ip_getLightmap(),com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrixBuffer(),
            com.mojang.blaze3d.systems.RenderSystem.getShaderFog(),com.mojang.blaze3d.systems.RenderSystem.getGlobalSettingsUniform(),
            new org.joml.Matrix4f(state.getGbufferModelView()),new org.joml.Matrix4f(state.getGbufferProjection()),
            net.irisshaders.iris.shadows.ShadowRenderer.MODELVIEW,net.irisshaders.iris.shadows.ShadowRenderer.PROJECTION,
            net.irisshaders.iris.shadows.ShadowRenderer.FRUSTUM,net.irisshaders.iris.shadows.ShadowRenderer.ACTIVE,counter,counter.get(),
            new org.joml.Vector3d(state.getFogColor()),state.getFogDensity(),state.getDarknessLightFactor(),state.getTickDelta(),state.getCloudTime());
        started=System.nanoTime();
    }
    public static void afterViews() {
        if(snapshot==null)return;
        var s=snapshot;snapshot=null;
        var mc=net.minecraft.client.Minecraft.getInstance();
        var a=(qouteall.imm_ptl.core.mixin.client.portal_view.GameRendererAccessor)mc.gameRenderer;
        var state=net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE;
        check(s.pipeline==Iris.getPipelineManager().getPipelineNullable(),"Main Iris pipeline leaked");
        check(s.mainUpdates.get()==s.updateCount,"Portal advanced main camera temporal notifier");
        check(s.camera==mc.gameRenderer.mainCamera() && s.target==a.ip_getMainRenderTarget() && s.lightmap==a.ip_getLightmap(),"Camera/target/lightmap leaked");
        check(s.projection==com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrixBuffer()
            &&s.fog==com.mojang.blaze3d.systems.RenderSystem.getShaderFog()
            &&s.globals==com.mojang.blaze3d.systems.RenderSystem.getGlobalSettingsUniform(),"GPU projection/fog/globals leaked");
        check(s.model.equals(state.getGbufferModelView(),1e-6f)&&s.irisProjection.equals(state.getGbufferProjection(),1e-6f),"Iris captured matrices leaked");
        check(s.capturedFog.equals(state.getFogColor(),1e-6)&&s.density==state.getFogDensity()
            &&s.darkness==state.getDarknessLightFactor()&&s.tick==state.getTickDelta()&&s.cloud==state.getCloudTime(),
            "Iris extraction scratch state leaked: darkness="+s.darkness+" -> "+state.getDarknessLightFactor());
        check(s.shadowModel==net.irisshaders.iris.shadows.ShadowRenderer.MODELVIEW
            &&s.shadowProjection==net.irisshaders.iris.shadows.ShadowRenderer.PROJECTION
            &&s.shadowFrustum==net.irisshaders.iris.shadows.ShadowRenderer.FRUSTUM
            &&s.shadowActive==net.irisshaders.iris.shadows.ShadowRenderer.ACTIVE,"Shadow globals leaked");
        if(System.getProperty("imm_ptl.test.selection", "").equals("phase5_state_probe")) state.setDarknessLightFactor(actualDarkness);
        frames++;nanos+=System.nanoTime()-started;
    }
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }

    static void single(ClientGameTestContext context,UUID id,Vec3 origin) {
        try(var in=Files.newInputStream(context.takeScreenshot("phase5_single"));var image=NativeImage.read(in)) {
            int[] p=context.computeOnClient(mc -> SodiumMulticameraTests.project(mc,origin,image.getWidth(),image.getHeight()));
            int c=image.getPixel(p[0],p[1]);
            check(ARGB.green(c)>70 && ARGB.green(c)>ARGB.red(c)*1.3,"Shader emerald destination missing: "+Integer.toHexString(c));
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        active(context,"single");
    }

    static void active(ClientGameTestContext context,String label) {
        context.runOnClient(mc -> {
            check(Iris.getCurrentPack().isPresent() && Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline,"Inactive shader pipeline");
            check(IrisViewPipelines.size()<=2 && IrisViewPipelines.current()==null,"Iris camera ownership leaked");
            try {
                var map=IrisViewPipelines.class.getDeclaredField("VIEWS");map.setAccessible(true);
                var targets=IrisRenderingPipeline.class.getDeclaredField("renderTargets");targets.setAccessible(true);
                var owners=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                owners.add(targets.get(Iris.getPipelineManager().getPipelineNullable()));
                for(Object entry:((java.util.Map<?,?>)map.get(null)).values()) {
                    var getter=entry.getClass().getDeclaredMethod("pipeline");getter.setAccessible(true);
                    check(owners.add(targets.get(getter.invoke(entry))),"Temporal render targets shared by cameras");
                }
            } catch(ReflectiveOperationException e){throw new AssertionError("Cannot inspect temporal ownership",e);}
            System.out.println("[Phase 5] "+label+" pack="+Iris.getCurrentPackName()+" pipelines="+IrisViewPipelines.size()
                +" views="+PortalViewRenderer.getViewCountThisFrame()+" targets="+PortalViewRenderer.getRenderTargetCount()
                +" fps="+mc.getFps()+" restoredFrames="+frames+" averagePortalMs="+(frames==0?0:nanos/1e6/frames));
        });
    }

    static void reload(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            try { Iris.reload(); }
            catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            check(IrisViewPipelines.size()==0,"Camera pipelines survived shader reload");
        });
        context.waitTicks(40);
        SodiumMulticameraTests.image(context,"phase5_after_shader_reload",false);
        active(context,"after-reload");
    }

    static void stress(ClientGameTestContext context,TestSingleplayerContext world) {
        // The cross-dimension round trip is included in this real-time bounded window.
        long start=System.nanoTime(),until=start+java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
        crossDimension(context,world);
        int crossings=2,samples=0,maxTargets=0,maxWrappers=0,initialWrappers=-1;
        long nextCross=System.nanoTime(),nextSample=System.nanoTime();
        while(System.nanoTime()<until) {
            double t=(System.nanoTime()-start)/1e9;
            context.getInput().lookAt((float)(180+8*Math.sin(t)),(float)(3*Math.cos(t)));
            if(System.nanoTime()>=nextCross) {
                world.getServer().runCommand("tp @a -1.5 101 5.5 180 0");
                world.getConnection().waitForClientboundPackets();context.waitTicks(5);
                context.getInput().holdKey(options -> options.keyUp);
                try {world.getServer().waitFor(s -> s.getPlayerList().getPlayers().getFirst().getX()>200,300);}
                finally {context.getInput().releaseKey(options -> options.keyUp);}
                context.waitFor(mc -> mc.player.getX()>200,200);
                crossings++;
                world.getServer().runCommand("tp @a 0.5 101 5.5 180 0");
                world.getConnection().waitForClientboundPackets();context.waitTicks(10);
                nextCross=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(18);
            }
            if(System.nanoTime()>=nextSample) {
                active(context,"stress-"+samples);
                SodiumMulticameraTests.image(context,"phase5_stress_"+samples++,true);
                int wrappers=context.computeOnClient(mc -> qouteall.imm_ptl.core.compat.iris_portal_view.mixin.IrisRenderSystemAccessor.ip_overrides()
                    .values().stream().mapToInt(map -> map.values().stream().mapToInt(java.util.Map::size).sum()).sum());
                maxWrappers=Math.max(maxWrappers,wrappers);
                if(initialWrappers<0)initialWrappers=wrappers;
                check(wrappers<=initialWrappers+16,"Shader wrapper cache accumulated: "+initialWrappers+" -> "+wrappers);
                maxTargets=Math.max(maxTargets,context.computeOnClient(mc -> (int)PortalViewRenderer.getRenderTargetCount()));
                check(maxTargets<=4,"Portal targets accumulated");
                check(context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount()<1500),"Chunks accumulated");
                System.out.println("[Phase 5 stress sample] wrappers="+wrappers+" targets="+maxTargets);
                nextSample=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            }
            context.waitTicks(10);
        }
        world.getServer().runCommand("tp @a 0.5 101 5.5 180 0");
        world.getConnection().waitForClientboundPackets();context.waitTicks(10);
        System.out.println("[Phase 5 stress] PASS seconds="+(System.nanoTime()-start)/1e9+" crossings="+crossings
            +" samples="+samples+" maxTargets="+maxTargets+" maxWrappers="+maxWrappers);
    }

    static void crossDimension(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();var connection=world.getConnection();
        server.runCommand("execute in minecraft:the_nether run forceload add -16 -16 16 16");
        server.runCommand("execute in minecraft:the_nether run fill -4 100 -7 4 107 4 minecraft:obsidian");
        server.runCommand("execute in minecraft:the_nether run fill -3 101 -6 3 106 3 minecraft:air");
        server.runCommand("execute in minecraft:the_nether run fill -3 100 -6 3 100 3 minecraft:glowstone");
        server.runCommand("execute in minecraft:the_nether run fill -4 100 -7 4 107 -7 minecraft:gold_block");
        UUID outward=server.computeOnServer(s -> TestUtil.spawnPortal(s.overworld(),new Vec3(.5,102.5,-2.5),Level.NETHER,new Vec3(.5,102.5,.5),3,4).getUUID());
        server.runCommand("tp @a 0.5 101 0.5 180 0");
        connection.waitForClientboundPackets();
        context.waitFor(mc -> PortalViewRenderer.isViewDrawnThisFrame(outward),1200);
        context.waitTicks(40);
        try(var in=Files.newInputStream(context.takeScreenshot("phase5_nether"));var image=NativeImage.read(in)) {
            int[] p=context.computeOnClient(mc -> SodiumMulticameraTests.project(mc,new Vec3(1.5,103.5,-9),image.getWidth(),image.getHeight()));
            int c=image.getPixel(p[0],p[1]);
            check(ARGB.red(c)>120&&ARGB.green(c)>90&&ARGB.blue(c)<ARGB.red(c)*.65,"Nether gold missing: "+Integer.toHexString(c));
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        active(context,"cross-dimension");
        walk(context,world,Level.NETHER);
        UUID back=server.computeOnServer(s -> TestUtil.spawnPortal(s.getLevel(Level.NETHER),new Vec3(.5,102.5,-4.5),Level.OVERWORLD,new Vec3(.5,102.5,6.5),3,4).getUUID());
        connection.waitForClientboundPackets();context.waitTicks(20);
        walk(context,world,Level.OVERWORLD);
        server.runOnServer(s -> {s.overworld().getEntity(outward).discard();s.getLevel(Level.NETHER).getEntity(back).discard();});
        server.runCommand("tp @a 0.5 101 5.5 180 0");
        connection.waitForClientboundPackets();context.waitTicks(20);
        active(context,"cross-dimension-return");
    }

    private static void walk(ClientGameTestContext context,TestSingleplayerContext world,net.minecraft.resources.ResourceKey<Level> dimension) {
        context.getInput().holdKey(options -> options.keyUp);
        try {world.getServer().waitFor(s -> s.getPlayerList().getPlayers().getFirst().level().dimension()==dimension,300);}
        finally {context.getInput().releaseKey(options -> options.keyUp);}
        context.waitFor(mc -> mc.level.dimension()==dimension&&mc.player.level()==mc.level,200);
        context.waitTicks(10);
        check(context.computeOnClient(mc -> Math.abs(Math.IEEEremainder(mc.player.getYRot()-180,360))<2),"Cross-world orientation changed");
    }
}
