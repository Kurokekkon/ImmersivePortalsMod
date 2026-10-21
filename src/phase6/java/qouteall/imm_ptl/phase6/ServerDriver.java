package qouteall.imm_ptl.phase6;

import com.google.gson.*;
import net.fabricmc.api.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;
import java.nio.file.*;
import java.util.*;

public class ServerDriver implements ModInitializer {
    private long sequence;
    private int ticks;
    private final List<UUID> portals=new ArrayList<>();
    private final List<Object> blockEvents=new ArrayList<>();
    private boolean failBreakOnce;
    public void onInitialize(){
        if(FabricLoader.getInstance().getEnvironmentType()!=EnvType.SERVER)return;
        Control.check(Files.exists(Path.of(".immersive-portals-phase6-test-clone")),"Driver requires marked clone");
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{
            if(player.getName().getString().startsWith("Phase6")){
                blockEvents.add(Map.of("player",player.getName().getString(),"eventWorld",world.dimension().identifier().toString(),"playerWorld",player.level().dimension().identifier().toString(),"position",pos.toString()));
                while(blockEvents.size()>20)blockEvents.removeFirst();Control.write("block-events.json",blockEvents);
                if(failBreakOnce){failBreakOnce=false;throw new IllegalStateException("expected break callback regression-test exception");}
            }return true;
        });
        var old=Control.read("server-command.json");sequence=old==null?0:old.get("seq").getAsLong();
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            Control.check(server.getPort()==25681,"Unsafe test server port");
            Control.write("driver-server-ready.json",Map.of("pid",ProcessHandle.current().pid(),"ready",true));
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(++ticks%4!=0)return;
            var command=Control.read("server-command.json");
            if(command!=null&&command.get("seq").getAsLong()>sequence){
                sequence=command.get("seq").getAsLong();
                try{action(server,command);Control.write("server-response.json",Map.of("seq",sequence,"status","PASS"));}
                catch(Throwable e){Control.write("server-response.json",Map.of("seq",sequence,"status","FAIL","error",e.toString()));e.printStackTrace();}
            }
            state(server);
        });
    }
    private void action(MinecraftServer server,JsonObject c){
        switch(c.get("action").getAsString()){
            case "command" -> {
                var source=server.createCommandSourceStack();
                if(c.has("player"))source=Objects.requireNonNull(server.getPlayerList().getPlayerByName(c.get("player").getAsString())).createCommandSourceStack();
                server.getCommands().performPrefixedCommand(source,c.get("command").getAsString());
            }
            case "fixture" -> fixture(server,c.get("mode").getAsString());
            case "phase81-fixture" -> Phase81Fixtures.create(server,c.get("mode").getAsString());
            case "save" -> server.saveEverything(false,true,true);
            case "catalog" -> Control.write("catalog.json",BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).filter(s->!s.startsWith("minecraft:")).sorted().toList());
            case "break-finally-check" -> {
                var p=Objects.requireNonNull(server.getPlayerList().getPlayerByName("Phase6A"));
                var pos=new net.minecraft.core.BlockPos(0,102,-1);
                var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try{
                    var packet=new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,pos,net.minecraft.core.Direction.SOUTH,0);
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.STREAM_CODEC.encode(buf,packet);
                    failBreakOnce=true;
                    try{qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.RemoteCallables.processPlayerActionPacket(p,Level.NETHER,qouteall.imm_ptl.core.IPMcHelper.bufToBytes(buf));throw new AssertionError("Expected callback did not throw");}
                    catch(IllegalStateException expected){Control.check(expected.getMessage().startsWith("expected break callback"),"Wrong failure");}
                    Control.check(!failBreakOnce,"Before-break hook not reached");
                    Control.check(qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.REDIRECT_CONTEXT.get()==null,"Leaked interaction context");
                    Control.check(qouteall.imm_ptl.core.network.PacketRedirection.getForceRedirectDimension()==null,"Leaked network context");
                    var field=net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredField("level");field.setAccessible(true);
                    Control.check(field.get(p.gameMode)==p.level(),"Leaked game-mode world");
                    Control.write("break-finally-check.json",Map.of("status","PASS","gameModeWorldRestored",true,"interactionContextRestored",true,"packetContextRestored",true));
                }catch(ReflectiveOperationException e){throw new RuntimeException(e);}finally{failBreakOnce=false;buf.release();}
            }
            case "polymer-virtual-check" -> {
                try{
                    var player=Objects.requireNonNull(server.getPlayerList().getPlayerByName("Phase6A"));
                    var chunk=player.level().getChunkAt(player.blockPosition());
                    var bridge=Class.forName("eu.pb4.polymer.virtualentity.impl.compat.ImmersivePortalsUtils");
                    var viewers=(List<?>)bridge.getMethod("getPlayerTracking",net.minecraft.world.level.chunk.LevelChunk.class).invoke(null,chunk);
                    Control.check(viewers!=null&&viewers.contains(player),"Polymer viewer list missing recipient");
                    var core=Class.forName("eu.pb4.polymer.core.impl.compat.ImmersivePortalsUtils");
                    Control.check(viewers.equals(core.getMethod("getPlayerTracking",net.minecraft.world.level.chunk.LevelChunk.class).invoke(null,chunk)),"Polymer core chunk overload mismatch");
                    Control.check(viewers.equals(core.getMethod("getPlayerTracking",net.minecraft.resources.ResourceKey.class,net.minecraft.world.level.ChunkPos.class).invoke(null,player.level().dimension(),chunk.getPos())),"Polymer core coordinate overload mismatch");
                    Control.check((Boolean)bridge.getMethod("isPlayerTracking",net.minecraft.server.level.ServerPlayer.class,net.minecraft.world.level.chunk.LevelChunk.class).invoke(null,player,chunk),"Polymer tracking predicate false");
                    var redirect=qouteall.imm_ptl.core.network.PacketRedirection.getForceRedirectDimension();
                    var target=server.getLevel(Level.NETHER);
                    Runnable check=()->Control.check(qouteall.imm_ptl.core.network.PacketRedirection.getForceRedirectDimension()==Level.NETHER,"Missing virtual-entity packet redirect");
                    bridge.getMethod("callRedirected",ServerLevel.class,Runnable.class).invoke(null,target,check);
                    Control.check(qouteall.imm_ptl.core.network.PacketRedirection.getForceRedirectDimension()==redirect,"Leaked packet redirect");
                    try{bridge.getMethod("callRedirected",ServerLevel.class,Runnable.class).invoke(null,target,(Runnable)()->{throw new IllegalStateException("expected regression-test exception");});throw new AssertionError("Exception lost");}
                    catch(java.lang.reflect.InvocationTargetException expected){Control.check(expected.getCause() instanceof IllegalStateException,"Wrong exception");}
                    Control.check(qouteall.imm_ptl.core.network.PacketRedirection.getForceRedirectDimension()==redirect,"Leaked redirect after failure");
                    Control.write("polymer-virtual-check.json",Map.of("status","PASS","viewers",viewers.size(),"recipient",player.getUUID().toString(),"redirectFinally",true));
                }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            }
            case "graves" -> {
                try{
                    var type=Class.forName("eu.pb4.graves.grave.GraveManager");var manager=type.getField("INSTANCE").get(null);
                    var graves=(Collection<?>)type.getMethod("getAll").invoke(manager);List<Object> entries=new ArrayList<>();
                    for(var grave:graves){var cls=grave.getClass();entries.add(Map.of("id",cls.getMethod("getId").invoke(grave),"location",cls.getMethod("getLocation").invoke(grave).toString(),"items",cls.getMethod("getItemCount").invoke(grave),"removed",cls.getMethod("isRemoved").invoke(grave)));}
                    Control.write("graves.json",entries);
                }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            }
            case "block" -> {
                var p=Objects.requireNonNull(server.getPlayerList().getPlayerByName(c.get("player").getAsString()));
                var pos=new net.minecraft.core.BlockPos(c.get("x").getAsInt(),c.get("y").getAsInt(),c.get("z").getAsInt());
                var level=c.has("dimension")?java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(),false).filter(l->l.dimension().identifier().toString().equals(c.get("dimension").getAsString())).findFirst().orElseThrow():p.level();
                var state=level.getBlockState(pos);
                Control.write("block-result.json",Map.of("state",state.toString(),"position",pos.toString(),"dimension",level.dimension().identifier().toString()));
            }
            default -> throw new IllegalArgumentException("Unknown test action");
        }
    }
    private static void command(MinecraftServer server,String command){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command);}
    private void fixture(MinecraftServer server,String mode){
        for(var level:server.getAllLevels())for(var id:portals){var e=level.getEntity(id);if(e!=null)e.discard();}
        portals.clear();
        command(server,"forceload add -16 -16 64 16");
        command(server,"fill -8 200 -12 64 200 8 minecraft:glowstone");
        command(server,"fill -8 201 -12 64 208 8 minecraft:air");
        command(server,"fill -8 200 -8 8 208 -8 minecraft:purple_concrete");
        command(server,"fill 24 196 -8 40 212 -8 minecraft:emerald_block");
        command(server,"fill 40 196 -8 56 212 -8 minecraft:lapis_block");
        if(mode.equals("same")||mode.equals("rotated")){
            Portal a=portal(server.overworld(),new Vec3(-1.5,202.5,-2.5),server.overworld(),new Vec3(32.5,202.5,-2.5));
            if(mode.equals("rotated")){a.setRotationTransformation(DQuaternion.rotationByDegrees(new Vec3(0,0,1),90));a.reloadAndSyncToClient();}
            portal(server.overworld(),new Vec3(2.5,202.5,-2.5),server.overworld(),new Vec3(48.5,202.5,-2.5));
        }else{
            ServerLevel destination=mode.equals("nether")?server.getLevel(Level.NETHER):
                java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(),false).filter(l->l.dimension().identifier().toString().equals("chronodawn:chronodawn")).findFirst().orElseThrow();
            String prefix="execute in "+destination.dimension().identifier()+" run ";
            command(server,prefix+"forceload add -16 -16 16 16");
            command(server,prefix+"fill -6 100 -12 6 108 8 minecraft:obsidian");
            command(server,prefix+"fill -5 101 -11 5 107 7 minecraft:air");
            command(server,prefix+"fill -5 100 -11 5 100 7 minecraft:glowstone");
            command(server,prefix+"fill -6 100 -8 6 108 -8 minecraft:gold_block");
            portal(server.overworld(),new Vec3(.5,202.5,-2.5),destination,new Vec3(.5,102.5,.5));
            portal(destination,new Vec3(.5,102.5,-5.5),server.overworld(),new Vec3(.5,202.5,4.5));
        }
        Control.write("fixture.json",Map.of("mode",mode,"portals",portals));
    }
    private Portal portal(ServerLevel source,Vec3 origin,ServerLevel destination,Vec3 target){
        Portal p=Portal.ENTITY_TYPE.create(source,EntitySpawnReason.COMMAND);Control.check(p!=null,"Portal creation failed");
        p.setOriginPos(origin);p.setDestinationDimension(destination.dimension());p.setDestination(target);
        p.setOrientationAndSize(new Vec3(1,0,0),new Vec3(0,1,0),3,4);
        Control.check(source.addFreshEntity(p),"Portal spawn failed");portals.add(p.getUUID());return p;
    }
    private void state(MinecraftServer server){
        Map<String,Object> players=new TreeMap<>();
        var ops=server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(var p:server.getPlayerList().getPlayers()){
            Map<String,Object> data=new LinkedHashMap<>();
            data.put("uuid",p.getUUID().toString());data.put("dimension",p.level().dimension().identifier().toString());
            data.put("x",p.getX());data.put("y",p.getY());data.put("z",p.getZ());data.put("yaw",p.getYRot());data.put("health",p.getHealth());
            List<Object> items=new ArrayList<>();for(int i=0;i<p.getInventory().getContainerSize();i++){
                var item=p.getInventory().getItem(i);items.add(item.isEmpty()?JsonNull.INSTANCE:ItemStack.CODEC.encodeStart(ops,item).getOrThrow());
            }data.put("inventory",items);
            try{
                Object attachment=Class.forName("eu.pb4.trinkets.api.TrinketsApi").getMethod("getAttachment",net.minecraft.world.entity.LivingEntity.class).invoke(null,p);
                var inventories=(Map<?,?>)Class.forName("eu.pb4.trinkets.api.TrinketAttachment").getMethod("getInventories").invoke(attachment);
                Map<String,Object> trinkets=new TreeMap<>();
                for(var entry:inventories.entrySet()){
                    var container=(net.minecraft.world.Container)entry.getValue();List<Object> stacks=new ArrayList<>();
                    for(int i=0;i<container.getContainerSize();i++){var stack=container.getItem(i);stacks.add(stack.isEmpty()?JsonNull.INSTANCE:ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());}
                    trinkets.put(entry.getKey().toString(),stacks);
                }data.put("trinkets",trinkets);
            }catch(Exception e){data.put("trinketReadError",e.toString());}
            data.put("maxHealth",p.getAttributeValue(Attributes.MAX_HEALTH));data.put("attackDamage",p.getAttributeValue(Attributes.ATTACK_DAMAGE));
            try{var field=net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredField("level");field.setAccessible(true);data.put("gameModeWorld",((ServerLevel)field.get(p.gameMode)).dimension().identifier().toString());}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            int copies=0;for(var l:server.getAllLevels())for(var e:l.getAllEntities())if(e.getUUID().equals(p.getUUID()))copies++;
            data.put("copies",copies);players.put(p.getName().getString(),data);
        }
        Map<String,Object> worlds=new TreeMap<>();for(var l:server.getAllLevels())worlds.put(l.dimension().identifier().toString(),Map.of("savingDisabled",l.noSave,"chunks",l.getChunkSource().getLoadedChunksCount()));
        Control.write("server-state.json",Map.of("pid",ProcessHandle.current().pid(),"tick",server.getTickCount(),"players",players,"worlds",worlds));
    }
}
