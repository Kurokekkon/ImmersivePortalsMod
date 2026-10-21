package qouteall.imm_ptl.gametest;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Test-only reflection keeps optional Nvidium classes out of the production dependency graph. */
final class NvidiumTestSupport {
    private static Path evidence;
    private static final Set<Object> managers=Collections.newSetFromMap(new IdentityHashMap<>());
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}

    static void manifest(ClientGameTestContext context) {
        managers.clear();
        Map<String,String> mods=new TreeMap<>();
        FabricLoader.getInstance().getAllMods().forEach(m -> mods.put(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString()));
        evidence=context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("phase4-evidence"));
        write("manifest",Map.of("mods",mods,"java",System.getProperty("java.version"),"pid",ProcessHandle.current().pid(),
            "gpu",context.computeOnClient(mc -> String.valueOf(GL11.glGetString(GL11.GL_RENDERER))),
            "driver",context.computeOnClient(mc -> String.valueOf(GL11.glGetString(GL11.GL_VERSION)))));
        check("26.3".equals(mods.get("minecraft"))&&"0.19.5".equals(mods.get("fabricloader")),"Wrong Minecraft/Loader");
        check("0.163.0+26.3".equals(mods.get("fabric-api")),"Wrong Fabric API");
        check("0.9.2+mc26.3".equals(mods.get("sodium")),"Wrong Sodium: "+mods.get("sodium"));
        check("0.4.4-beta7-26.3".equals(mods.get("nvidium")),"Wrong Nvidium: "+mods.get("nvidium"));
        check(!mods.containsKey("iris"),"Iris contaminates Phase 4");
    }

    static Map<String,Object> active(ClientGameTestContext context,String label) {
        Map<String,Object> state=context.computeOnClient(mc -> {
            try {
                Class<?> nvidium=Class.forName("me.cortex.nvidium.Nvidium");
                check(nvidium.getField("IS_ENABLED").getBoolean(null),"Nvidium renderer is disabled");
                Class<?> extension=Class.forName("net.caffeinemc.mods.sodium.client.world.LevelRendererExtension");
                Class<?> getter=Class.forName("me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererGetter");
                List<Map<String,Object>> worlds=new ArrayList<>();
                for(var entry:ClientWorldLoader.WORLD_RENDERER_MAP.entrySet()) {
                    Object sodium=extension.getMethod("sodium$getWorldRenderer").invoke(entry.getValue());
                    Object renderer=getter.getMethod("getRenderer").invoke(sodium);
                    managers.add(field(sodium,"renderSectionManager"));
                    check(renderer!=null,"Missing active Nvidium renderer for "+entry.getKey());
                    Object pipeline=field(renderer,"renderPipeline");
                    Object sections=renderer.getClass().getMethod("getSectionManager").invoke(renderer);
                    Object regions=sections.getClass().getMethod("getRegionManager").invoke(sections);
                    List<String> debug=new ArrayList<>();
                    renderer.getClass().getMethod("addDebugInfo",ArrayList.class).invoke(renderer,new ArrayList<String>() {
                        @Override public boolean add(String line){debug.add(line);return super.add(line);}
                    });
                    worlds.add(Map.of("dimension",entry.getKey().identifier().toString(),"renderer",System.identityHashCode(renderer),
                        "terrainFrames",((Number)field(pipeline,"frameId")).intValue(),
                        "regions",regions.getClass().getMethod("regionCount").invoke(regions),
                        "chunks",ClientWorldLoader.getWorld(entry.getKey()).getChunkSource().getLoadedChunksCount(),"debug",debug));
                }
                check(!worlds.isEmpty(),"No live world renderer");
                boolean drew=worlds.stream().anyMatch(w -> ((Number)w.get("terrainFrames")).intValue()>0&&((Number)w.get("regions")).intValue()>0);
                check(drew,"Nvidium enabled but no actual terrain frames/regions rendered");
                return Map.<String,Object>of("enabled",true,"worlds",worlds,"views",PortalViewRenderer.getViewCountThisFrame(),
                    "targets",PortalViewRenderer.getRenderTargetCount(),"retainedViews",PortalViewRenderer.getRetainedViewCount());
            }catch(ReflectiveOperationException e){throw new AssertionError("Cannot verify Nvidium runtime",e);}
        });
        write(label,state);
        System.out.println("[Phase 4 active] "+label+" "+new GsonBuilder().create().toJson(state));
        return state;
    }

    static void disconnected(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            try {
                Class<?> getter=Class.forName("me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererGetter");
                for(Object manager:managers) {
                    // Nvidium's destroy hook clears this only after renderer.delete() returns.
                    check(getter.getMethod("getRenderer").invoke(manager)==null,"Nvidium renderer survived world disposal");
                }
                check(ClientWorldLoader.WORLD_RENDERER_MAP.isEmpty(),"World renderer map survived disconnect");
                write("after-disconnect",Map.of("destroyedManagers",managers.size(),"worldRenderersEmpty",true));
                System.out.println("[Phase 4 cleanup] destroyedManagers="+managers.size()+" worldRenderersEmpty=true");
            }catch(ReflectiveOperationException e){throw new AssertionError("Cannot verify Nvidium destruction",e);}
            finally {managers.clear();}
        });
    }

    static void singleView(ClientGameTestContext context,UUID portal,Vec3 origin) {
        context.waitTicks(100);
        Map<String,Object> before=active(context,"single-before");
        context.waitTicks(10);
        Map<String,Object> after=active(context,"single-after");
        check(!before.get("worlds").equals(after.get("worlds")),"Nvidium terrain frame counter did not advance");
        Path imagePath=context.takeScreenshot("phase4_single_distant_view");
        try(var in=Files.newInputStream(imagePath);NativeImage image=NativeImage.read(in)) {
            int[] pixel=context.computeOnClient(mc -> SodiumMulticameraTests.project(mc,origin,image.getWidth(),image.getHeight()));
            int color=image.getPixel(pixel[0],pixel[1]);
            check(ARGB.green(color)>ARGB.red(color)*1.3&&ARGB.green(color)>ARGB.blue(color)*1.1,
                "Single distant portal does not show emerald: rgb="+ARGB.red(color)+","+ARGB.green(color)+","+ARGB.blue(color));
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        check(context.computeOnClient(mc -> PortalViewRenderer.isViewDrawnThisFrame(portal)),"Single portal not submitted");
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
        context.waitFor(mc -> PortalViewRenderer.isViewDrawnThisFrame(outward)
            && ClientWorldLoader.getWorld(Level.NETHER).getChunkSource().getChunk(0,0,false)!=null,1200);
        context.waitTicks(40);
        Path path=context.takeScreenshot("phase4_nether_destination");
        try(var in=Files.newInputStream(path);NativeImage image=NativeImage.read(in)) {
            // Project the center of a known gold block face, rather than an arbitrary
            // screen offset that can hit a dark texture grout line. Source equivalent
            // of Nether (1.5,103.5,-6), with the portal's -3 block Z translation.
            int[] point=context.computeOnClient(mc -> SodiumMulticameraTests.project(mc,new Vec3(1.5,103.5,-9),image.getWidth(),image.getHeight()));
            int color=image.getPixel(point[0],point[1]);
            check(ARGB.red(color)>150&&ARGB.green(color)>100&&ARGB.blue(color)<ARGB.red(color)*.6,
                "Nether gold wall missing: rgb="+ARGB.red(color)+","+ARGB.green(color)+","+ARGB.blue(color));
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        active(context,"cross-dimension-view");
        walk(context,world,Level.NETHER);
        UUID back=server.computeOnServer(s -> TestUtil.spawnPortal(s.getLevel(Level.NETHER),new Vec3(.5,102.5,-4.5),Level.OVERWORLD,new Vec3(.5,102.5,6.5),3,4).getUUID());
        connection.waitForClientboundPackets();context.waitTicks(10);
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

    static void stress(ClientGameTestContext context,TestSingleplayerContext world) {
        long start=System.nanoTime(),until=start+TimeUnit.MINUTES.toNanos(3),nextCross=start,nextSample=start;
        int samples=0,crossings=0,maxChunks=0;
        List<Map<String,Object>> observations=new ArrayList<>();
        Map<String,Object> initial=active(context,"stress-initial");
        Set<Object> initialRenderers=rendererIds(initial);
        int initialRegions=regionCount(initial),maxRegions=initialRegions;
        while(System.nanoTime()<until) {
            double seconds=(System.nanoTime()-start)/1e9;
            context.getInput().lookAt((float)(180+12*Math.sin(seconds)),(float)(4*Math.cos(seconds*.7)));
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
                nextCross=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
            }
            if(System.nanoTime()>=nextSample) {
                int chunks=context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
                maxChunks=Math.max(chunks,maxChunks);
                check(chunks<1500,"Runaway chunk retention during stress: "+chunks);
                check(context.computeOnClient(mc -> PortalViewRenderer.getRenderTargetCount()<=4),"Target accumulation during stress");
                Map<String,Object> sample=active(context,"stress-"+samples++);
                check(rendererIds(sample).equals(initialRenderers),"Nvidium renderer instances accumulated during stress");
                maxRegions=Math.max(maxRegions,regionCount(sample));
                check(maxRegions<=initialRegions+16,"Nvidium regions accumulated unexpectedly: "+maxRegions);
                observations.add(sample);
                SodiumMulticameraTests.image(context,"phase4_stress_pixels_"+samples,true);
                nextSample=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            }
            context.waitTicks(10);
        }
        world.getServer().runCommand("tp @a 0.5 101 5.5 180 0");
        world.getConnection().waitForClientboundPackets();context.waitTicks(10);
        double elapsed=(System.nanoTime()-start)/1e9;
        write("stress-result",Map.of("seconds",elapsed,"crossings",crossings,"samples",observations,"maxChunks",maxChunks,
            "initialRegions",initialRegions,"maxRegions",maxRegions));
        System.out.println("[Phase 4 stress] PASS seconds="+elapsed+" crossings="+crossings+" maxChunks="+maxChunks);
    }

    private static Set<Object> rendererIds(Map<String,Object> state) {
        Set<Object> ids=new HashSet<>();
        for(Object value:(List<?>)state.get("worlds")) ids.add(((Map<?,?>)value).get("renderer"));
        return ids;
    }
    private static int regionCount(Map<String,Object> state) {
        int regions=0;
        for(Object value:(List<?>)state.get("worlds")) regions+=((Number)((Map<?,?>)value).get("regions")).intValue();
        return regions;
    }

    private static Object field(Object object,String name)throws ReflectiveOperationException {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static void write(String name,Object data) {
        try {Files.createDirectories(evidence);Files.writeString(evidence.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
