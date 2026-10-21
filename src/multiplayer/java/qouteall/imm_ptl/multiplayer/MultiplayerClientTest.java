package qouteall.imm_ptl.multiplayer;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;

import java.util.*;

/** One instance per independent real Minecraft client JVM. Never creates an integrated server. */
public class MultiplayerClientTest implements FabricClientGameTest {
    private static volatile long messages;
    @Override
    public void runTest(ClientGameTestContext context) {
        Protocol.validateProfile();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> messages++);
        String role = Protocol.ROLE;
        Protocol.check(role.equals("A") || role.equals("B"),"Wrong client role");
        context.runOnClient(mc -> mc.options.renderDistance().set(4));
        Protocol.write("ready-client"+role+".json",Map.of("pid",ProcessHandle.current().pid(),"ready",true));
        long sequence = 0;
        long deadline = System.nanoTime()+java.time.Duration.ofMinutes(12).toNanos();
        while(System.nanoTime()<deadline) {
            JsonObject command = Protocol.read("client"+role+"-command.json");
            if(command!=null && command.get("seq").getAsLong()>sequence) {
                sequence=command.get("seq").getAsLong();
                String action=command.get("action").getAsString();
                try {
                    if(action.equals("exit")) {
                        disconnect(context);
                        Protocol.write("client"+role+"-response.json",Map.of("seq",sequence,"status","PASS"));
                        return;
                    }
                    execute(context,command);
                    snapshot(context);
                    Protocol.write("client"+role+"-response.json",Map.of("seq",sequence,"status","PASS"));
                }
                catch(Throwable failure) {
                    Protocol.write("client"+role+"-response.json",Map.of("seq",sequence,"status","FAIL","error",failure.toString()));
                    try { context.takeScreenshot("phase2-failure-"+role); } catch(Throwable ignored) {}
                    throw failure;
                }
            }
            snapshot(context);
            context.waitTicks(2);
        }
        throw new AssertionError("Phase 2 client coordinator timed out");
    }

    private static void execute(ClientGameTestContext context, JsonObject command) {
        switch(command.get("action").getAsString()) {
            case "connect" -> {
                int port=Protocol.read("ready-server.json").get("port").getAsInt();
                context.runOnClient(mc -> {
                    Protocol.check(mc.getSingleplayerServer()==null,"Integrated server forbidden");
                    String address="127.0.0.1:"+port;
                    ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),
                        new ServerData("Isolated Phase 2",address,ServerData.Type.OTHER),false,null);
                });
                context.waitFor(mc -> mc.level!=null && mc.player!=null && mc.gui.screen()==null,1200);
            }
            case "disconnect" -> disconnect(context);
            case "walk" -> {
                String destination=command.get("dimension").getAsString();
                context.getInput().holdKey(options -> options.keyUp);
                try { context.waitFor(mc -> mc.level!=null && mc.level.dimension().identifier().toString().equals(destination)
                    && mc.player.level()==mc.level,500); }
                finally {context.getInput().releaseKey(options -> options.keyUp);}
                context.waitTicks(3);
            }
            case "view" -> {
                UUID id=UUID.fromString(Protocol.read("fixture.json").get("outward").getAsString());
                context.waitFor(mc -> {
                    if(mc.level==null) return false;
                    for(Entity entity:mc.level.entitiesForRendering()) if(entity instanceof Portal p && p.getUUID().equals(id))
                        return PortalViewRenderer.isViewDrawnThisFrame(id);
                    return false;
                },1200);
            }
            case "ping" -> {
                long before = messages;
                context.runOnClient(mc -> {
                    Protocol.check(mc.getConnection()!=null && mc.level!=null,"Client disconnected");
                    mc.getConnection().sendCommand("help");
                });
                context.waitFor(mc -> messages > before, 200);
            }
            default -> throw new AssertionError("Unknown client action "+command);
        }
    }

    private static void disconnect(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if(mc.level!=null) {
                mc.level.disconnect(Component.literal("Phase 2 normal disconnect"));
                mc.disconnectWithSavingScreen();
            }
            mc.gui.setScreen(new TitleScreen());
        });
        context.waitFor(mc -> mc.level==null && mc.player==null,200);
        context.waitTicks(3);
        Protocol.check(context.computeOnClient(mc -> !ClientWorldLoader.getIsInitialized()
            && ClientWorldLoader.WORLD_RENDERER_MAP.isEmpty() && ClientWorldLoader.EXTRACTOR_MAP.isEmpty()),"Client resources survived disconnect");
    }

    private static void snapshot(ClientGameTestContext context) {
        Map<String,Object> state=context.computeOnClient(mc -> {
            Protocol.check(mc.getSingleplayerServer()==null,"Client accidentally created an integrated server");
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("pid",ProcessHandle.current().pid()); result.put("connected",mc.level!=null && mc.player!=null);
            result.put("initialized",ClientWorldLoader.getIsInitialized());
            if(mc.player!=null && mc.level!=null) {
                result.put("uuid",mc.player.getUUID().toString()); result.put("name",mc.player.getName().getString());
                result.put("dimension",mc.level.dimension().identifier().toString()); result.put("x",mc.player.getX());
                result.put("y",mc.player.getY()); result.put("z",mc.player.getZ()); result.put("yaw",mc.player.getYRot());
                result.put("pitch",mc.player.getXRot()); result.put("mounted",mc.player.getVehicle()!=null);
                List<Map<String,Object>> players=new ArrayList<>();
                List<Map<String,Object>> vehicles=new ArrayList<>();
                Map<String,Integer> chunks=new TreeMap<>();
                Collection<ClientLevel> levels=ClientWorldLoader.getIsInitialized()?ClientWorldLoader.getClientWorlds():List.of(mc.level);
                for(ClientLevel level:levels) {
                    chunks.put(level.dimension().identifier().toString(),level.getChunkSource().getLoadedChunksCount());
                    for(Entity entity:level.entitiesForRendering()) if(entity instanceof Player player) players.add(Map.of(
                        "uuid",player.getUUID().toString(),"dimension",level.dimension().identifier().toString(),"id",player.getId()));
                    for(Entity entity:level.entitiesForRendering()) if(entity instanceof net.minecraft.world.entity.vehicle.boat.ChestBoat) vehicles.add(Map.of(
                        "uuid",entity.getUUID().toString(),"dimension",level.dimension().identifier().toString(),"id",entity.getId()));
                }
                result.put("players",players); result.put("chunks",chunks);
                result.put("vehicles",vehicles);
            }
            return result;
        });
        Protocol.write("client"+Protocol.ROLE+"-state.json",state);
    }
}
