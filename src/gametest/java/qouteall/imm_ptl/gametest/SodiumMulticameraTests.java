package qouteall.imm_ptl.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Focused two-view pixel, camera restoration, traversal and resource regression. */
public class SodiumMulticameraTests implements FabricClientGameTest {
    private static final Vec3 A = new Vec3(-1.5, 102.5, 0.5), B = new Vec3(2.5, 102.5, 0.5);

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        boolean phase4 = System.getProperty("imm_ptl.test.selection", "").startsWith("phase4");
        boolean phase5 = System.getProperty("imm_ptl.test.selection", "").startsWith("phase5");
        check(phase4 || !FabricLoader.getInstance().isModLoaded("nvidium"), "Nvidium contaminates Phase 3 baseline");
        if (phase4) NvidiumTestSupport.manifest(context);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            var server = world.getServer();
            var connection = world.getConnection();
            connection.waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("forceload add -16 -16 16 16");
            server.runCommand("forceload add 240 -16 272 16");
            server.runCommand("forceload add 496 -16 528 16");
            server.runCommand("fill -8 100 -8 8 100 8 minecraft:glowstone");
            server.runCommand("fill -8 101 -8 8 108 8 minecraft:air");
            server.runCommand("fill -8 100 -6 8 108 -6 minecraft:purple_concrete");
            server.runCommand("fill 248 96 -6 255 112 -6 minecraft:emerald_block");
            server.runCommand("fill 256 96 -6 264 112 -6 minecraft:gold_block");
            server.runCommand("fill 504 96 -6 520 112 -6 minecraft:lapis_block");
            server.runCommand("fill 248 100 -5 264 100 8 minecraft:glowstone");
            server.runCommand("fill 504 100 -5 520 100 8 minecraft:glowstone");
            // Deliberately between the virtual camera and destination plane: must be clipped.
            if (phase5) server.runCommand("fill 248 96 2 264 112 2 minecraft:red_concrete");
            server.runCommand("tp @a 0.5 101 5.5 180 0");
            UUID a = server.computeOnServer(s -> TestUtil.spawnPortal(s.overworld(), A, Level.OVERWORLD,
                new Vec3(256.5,102.5,0.5),3,4).getUUID());
            if (phase4 || phase5) {
                connection.waitForClientboundPackets();
                context.waitFor(mc -> PortalViewRenderer.isViewDrawnThisFrame(a)
                    && mc.level.getChunkSource().getChunk(16,0,false)!=null,1200);
                context.waitTicks(40);
                if (phase4) NvidiumTestSupport.singleView(context,a,A);
                if (phase5) IrisTestSupport.single(context,a,A);
                restored(context);
            }
            UUID b = server.computeOnServer(s -> TestUtil.spawnPortal(s.overworld(), B, Level.OVERWORLD,
                new Vec3(512.5,102.5,0.5),3,4).getUUID());
            connection.waitForClientboundPackets();
            context.waitFor(mc -> PortalViewRenderer.isViewDrawnThisFrame(a)
                && PortalViewRenderer.isViewDrawnThisFrame(b)
                && mc.level.getChunkSource().getChunk(16,0,false)!=null
                && mc.level.getChunkSource().getChunk(32,0,false)!=null,1200);
            context.waitTicks(40);
            int chunks = context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
            check(chunks < 1500, "Unexpectedly unbounded destination loading: " + chunks);
            pair(context,"phase3_two_destinations");
            restored(context);
            if (phase5 && !System.getProperty("imm_ptl.test.selection").equals("phase5_secondary")) IrisTestSupport.reload(context);
            System.setProperty("imm_ptl.test.reverseViews","true");
            context.waitTicks(10);
            pair(context,"phase3_two_destinations_reversed");
            restored(context);
            System.clearProperty("imm_ptl.test.reverseViews");

            // Same destination, distinct transformed camera poses (B sees the gold half).
            server.runOnServer(s -> {
                Portal p=(Portal)s.overworld().getEntity(b);
                p.setDestination(new Vec3(262.5,102.5,0.5)); p.reloadAndSyncToClient();
            });
            connection.waitForClientboundPackets();context.waitTicks(20);
            image(context,"phase3_shared_destination",true);
            restored(context);
            if (System.getProperty("imm_ptl.test.selection").equals("phase5_state_probe")) {
                IrisTestSupport.active(context,"extraction-canary-preserved");
                return;
            }
            if (System.getProperty("imm_ptl.test.selection").equals("phase4_sanity")) {
                NvidiumTestSupport.active(context,"two-camera-sanity");
                return;
            }
            if (phase5) IrisTestSupport.crossDimension(context,world);
            if (phase5 && System.getProperty("imm_ptl.test.selection").equals("phase5_stress")) IrisTestSupport.stress(context,world);
            if (phase5 && System.getProperty("imm_ptl.test.selection").equals("phase5_secondary")) {
                server.runOnServer(s -> {s.overworld().getEntity(a).discard();s.overworld().getEntity(b).discard();});
                context.waitFor(mc -> PortalViewRenderer.getRetainedViewCount()==0,1200);
                IrisTestSupport.active(context,"secondary-cleanup");
                return;
            }
            if (phase4) {
                NvidiumTestSupport.crossDimension(context,world);
                restored(context);
                if (System.getProperty("imm_ptl.test.selection").equals("phase4_stress")) {
                    NvidiumTestSupport.stress(context,world);
                    image(context,"phase4_after_stress",true);
                    restored(context);
                }
            }

            // Isolate A, center the source camera and verify a 90-degree roll with asymmetric markers.
            server.runOnServer(s -> s.overworld().getEntity(b).discard());
            server.runCommand("tp @a -1.5 101 5.5 180 0");
            connection.waitForClientboundPackets();context.waitTicks(15);
            Path straight = dump(context,"phase3_unrotated_target");
            targetColors(straight,false);
            server.runOnServer(s -> {
                Portal p=(Portal)s.overworld().getEntity(a);
                p.setRotationTransformation(DQuaternion.rotationByDegrees(new Vec3(0,0,1),90));
                p.reloadAndSyncToClient();
            });
            connection.waitForClientboundPackets();context.waitTicks(15);
            targetColors(dump(context,"phase3_rolled_target"),true);
            restored(context);
            if (phase4 || phase5) {
                server.runCommand("tp @a -1.5 101 0.53 180 0");
                connection.waitForClientboundPackets();context.waitTicks(8);
                targetColors(dump(context,"phase4_rolled_near_plane"),true);
                if (phase5) targetColors(context.takeScreenshot("phase5_near_composite"),true);
                if (phase4) NvidiumTestSupport.active(context,"rolled-near-plane");
                server.runCommand("tp @a -1.5 101 5.5 180 0");
                connection.waitForClientboundPackets();context.waitTicks(10);
            }
            System.out.println("[Phase 3 metrics] views="+context.computeOnClient(mc -> PortalViewRenderer.getViewCountThisFrame())
                +" targets="+context.computeOnClient(mc -> PortalViewRenderer.getRenderTargetCount())
                +" chunksWithTwoDestinations="+chunks);

            // Same-dimension traversal must transform the real player's location and roll-derived orientation.
            context.getInput().holdKey(options -> options.keyUp);
            try {server.waitFor(s -> s.getPlayerList().getPlayers().getFirst().getX()>200,300);}
            finally {context.getInput().releaseKey(options -> options.keyUp);}
            context.waitFor(mc -> mc.player.getX()>200,200);
            // Server relocation precedes the client's next authoritative movement update.
            context.waitTicks(10);
            Vec3 position=server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
            check(position.distanceTo(new Vec3(256.5,101,0.5))<8,"Bad same-world traversal "+position);
            float yaw=server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getYRot());
            System.out.println("[Phase 3 traversal] serverYaw="+yaw+" clientYaw="
                +context.computeOnClient(mc -> mc.player.getYRot()));
            check(Math.abs(Math.IEEEremainder(yaw-180,360))<2,"Roll corrupted traversal yaw "+yaw);
            server.runOnServer(s -> s.overworld().getEntity(a).discard());
            server.runCommand("tp @a 0.5 101 5.5 180 0");
            connection.waitForClientboundPackets();
            context.waitFor(mc -> PortalViewRenderer.getRetainedViewCount()==0,1200);
            check(context.computeOnClient(mc -> PortalViewRenderer.getRenderTargetCount()==0),"View targets survived expiry");
            context.waitFor(mc -> mc.level.getChunkSource().getChunk(16,0,false)==null
                && mc.level.getChunkSource().getChunk(32,0,false)==null,1200);
            restored(context);
            System.out.println("[Phase 3 cleanup] targets=0 retainedViews=0 remoteChunksReleased=true chunks="
                +context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount()));
            if (phase4) NvidiumTestSupport.active(context,"after-cleanup");
            if (phase5) {
                IrisTestSupport.active(context,"after-cleanup");
                check(context.computeOnClient(mc -> qouteall.imm_ptl.core.compat.iris_portal_view.IrisViewPipelines.size()==0),"Iris portal pipelines survived expiry");
            }
        }
        finally {System.clearProperty("imm_ptl.test.reverseViews");}
        check(context.computeOnClient(mc -> !ClientWorldLoader.getIsInitialized()),"Client worlds survived disconnect");
        if (phase4) NvidiumTestSupport.disconnected(context);
        if (phase5) check(context.computeOnClient(mc -> qouteall.imm_ptl.core.compat.iris_portal_view.IrisViewPipelines.size()==0),"Iris portal pipelines survived disconnect");
    }

    private static void pair(ClientGameTestContext context,String name) {image(context,name,false);}

    static void image(ClientGameTestContext context,String name,boolean shared) {
        Path path=context.takeScreenshot(name);
        try(var in=Files.newInputStream(path);NativeImage image=NativeImage.read(in)) {
            int[] points=context.computeOnClient(mc -> {
                int[] aa=project(mc,A,image.getWidth(),image.getHeight()),bb=project(mc,B,image.getWidth(),image.getHeight());
                return new int[]{aa[0],aa[1],bb[0],bb[1]};
            });
            int left=image.getPixel(points[0],points[1]),right=image.getPixel(points[2],points[3]);
            check(green(left),name+" A camera wrong: "+rgb(left));
            check(shared?gold(right):blue(right),name+" B camera wrong: "+rgb(right));
            int[] main=context.computeOnClient(mc -> project(mc,new Vec3(0.3,103,-6),image.getWidth(),image.getHeight()));
            int marker=image.getPixel(main[0],main[1]);
            check(ARGB.red(marker)>ARGB.green(marker)*1.5 && ARGB.blue(marker)>ARGB.green(marker)*1.5,
                name+" main-world purple marker/camera corrupted: "+rgb(marker));
            System.out.println("[Phase 3 pixels] "+name+" A="+rgb(left)+" B="+rgb(right));
            System.out.println("[Phase 3 two-view metrics] views="+context.computeOnClient(mc -> PortalViewRenderer.getViewCountThisFrame())
                +" targets="+context.computeOnClient(mc -> PortalViewRenderer.getRenderTargetCount()));
            if (System.getProperty("imm_ptl.test.selection", "").startsWith("phase4")) NvidiumTestSupport.active(context,name);
        } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
    }

    static int[] project(Minecraft mc,Vec3 point,int width,int height) {
        var camera=mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Vec3 p=point.subtract(camera.pos);
        Vector4f clip=new Vector4f((float)p.x,(float)p.y,(float)p.z,1);
        camera.viewRotationMatrix.transform(clip);camera.projectionMatrix.transform(clip);
        return new int[]{(int)((clip.x/clip.w*0.5+0.5)*width),(int)((0.5-clip.y/clip.w*0.5)*height)};
    }

    private static Path dump(ClientGameTestContext context,String name) {
        Path target=context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots/"+name+".png"));
        try {Files.deleteIfExists(target);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        context.runOnClient(mc -> PortalViewRenderer.debugDumpNextView(target));
        context.waitFor(mc -> Files.exists(target),200);
        return target;
    }

    private static void targetColors(Path path,boolean rolled) {
        try(var in=Files.newInputStream(path);NativeImage image=NativeImage.read(in)) {
            int x=image.getWidth()/2,y=image.getHeight()/2;
            int first=image.getPixel(rolled?x:x-image.getWidth()/8,rolled?y-image.getHeight()/8:y);
            int second=image.getPixel(rolled?x:x+image.getWidth()/8,rolled?y+image.getHeight()/8:y);
            check(green(first)&&gold(second),"Incorrect "+(rolled?"rolled":"straight")+" image orientation: "+rgb(first)+" / "+rgb(second));
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }

    private static void restored(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            check(mc.level==mc.player.level(),"Main level not restored");
            check(mc.levelRenderer==ClientWorldLoader.getWorldRenderer(mc.level.dimension()),"Main renderer not restored");
            check(((qouteall.imm_ptl.core.mixin.client.portal_view.LevelRendererAccessor)mc.levelRenderer).ip_getLevelRenderState()
                ==mc.gameRenderer.gameRenderState().levelRenderState,"Main render state not restored");
            if(!SodiumInterface.invoker.isSodiumPresent())return;
            try {
                Class<?> ext=Class.forName("net.caffeinemc.mods.sodium.client.world.LevelRendererExtension");
                Object renderer=ext.getMethod("sodium$getWorldRenderer").invoke(mc.levelRenderer);
                var field=renderer.getClass().getDeclaredField("lastCameraPos");field.setAccessible(true);
                org.joml.Vector3d position=(org.joml.Vector3d)field.get(renderer);
                Vec3 expected=mc.gameRenderer.mainCamera().position();
                check(position.distance(expected.x,expected.y,expected.z)<1e-5,"Sodium terrain camera leaked: "+position+" expected "+expected);
                var cull=renderer.getClass().getDeclaredField("cullMatrix");cull.setAccessible(true);
                Object frustum=mc.gameRenderer.mainCamera().getCullFrustum();
                org.joml.Matrix4fc wantedMatrix=(org.joml.Matrix4fc)Class.forName("net.caffeinemc.mods.sodium.mixin.core.render.world.FrustumAccessor")
                    .getMethod("sodium$getMatrix").invoke(frustum);
                check(((org.joml.Matrix4f)cull.get(renderer)).equals(wantedMatrix,1e-6f),"Main Sodium culling matrix leaked");
                for(String axis:new String[]{"lastCameraPitch","lastCameraYaw"}) {
                    var angle=renderer.getClass().getDeclaredField(axis);angle.setAccessible(true);
                    double wanted=axis.endsWith("Pitch")?mc.gameRenderer.mainCamera().xRot():mc.gameRenderer.mainCamera().yRot();
                    check(Math.abs(angle.getDouble(renderer)-wanted)<1e-5,"Sodium camera angle leaked");
                }
            }catch(ReflectiveOperationException e){throw new AssertionError("Cannot inspect Sodium camera",e);}
        });
    }
    private static boolean green(int c){return ARGB.green(c)>70&&ARGB.green(c)>ARGB.red(c)*1.3&&ARGB.green(c)>ARGB.blue(c)*1.1;}
    private static boolean blue(int c){return ARGB.blue(c)>50&&ARGB.blue(c)>ARGB.red(c)*1.3&&ARGB.blue(c)>ARGB.green(c)*1.2;}
    private static boolean gold(int c){return ARGB.red(c)>120&&ARGB.green(c)>90&&ARGB.blue(c)<ARGB.red(c)*0.65;}
    private static String rgb(int c){return "("+ARGB.red(c)+","+ARGB.green(c)+","+ARGB.blue(c)+")";}
}
