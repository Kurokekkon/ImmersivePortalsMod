package qouteall.imm_ptl.multiplayer;

import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;
import qouteall.imm_ptl.core.ducks.IEServerChunkCache;
import qouteall.imm_ptl.core.ducks.IEServerPlayNetworkHandler;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.multiplayer.mixin.ChunkTrackingAccessor;
import qouteall.imm_ptl.multiplayer.mixin.TicketStorageAccessor;

import java.nio.file.Files;
import java.util.*;

/** Loaded only from the multiplayer source set. All world operations occur on the server thread. */
public class MultiplayerServerDriver implements ModInitializer {
    private long lastSequence;
    private UUID outwardId, returnId, vehicleId;
    private int ticks;
    private boolean ready;

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        Protocol.validateProfile();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Only first-ever logins are positioned. Reconnect and restart must use saved state.
            if (handler.player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.LEAVE_GAME)) == 0
                && !Files.exists(Protocol.ROOT.resolve("joined-" + handler.player.getUUID()))) {
                double x = handler.player.getName().getString().equals("PortalTestA") ? 0.5 : 1.5;
                handler.player.connection.teleport(x, 81, 0.5, 180, 0);
                try { Files.writeString(Protocol.ROOT.resolve("joined-" + handler.player.getUUID()), "first login"); }
                catch (java.io.IOException e) { throw new IllegalStateException(e); }
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // All initializers have completed now: register after the runtime's
            // cleanup listeners rather than relying on mod initializer order.
            qouteall.imm_ptl.core.IPGlobal.SERVER_CLEANUP_EVENT.register(stopped -> {
                if (stopped != server) return;
                Protocol.write("stopped-server-" + ProcessHandle.current().pid() + ".json", Map.of(
                    "pid", ProcessHandle.current().pid(), "trackedPlayers", ChunkTrackingAccessor.players().size(),
                    "ticketManagers", ImmPtlChunkTickets.BY_DIMENSION.size(), "stopped", true));
            });
            Protocol.check(!server.isSingleplayer(), "Integrated server is not a Phase 2 server");
            Protocol.check(server.getPort() != 25565, "Production port forbidden");
            JsonObject old = Protocol.read("server-command.json");
            lastSequence = old == null ? 0 : old.get("seq").getAsLong();
            fixture(server);
            state(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ready) {
                // getChunk waits for terrain, not asynchronous saved entity loading.
                // Never recreate a persisted fixture to make this assertion pass.
                if (server.overworld().getEntity(outwardId) instanceof Portal
                    && server.getLevel(Level.NETHER).getEntity(returnId) instanceof Portal) {
                    ready = true;
                    state(server);
                    Protocol.write("ready-server.json", Map.of("pid", ProcessHandle.current().pid(), "port", server.getPort(), "ready", true));
                }
                else Protocol.check(ticks < 400, "Saved portal entities did not load within 400 ticks");
            }
            JsonObject command = Protocol.read("server-command.json");
            if (command != null && command.get("seq").getAsLong() > lastSequence) {
                lastSequence = command.get("seq").getAsLong();
                try {
                    action(server, command);
                    state(server);
                    Protocol.write("server-response.json", Map.of("seq", lastSequence, "status", "PASS"));
                }
                catch (Throwable failure) {
                    Protocol.write("server-response.json", Map.of("seq", lastSequence, "status", "FAIL", "error", failure.toString()));
                    failure.printStackTrace();
                }
            }
            if (++ticks % 2 == 0) state(server);
        });
    }

    private void fixture(MinecraftServer server) {
        command(server, "forceload add -16 -16 16 16");
        command(server, "execute in minecraft:the_nether run forceload add -16 -16 16 16");
        server.getLevel(Level.NETHER).getChunk(0, -1);
        JsonObject saved = Protocol.read("fixture.json");
        if (saved != null) {
            outwardId = UUID.fromString(saved.get("outward").getAsString());
            returnId = UUID.fromString(saved.get("return").getAsString());
            server.overworld().getChunk(0, -1);
            return;
        }
        for (ServerLevel level : List.of(server.overworld(), server.getLevel(Level.NETHER))) {
            int y = level.dimension() == Level.OVERWORLD ? 80 : 110;
            for (int x = -1; x <= 0; x++) for (int z = -1; z <= 1; z++) level.getChunk(x, z);
            String prefix = "execute in " + level.dimension().identifier() + " run ";
            command(server, prefix + "fill -8 " + y + " -16 8 " + y + " 16 minecraft:blue_ice");
            command(server, prefix + "fill -8 " + (y+1) + " -16 8 " + (y+7) + " 16 minecraft:air");
            Protocol.check(level.getBlockState(new BlockPos(0,y,0)).is(net.minecraft.world.level.block.Blocks.BLUE_ICE), "Fixture floor missing");
        }
        command(server, "setworldspawn 0 81 4");
        outwardId = portal(server.overworld(), new Vec3(0.5,82.5,-2.5), Level.NETHER, new Vec3(0.5,112.5,0.5)).getUUID();
        returnId = portal(server.getLevel(Level.NETHER), new Vec3(0.5,112.5,-8.5), Level.OVERWORLD, new Vec3(0.5,82.5,8.5)).getUUID();
        Protocol.write("fixture.json", Map.of("outward", outwardId, "return", returnId));
    }

    private static Portal portal(ServerLevel level, Vec3 origin, ResourceKey<Level> dest, Vec3 position) {
        Portal p = Portal.ENTITY_TYPE.create(level, EntitySpawnReason.COMMAND);
        Protocol.check(p != null, "Cannot create portal");
        p.setOriginPos(origin); p.setDestinationDimension(dest); p.setDestination(position);
        p.setOrientationAndSize(new Vec3(1,0,0),new Vec3(0,1,0),6,5);
        Protocol.check(level.addFreshEntity(p), "Cannot add portal");
        return p;
    }

    private void action(MinecraftServer server, JsonObject c) {
        switch (c.get("action").getAsString()) {
            case "setup" -> {
                String who = c.get("player").getAsString();
                String dim = c.get("dimension").getAsString();
                double x = who.equals("PortalTestA") ? 0.5 : 1.5;
                double y = dim.equals("minecraft:overworld") ? 81 : 111;
                double z = dim.equals("minecraft:overworld") ? 0.5 : -5.5;
                command(server, "execute in " + dim + " run tp " + who + " " + x + " " + y + " " + z + " 180 0");
            }
            case "mount" -> {
                ServerPlayer player = server.getPlayerList().getPlayerByName("PortalTestA");
                Protocol.check(player != null && player.level().dimension() == Level.OVERWORLD, "A not in source dimension");
                ChestBoat boat = EntityTypes.OAK_CHEST_BOAT.create(server.overworld(), EntitySpawnReason.COMMAND);
                Protocol.check(boat != null, "Cannot create chest boat");
                boat.snapTo(0.5,81,-1.5,180,0);
                boat.setItem(0,new ItemStack(Items.GOLD_INGOT,13));
                Protocol.check(server.overworld().addFreshEntity(boat), "Cannot add chest boat");
                player.connection.teleport(0.5,81,-1.5,180,0);
                Protocol.check(player.startRiding(boat,true,true), "Cannot mount A");
                vehicleId = boat.getUUID();
            }
            case "dismount" -> {
                ServerPlayer player = server.getPlayerList().getPlayerByName("PortalTestA");
                Entity vehicle = player.getVehicle();
                player.stopRiding();
                if (vehicle != null) vehicle.discard();
            }
            case "ping" -> command(server,"time add 1");
            case "stop" -> { server.saveEverything(false,true,true); server.halt(false); }
            default -> throw new AssertionError("Unknown server action " + c);
        }
    }

    private void state(MinecraftServer server) {
        Map<String,Object> players = new TreeMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int copies = 0;
            for (ServerLevel level : server.getAllLevels()) for (Entity e : level.getAllEntities()) if(e.getUUID().equals(player.getUUID())) copies++;
            Map<String,Object> p = new LinkedHashMap<>();
            p.put("uuid", player.getUUID().toString()); p.put("dimension", player.level().dimension().identifier().toString());
            p.put("x",player.getX()); p.put("y",player.getY()); p.put("z",player.getZ()); p.put("yaw",player.getYRot()); p.put("pitch",player.getXRot());
            p.put("copies",copies); p.put("pending",((IEServerPlayNetworkHandler) player.connection).ip_hasAwaitingTeleport());
            p.put("mounted",player.getVehicle()!=null);
            players.put(player.getName().getString(),p);
        }
        int tickets = 0, vehicleCopies = 0, portalCopies = 0;
        Map<String,Integer> entitiesByDimension = new TreeMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for(Entity e:level.getAllEntities()) {
                if(e.getUUID().equals(outwardId)) portalCopies++;
                if(vehicleId!=null && e.getUUID().equals(vehicleId)) {
                    vehicleCopies++; entitiesByDimension.merge(level.dimension().identifier().toString(),1,Integer::sum);
                }
            }
            var storage = ((IEServerChunkCache)level.getChunkSource()).ip_getTicketStorage();
            tickets += ((TicketStorageAccessor)storage).tickets().values().stream()
                .mapToInt(list -> (int)list.stream().filter(t -> t.getType()==ImmPtlChunkTickets.TICKET_TYPE).count()).sum();
        }
        List<String> tracked = ChunkTrackingAccessor.players().keySet().stream().map(p -> p.getUUID().toString()).sorted().toList();
        Map<String,Object> state = new LinkedHashMap<>();
        state.put("pid",ProcessHandle.current().pid()); state.put("tick",server.getTickCount()); state.put("players",players);
        state.put("trackedPlayers",tracked); state.put("portalTickets",tickets); state.put("portalCopies",portalCopies);
        state.put("vehicleCopies",vehicleCopies); state.put("vehicleDimensions",entitiesByDimension);
        state.put("vehicleUuid",vehicleId == null ? "" : vehicleId.toString());
        Portal p = (Portal)server.overworld().getEntity(outwardId);
        state.put("portalValid",p!=null && p.getDestDim()==Level.NETHER && p.getDestPos().distanceTo(new Vec3(0.5,112.5,0.5))<0.01);
        Protocol.write("server-state.json",state);
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }
}
