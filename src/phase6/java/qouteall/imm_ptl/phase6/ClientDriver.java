package qouteall.imm_ptl.phase6;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;
import java.nio.file.*;
import java.util.*;

public class ClientDriver implements ClientModInitializer {
    private long sequence;
    private int ticks;
    private String role;
    private final List<String> messages=new ArrayList<>();
    public void onInitializeClient(){
        Control.check(Files.exists(Path.of(".phase6-client")),"Phase 6 requires disposable client marker");
        role=System.getProperty("imm_ptl.phase6.role","A");Control.check(role.equals("A")||role.equals("B"),"Bad role");
        var old=Control.read("client"+role+"-command.json");sequence=old==null?0:old.get("seq").getAsLong();
        Map<String,String> mods=new TreeMap<>();FabricLoader.getInstance().getAllMods().forEach(m->mods.put(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString()));
        Control.write("client"+role+"-mods.json",mods);
        ClientReceiveMessageEvents.GAME.register((text,overlay)->{messages.add(text.getString());while(messages.size()>30)messages.removeFirst();});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(++ticks%4!=0)return;
            var c=Control.read("client"+role+"-command.json");
            if(c!=null&&c.get("seq").getAsLong()>sequence){
                sequence=c.get("seq").getAsLong();
                try{action(mc,c);Control.write("client"+role+"-response.json",Map.of("seq",sequence,"status","PASS"));}
                catch(Throwable e){Control.write("client"+role+"-response.json",Map.of("seq",sequence,"status","FAIL","error",e.toString()));e.printStackTrace();}
            }
            state(mc);
        });
    }
    private void action(Minecraft mc,com.google.gson.JsonObject c){
        switch(c.get("action").getAsString()){
            case "phase81-start" -> Phase81Probe.start(c.get("name").getAsString(),c.has("audio")&&c.get("audio").getAsBoolean());
            case "phase81-stop" -> Phase81Probe.finish(Control.ROOT.resolve(c.get("name").getAsString()+".json"));
            case "phase81-options" -> {mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);mc.options.framerateLimit().set(c.get("fps").getAsInt());mc.options.enableVsync().set(c.get("vsync").getAsBoolean());if(c.has("thirdPerson"))mc.options.setCameraType(c.get("thirdPerson").getAsBoolean()?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);}
            case "connect" -> ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25681"),new ServerData("Disposable Phase 6","127.0.0.1:25681",ServerData.Type.OTHER),false,null);
            case "command" -> {Control.check(mc.getConnection()!=null,"No connection");mc.getConnection().sendCommand(c.get("command").getAsString());}
            case "walk" -> mc.options.keyUp.setDown(c.get("enabled").getAsBoolean());
            case "strafe" -> mc.options.keyLeft.setDown(c.get("enabled").getAsBoolean());
            case "look" -> {mc.player.setYRot(c.get("yaw").getAsFloat());mc.player.setXRot(c.get("pitch").getAsFloat());}
            case "attack-key" -> {mc.options.keyAttack.setDown(c.get("enabled").getAsBoolean());if(c.get("enabled").getAsBoolean())KeyMapping.click(mc.options.keyAttack.getDefaultKey());}
            case "use-key" -> {mc.options.keyUse.setDown(c.get("enabled").getAsBoolean());if(c.get("enabled").getAsBoolean())KeyMapping.click(mc.options.keyUse.getDefaultKey());}
            case "shift" -> mc.options.keyShift.setDown(c.get("enabled").getAsBoolean());
            case "use-item" -> mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND);
            case "block" -> {
                var pos=new net.minecraft.core.BlockPos(c.get("x").getAsInt(),c.get("y").getAsInt(),c.get("z").getAsInt());
                if(c.get("operation").getAsString().equals("break"))mc.gameMode.startDestroyBlock(pos,net.minecraft.core.Direction.UP);
                else mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));
            }
            case "respawn" -> mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            case "press" -> {Control.check(mc.gui.screen()!=null,"No screen");Control.check(press(mc.gui.screen().children(),c.get("label").getAsString()),"Button not found");}
            case "screenshot" -> {
                String name=c.get("name").getAsString();Control.check(name.matches("[A-Za-z0-9_-]+"),"Bad screenshot name");
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{try(image){image.writeToFile(Control.ROOT.resolve(name+"-"+role+".png"));}catch(Exception e){throw new RuntimeException(e);}});
            }
            case "close-screen" -> mc.gui.setScreen(null);
            case "sync-pack" -> {try{BootstrapDriver.sync(role);}catch(Exception e){throw new RuntimeException(e);}}
            case "exit" -> mc.stop();
            default -> throw new IllegalArgumentException("Unknown client action");
        }
    }
    private boolean press(List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children,String label){
        for(var child:children){
            if(child instanceof net.minecraft.client.gui.components.Button b && b.active && b.getMessage().getString().contains(label)){
                b.onPress(new net.minecraft.client.input.InputWithModifiers(){public int input(){return 0;}public int modifiers(){return 0;}});return true;
            }
            if(child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container && press(container.children(),label))return true;
        }return false;
    }
    private void state(Minecraft mc){
        Map<String,Object> s=new LinkedHashMap<>();s.put("pid",ProcessHandle.current().pid());s.put("screen",mc.gui.screen()==null?"none":mc.gui.screen().getClass().getName());
        s.put("connected",mc.level!=null&&mc.player!=null);s.put("fps",mc.getFps());s.put("messages",List.copyOf(messages));
        s.put("views",PortalViewRenderer.getViewCountThisFrame());s.put("targets",PortalViewRenderer.getRenderTargetCount());
        if(mc.gui.screen()!=null){List<String> labels=new ArrayList<>();labels(mc.gui.screen().children(),labels);s.put("buttons",labels);}
        s.put("remotePointedDimension",String.valueOf(qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remotePointedDim));
        s.put("remoteHit",String.valueOf(qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remoteHitResult));
        if(mc.player!=null&&mc.level!=null){
            s.put("dimension",mc.level.dimension().identifier().toString());s.put("x",mc.player.getX());s.put("y",mc.player.getY());s.put("z",mc.player.getZ());s.put("yaw",mc.player.getYRot());
            Map<String,Integer> chunks=new TreeMap<>();List<Object> players=new ArrayList<>();
            for(var world:ClientWorldLoader.getClientWorlds()){
                chunks.put(world.dimension().identifier().toString(),world.getChunkSource().getLoadedChunksCount());
                for(var e:world.entitiesForRendering())if(e instanceof net.minecraft.world.entity.player.Player)players.add(Map.of("uuid",e.getUUID().toString(),"dimension",world.dimension().identifier().toString()));
            }s.put("chunks",chunks);s.put("players",players);
        }
        Control.write("client"+role+"-state.json",s);
    }
    private void labels(List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children,List<String> labels){
        for(var child:children){if(child instanceof net.minecraft.client.gui.components.AbstractWidget w)labels.add(w.getMessage().getString()+" [active="+w.active+"]");if(child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)labels(container.children(),labels);}
    }
}
