package qouteall.imm_ptl.phase6;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal_view.PortalViewCamera;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, opt-in test diagnostics. Never packaged in the IP runtime jar. */
public final class Phase81Probe {
    public static boolean enabled, audio;
    private static String label;
    private static long cameraUpdates, cameraJumps, draws, staleDraws, frame, orderFrame=-1;
    private static int order;
    private static final List<Object> rows=new ArrayList<>(), samples=new ArrayList<>(), listeners=new ArrayList<>();
    private static final Map<Object,Long> updated=new IdentityHashMap<>();
    private static final Map<Object,Long> renderedFrames=new IdentityHashMap<>();
    private static long minecraftFrame;
    private static final List<String> errors=new ArrayList<>();
    private static final UUID SPEAKER=UUID.fromString("f4c1febb-3196-4853-b2db-860707fa8100");
    private static RemotePlayer speaker;
    private static Object channel, packet;
    private static Method write;
    private static ExecutorService worker;
    private static final ThreadLocal<Map<String,Object>> AUDIO_CALL=new ThreadLocal<>();
    private static class ReachedSpeaker extends RuntimeException { ReachedSpeaker(){super(null,null,false,false);} }

    public static void start(String name,boolean sound) {
        if(!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Bad diagnostic label");
        stopAudio();label=name;audio=sound;cameraUpdates=cameraJumps=draws=staleDraws=0;
        rows.clear();samples.clear();listeners.clear();updated.clear();renderedFrames.clear();errors.clear();enabled=true;
        if(sound)try{
            Minecraft mc=Minecraft.getInstance();ClientLevel level=(ClientLevel)mc.player.level();
            speaker=new RemotePlayer(level,new GameProfile(SPEAKER,"VoiceProbe"));
            speaker.setId(-81001);speaker.setPos(mc.player.position().add(2,0,0));level.addEntity(speaker);
            Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Field f=unsafeType.getDeclaredField("theUnsafe");f.setAccessible(true);
            Object unsafe=f.get(null);Class<?> type=Class.forName("de.maxhenkel.voicechat.voice.client.AudioChannel");
            channel=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,type);
            for(String field:new String[]{"minecraft","uuid"}){Field member=type.getDeclaredField(field);member.setAccessible(true);member.set(channel,field.equals("minecraft")?mc:SPEAKER);}
            Class<?> packetType=Class.forName("de.maxhenkel.voicechat.voice.common.PlayerSoundPacket");
            packet=packetType.getConstructor(UUID.class,UUID.class,short[].class,boolean.class,float.class,String.class)
                .newInstance(SPEAKER,SPEAKER,new short[]{0,0},false,48F,null);
            write=type.getDeclaredMethod("writeToSpeaker",Class.forName("de.maxhenkel.voicechat.voice.common.SoundPacket"),short[].class);write.setAccessible(true);
            worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"phase81-voice-packet-probe");t.setDaemon(true);return t;});
        }catch(Exception e){errors.add(e.toString());audio=false;throw new RuntimeException(e);}
    }
    private static void stopAudio(){if(worker!=null){worker.shutdownNow();worker=null;}if(speaker!=null){speaker.discard();speaker=null;}channel=packet=null;}
    static Object field(Object value,String name)throws Exception {Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static Object staticField(String name)throws Exception {Field f=PortalViewRenderer.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    public static void nextFrame(){minecraftFrame++;}
    public static void rendered(Object view){if(!enabled)return;try{if(updated.size()>2048){enabled=false;error("Diagnostic target budget reached");return;}Object target=field(view,"target");updated.put(target,(Long)staticField("frameIndex"));renderedFrames.put(target,minecraftFrame);}catch(Exception e){error(e.toString());}}
    public static void drawn(Object view){if(!enabled)return;try{
        frame=(Long)staticField("frameIndex");if(orderFrame!=frame){orderFrame=frame;order=0;}
        Portal p=(Portal)field(view,"portal");Object target=field(view,"target");Long last=updated.get(target);
        draws++;if(last==null||last!=frame||!Objects.equals(renderedFrames.get(target),minecraftFrame))staleDraws++;
        if(rows.size()<4096){var state=(net.minecraft.client.renderer.state.level.LevelRenderState)field(view,"state");
            Map<String,Object> row=new LinkedHashMap<>();row.put("frame",frame);row.put("portal",p.getUUID().toString());row.put("sourceEntity",p.getId());
            row.put("type",p.getType().toString());row.put("origin",p.getOriginPos().toString());row.put("size",List.of(p.getWidth(),p.getHeight()));
            row.put("targetIdentity",System.identityHashCode(target));row.put("targetLastSubmittedFrame",last);row.put("camera",state.cameraRenderState.pos.toString());
            row.put("minecraftRenderFrame",minecraftFrame);row.put("targetLastMinecraftRenderFrame",renderedFrames.get(target));row.put("frameViewCount",PortalViewRenderer.getViewCountThisFrame());
            row.put("rotation",((PortalViewCamera)field(view,"camera")).rotation().toString());row.put("projectionHash",state.cameraRenderState.projectionMatrix.hashCode());
            row.put("viewHash",state.cameraRenderState.viewRotationMatrix.hashCode());row.put("order",order++);rows.add(row);
        }
    }catch(Exception e){error(e.toString());}}
    public static void cameraUpdated(Object tracker){if(!enabled)return;try{
        Object state=tracker.getClass().getMethod("getState").invoke(tracker);Vec3 pos=(Vec3)state.getClass().getMethod("position").invoke(state);
        var camera=Minecraft.getInstance().gameRenderer.mainCamera();if(!camera.isInitialized())return;
        cameraUpdates++;if(camera instanceof PortalViewCamera||pos.distanceToSqr(camera.position())>1.0e-8)cameraJumps++;
        if(listeners.size()<256)listeners.add(Map.of("position",pos.toString(),"expectedMainCamera",camera.position().toString(),"playerEye",Minecraft.getInstance().player.getEyePosition().toString(),"cameraClass",camera.getClass().getSimpleName(),"world",Minecraft.getInstance().player.level().dimension().identifier().toString(),"nanoTime",System.nanoTime()));
    }catch(Exception e){error(e.toString());}}
    public static void lookup(ClientLevel level,Player result){
        Map<String,Object> row=AUDIO_CALL.get();if(row==null)return;
        row.put("lookupWorld",level.dimension().identifier().toString());row.put("speakerFound",result!=null);
        if(result!=null){row.put("speakerPosition",result.getEyePosition().toString());throw new ReachedSpeaker();}
    }
    public static void renderSample(){
        if(!enabled||!audio||samples.size()>=100)return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        boolean portal=mc.gameRenderer.mainCamera() instanceof PortalViewCamera;
        Map<String,Object> row=new LinkedHashMap<>();row.put("portalRender",portal);row.put("temporaryWorld",mc.level.dimension().identifier().toString());
        row.put("playerWorld",mc.player.level().dimension().identifier().toString());row.put("temporaryCamera",mc.gameRenderer.mainCamera().position().toString());
        try{
            samples.add(worker.submit(()->{
                AUDIO_CALL.set(row);
                try{
                    Object state=Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager").getMethod("getCameraState").invoke(null);
                    Vec3 listener=(Vec3)state.getClass().getMethod("position").invoke(state);row.put("listener",listener.toString());
                    if(speaker!=null){row.put("distance",listener.distanceTo(speaker.getEyePosition()));row.put("attenuation",Class.forName("de.maxhenkel.voicechat.voice.client.PositionalAudioUtils").getMethod("getDistanceVolume",float.class,Vec3.class).invoke(null,48F,speaker.getEyePosition()));}
                    write.invoke(channel,packet,new short[]{0,0});row.put("normalReturnBeforeSpeaker",true);
                }
                catch(InvocationTargetException e){if(!(e.getCause() instanceof ReachedSpeaker))row.put("error",e.getCause().toString());}
                catch(Exception e){row.put("error",e.toString());}finally{AUDIO_CALL.remove();}
                return row;
            }).get(2,TimeUnit.SECONDS));
        }catch(Exception e){error(e.toString());}
    }
    public static Map<String,Object> finish(Path path){
        enabled=false;Map<String,Object> result=new LinkedHashMap<>();result.put("label",label);result.put("cameraUpdates",cameraUpdates);result.put("portalCameraSamples",cameraJumps);
        result.put("draws",draws);result.put("staleDraws",staleDraws);result.put("viewRows",rows);result.put("audioSamples",samples);result.put("listenerSamples",listeners);result.put("errors",errors);
        Minecraft mc=Minecraft.getInstance();result.put("fps",mc.getFps());result.put("cameraType",mc.options.getCameraType().toString());
        result.put("fpsCap",mc.options.framerateLimit().get());result.put("vsync",mc.options.enableVsync().get());
        Map<String,String> mods=new TreeMap<>();net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().forEach(m->mods.put(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString()));result.put("mods",mods);
        try{Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new RuntimeException(e);}finally{stopAudio();}
        return result;
    }
    private static void error(String message){if(errors.size()<16)errors.add(message);}
}
