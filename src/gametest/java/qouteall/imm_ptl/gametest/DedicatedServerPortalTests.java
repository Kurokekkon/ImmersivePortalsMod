package qouteall.imm_ptl.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestDedicatedServerContextImpl;
import net.fabricmc.fabric.impl.client.gametest.util.DedicatedServerImplUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEServerChunkCache;
import qouteall.imm_ptl.core.ducks.IEServerPlayNetworkHandler;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.GlobalTrackedPortal;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;
import qouteall.imm_ptl.gametest.mixin.TicketStorageAccessor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Real TCP client -> dedicated server lifecycle, using only disposable build directories. */
public class DedicatedServerPortalTests implements FabricClientGameTest {
    private static final Vec3 ORIGIN = new Vec3(0.5, 82.5, -2.5);
    private static final Vec3 DESTINATION = new Vec3(0.5, 112.5, 0.5);
    private static final Vec3 RETURN_ORIGIN = new Vec3(0.5, 112.5, -8.5);
    private static final Vec3 RETURN_DESTINATION = new Vec3(0.5, 82.5, 8.5);
    private UUID outwardId;
    private UUID returnId;
    private UUID globalId;
    private UUID playerId;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientTestReport report = new ClientTestReport("dedicated-lifecycle", "startup", "client-login",
            "portal-creation", "overworld-to-nether", "nether-to-overworld", "disconnect", "reconnect",
            "server-stop", "restart", "ordinary-persistence", "global-persistence", "restart-travel",
            "mounted-inventory", "portal-ticket-release", "client-cleanup", "final-stop", "two-real-clients");
        Path runDirectory = context.computeOnClient(mc -> mc.gameDirectory.toPath().toAbsolutePath().normalize());
        // The process cwd is already build/clientgametest. Never permit a production/world override.
        check(runDirectory.equals(Path.of("").toAbsolutePath().normalize())
            && runDirectory.endsWith(Path.of("build/clientgametest")), "Unsafe lifecycle run directory: " + runDirectory);
        if (!eulaAccepted(runDirectory)) {
            report.prerequisiteMissing("Accept the Minecraft EULA at " + runDirectory.resolve("eula.txt"),
                !System.getProperty("imm_ptl.test.selection", "all").equals("all"));
            return;
        }
        report.skip("two-real-clients", "Fabric provides one Minecraft client singleton and max-players=1; no second real client harness");
        report.step("lifecycle", () -> {
            if (System.getProperty("imm_ptl.test.selection", "all").equals("mounted")) mountedOnly(context, report, runDirectory);
            else lifecycle(context, report, runDirectory);
        });
    }

    private void mountedOnly(ClientGameTestContext context, ClientTestReport report, Path runDirectory) {
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties(runDirectory))) {
            prepareRooms(server);
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksRender();
                playerId = server.computeOnServer(s -> player(s).getUUID());
                setupPlayer(server, Level.OVERWORLD, new Vec3(0.5, 81, 0.5));
                connection.waitForClientboundPackets();
                createPortals(server);
                waitForView(context, outwardId);
                report.step("mounted-inventory", () -> mountedInventory(context, server, connection));
            }
        }
    }

    private void lifecycle(ClientGameTestContext context, ClientTestReport report, Path runDirectory) {
        Properties properties = properties(runDirectory);
        Path world = Path.of(properties.getProperty("level-name"));
        check(world.startsWith(runDirectory) && !Files.exists(world), "Refusing to recreate an existing world: " + world);
        Vec3 savedPosition;
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
            report.step("startup", () -> server.runOnServer(s -> {
                check(!s.isSingleplayer(), "Expected a dedicated server");
                check(FabricLoader.getInstance().isModLoaded("immersive_portals"), "Immersive Portals absent");
                check(FabricLoader.getInstance().isModLoaded("dimlib"), "DimLib absent");
                check(s.getLevel(Level.NETHER) != null, "Nether missing");
            }));
            prepareRooms(server);
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksRender();
                playerId = server.computeOnServer(s -> player(s).getUUID());
                report.step("client-login", () -> assertSession(context, server, Level.OVERWORLD));
                setupPlayer(server, Level.OVERWORLD, new Vec3(0.5, 81, 0.5));
                connection.waitForClientboundPackets();
                report.step("portal-creation", () -> {
                    createPortals(server);
                    waitForView(context, outwardId);
                    server.waitFor(s -> portalTicketCount(s.getLevel(Level.NETHER)) > 0, 600);
                });
                report.step("overworld-to-nether", () -> walk(context, server, Level.NETHER, DESTINATION));
                setupPlayer(server, Level.NETHER, new Vec3(0.5, 111, -5.5));
                connection.waitForClientboundPackets();
                report.step("nether-to-overworld", () -> walk(context, server, Level.OVERWORLD, RETURN_DESTINATION));
                savedPosition = server.computeOnServer(s -> player(s).position());
            }
            report.step("disconnect", () -> assertDisconnected(context, server));
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksRender();
                Vec3 expectedPosition = savedPosition;
                report.step("reconnect", () -> {
                    assertSession(context, server, Level.OVERWORLD);
                    check(server.computeOnServer(s -> player(s).position().distanceTo(expectedPosition)) < 0.1,
                        "Reconnect changed the saved position");
                    check(context.computeOnClient(mc -> mc.player.position().distanceTo(expectedPosition)) < 0.1,
                        "Client position stale after reconnect");
                    setupPlayer(server, Level.OVERWORLD, new Vec3(0.5, 81, 0.5));
                    connection.waitForClientboundPackets();
                    waitForView(context, outwardId);
                });
            }
            server.runOnServer(s -> s.saveEverything(false, true, true));
        }
        report.step("server-stop", () -> assertServerStopped(context));
        check(Files.isRegularFile(world.resolve("level.dat")), "Missing saved level.dat before restart");
        // createServer() DELETES its world. Restart through the same Fabric startup/context implementation instead.
        try (TestDedicatedServerContext server = new TestDedicatedServerContextImpl(context,
            DedicatedServerImplUtil.start(context, properties))) {
            report.step("restart", () -> server.runOnServer(s -> check(!s.isSingleplayer(), "Restart is not dedicated")));
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksRender();
                server.runCommand("forceload add -16 -16 80 16");
                server.waitFor(s -> s.overworld().getEntity(outwardId) instanceof Portal, 400);
                report.step("ordinary-persistence", () -> server.runOnServer(s -> {
                    assertPortal((Portal) s.overworld().getEntity(outwardId), outwardId, ORIGIN, Level.NETHER, DESTINATION, 4, 5);
                    s.getLevel(Level.NETHER).getChunk(0, -1);
                    assertPortal((Portal) s.getLevel(Level.NETHER).getEntity(returnId), returnId,
                        RETURN_ORIGIN, Level.OVERWORLD, RETURN_DESTINATION, 4, 5);
                }));
                report.step("global-persistence", () -> server.runOnServer(s -> {
                    Portal portal = GlobalPortalStorage.get(s.overworld()).data.stream()
                        .filter(p -> p.getUUID().equals(globalId)).findFirst().orElseThrow();
                    assertPortal(portal, globalId, new Vec3(64.5, 82.5, -2.5), Level.NETHER,
                        new Vec3(64.5, 112.5, 0.5), 2, 3);
                    check(portal.getIsGlobal(), "Global flag lost");
                }));
                report.step("restart-travel", () -> {
                    assertSession(context, server, Level.OVERWORLD);
                    waitForView(context, outwardId);
                    walk(context, server, Level.NETHER, DESTINATION);
                    setupPlayer(server, Level.NETHER, new Vec3(0.5, 111, -5.5));
                    connection.waitForClientboundPackets();
                    walk(context, server, Level.OVERWORLD, RETURN_DESTINATION);
                });
                report.step("mounted-inventory", () -> mountedInventory(context, server, connection));
                report.step("portal-ticket-release", () -> {
                    server.runOnServer(s -> {
                        s.overworld().getEntity(outwardId).discard();
                        s.getLevel(Level.NETHER).getEntity(returnId).discard();
                        GlobalPortalStorage storage = GlobalPortalStorage.get(s.overworld());
                        storage.removePortals(p -> p.getUUID().equals(globalId));
                    });
                    int purgeTicks = ImmPtlChunkTracking.updateInterval * (ImmPtlChunkTracking.defaultDelayUnloadGenerations + 4);
                    server.waitFor(s -> portalTicketCount(s.getLevel(Level.NETHER)) == 0
                        && ImmPtlChunkTickets.get(s.getLevel(Level.NETHER)).getLoadedChunkNum() == 0
                        && !ImmPtlChunkTracking.getPlayerInfo(player(s)).visibleDimensions.contains(Level.NETHER), purgeTicks + 200);
                    context.waitFor(mc -> {
                        ClientLevel nether = findClientWorld(Level.NETHER);
                        return nether == null || nether.getChunkSource().getLoadedChunksCount() == 0;
                    }, 400);
                });
            }
            report.step("client-cleanup", () -> assertDisconnected(context, server));
        }
        report.step("final-stop", () -> assertServerStopped(context));
    }

    private void createPortals(TestDedicatedServerContext server) {
        server.runOnServer(s -> {
            outwardId = TestUtil.spawnPortal(s.overworld(), ORIGIN, Level.NETHER, DESTINATION, 4, 5).getUUID();
            returnId = TestUtil.spawnPortal(s.getLevel(Level.NETHER), RETURN_ORIGIN,
                Level.OVERWORLD, RETURN_DESTINATION, 4, 5).getUUID();
            GlobalTrackedPortal global = GlobalTrackedPortal.ENTITY_TYPE.create(s.overworld(), EntitySpawnReason.LOAD);
            check(global != null, "Cannot create global portal");
            global.setOriginPos(new Vec3(64.5, 82.5, -2.5));
            global.setDestinationDimension(Level.NETHER);
            global.setDestination(new Vec3(64.5, 112.5, 0.5));
            global.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2, 3);
            GlobalPortalStorage.get(s.overworld()).addPortal(global);
            globalId = global.getUUID();
        });
    }

    private void mountedInventory(ClientGameTestContext context, TestDedicatedServerContext server,
                                  TestDedicatedServerConnection connection) {
        setupPlayer(server, Level.OVERWORLD, new Vec3(0.5, 81, -1.5));
        connection.waitForClientboundPackets();
        UUID vehicleId = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            p.getInventory().setItem(8, new ItemStack(Items.EMERALD, 11));
            ChestBoat boat = EntityTypes.OAK_CHEST_BOAT.create(s.overworld(), EntitySpawnReason.COMMAND);
            check(boat != null, "Cannot create chest boat");
            boat.snapTo(0.5, 81, -1.5, 180, 0);
            boat.setItem(0, new ItemStack(Items.GOLD_INGOT, 13));
            boat.setItem(5, new ItemStack(Items.NETHERITE_INGOT, 3));
            check(s.overworld().addFreshEntity(boat), "Cannot add chest boat");
            check(p.startRiding(boat, true, true), "Cannot mount chest boat");
            return boat.getUUID();
        });
        context.waitFor(mc -> mc.player.getVehicle() != null && mc.player.getVehicle().getUUID().equals(vehicleId), 200);
        server.waitFor(s -> !((IEServerPlayNetworkHandler) player(s).connection).ip_hasAwaitingTeleport(), 200);
        context.getInput().holdKey(options -> options.keyUp);
        try { server.waitFor(s -> player(s).level().dimension() == Level.NETHER, 400); }
        finally { context.getInput().releaseKey(options -> options.keyUp); }
        context.waitFor(mc -> mc.level.dimension() == Level.NETHER && mc.player.getVehicle() != null, 400);
        assertSession(context, server, Level.NETHER);
        context.waitFor(mc -> count(mc.player.getInventory(), Items.DIAMOND) == 7
            && count(mc.player.getInventory(), Items.EMERALD) == 11 && total(mc.player.getInventory()) == 18, 200);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Entity vehicle = p.getVehicle();
            check(vehicle instanceof ChestBoat && vehicle.getUUID().equals(vehicleId), "Wrong or missing mounted vehicle");
            check(vehicle.level().dimension() == Level.NETHER, "Vehicle remained in source dimension");
            int vehicles = 0;
            for (ServerLevel level : s.getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e.getUUID().equals(vehicleId)) vehicles++;
                    check(!(e instanceof ItemEntity item) || !(item.getItem().is(Items.GOLD_INGOT)
                        || item.getItem().is(Items.NETHERITE_INGOT)), "Vehicle contents dropped/duplicated");
                }
            }
            check(vehicles == 1, "Vehicle UUID present " + vehicles + " times");
            check(s.overworld().getEntity(vehicleId) == null, "Vehicle remained registered in source dimension");
            Container inventory = (Container) vehicle;
            check(count(inventory, Items.GOLD_INGOT) == 13 && count(inventory, Items.NETHERITE_INGOT) == 3,
                "Vehicle inventory changed");
            check(total(inventory) == 16, "Unexpected additional vehicle items");
            check(count(p.getInventory(), Items.DIAMOND) == 7 && count(p.getInventory(), Items.EMERALD) == 11
                && total(p.getInventory()) == 18, "Player inventory changed");
            p.stopRiding();
            vehicle.discard();
        });
        assertSession(context, server, Level.NETHER);
        setupPlayer(server, Level.NETHER, new Vec3(0.5, 111, -5.5));
        connection.waitForClientboundPackets();
        walk(context, server, Level.OVERWORLD, RETURN_DESTINATION);
    }

    private static int count(Container container, Item item) {
        int result = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    private static int total(Container container) {
        int result = 0;
        for (int i = 0; i < container.getContainerSize(); i++) result += container.getItem(i).getCount();
        return result;
    }

    private void assertSession(ClientGameTestContext context, TestDedicatedServerContext server, ResourceKey<Level> dimension) {
        server.waitFor(s -> s.getPlayerList().getPlayers().size() == 1 && player(s).level().dimension() == dimension
            && !((IEServerPlayNetworkHandler) player(s).connection).ip_hasAwaitingTeleport(), 200);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(p.getUUID().equals(playerId), "Player identity changed");
            int copies = 0;
            for (ServerLevel level : s.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) if (entity.getUUID().equals(playerId)) copies++;
            }
            check(copies == 1, "Player exists in " + copies + " dimension entity stores");
        });
        context.waitFor(mc -> mc.level != null && mc.player != null && mc.level.dimension() == dimension
            && mc.player.level() == mc.level && mc.player.getUUID().equals(playerId), 200);
        Vec3 position = server.computeOnServer(s -> player(s).position());
        check(context.computeOnClient(mc -> mc.player.position().distanceTo(position)) < 0.5,
            "Server/client positions disagree");
    }

    private void walk(ClientGameTestContext context, TestDedicatedServerContext server, ResourceKey<Level> dimension, Vec3 target) {
        trace(context, server, "before walk");
        context.getInput().holdKey(options -> options.keyUp);
        try { context.waitFor(mc -> mc.level != null && mc.level.dimension() == dimension, 400); }
        finally { context.getInput().releaseKey(options -> options.keyUp); }
        trace(context, server, "after walk");
        try { assertSession(context, server, dimension); }
        catch (AssertionError failure) {
            trace(context, server, "failed walk");
            context.takeScreenshot("dedicated_failed_walk");
            throw failure;
        }
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(p.position().distanceTo(target) < 8, "Unexpected destination position " + p.position());
            check(Math.abs(Math.IEEEremainder(p.getYRot() - 180, 360)) < 2 && Math.abs(p.getXRot()) < 2,
                "Player facing changed unexpectedly");
        });
        check(context.computeOnClient(mc -> Math.abs(Math.IEEEremainder(mc.player.getYRot() - 180, 360)) < 2
            && Math.abs(mc.player.getXRot()) < 2), "Client facing changed unexpectedly");
    }

    private static void trace(ClientGameTestContext context, TestDedicatedServerContext server, String label) {
        System.out.println("[ImmPtl lifecycle position] " + label + " server=" + server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            return p.level().dimension() + " " + p.position() + " yaw=" + p.getYRot()
                + " pending=" + ((IEServerPlayNetworkHandler) p.connection).ip_hasAwaitingTeleport();
        }) + " client=" + context.computeOnClient(mc -> mc.level.dimension() + " " + mc.player.position()
            + " yaw=" + mc.player.getYRot() + " screen=" + mc.gui.screen()));
    }

    private static void setupPlayer(TestDedicatedServerContext server, ResourceKey<Level> dimension, Vec3 feet) {
        server.runCommand("execute in " + dimension.identifier() + " run tp @a " + feet.x + " " + feet.y + " " + feet.z + " 180 0");
    }

    private static void prepareRooms(TestDedicatedServerContext server) {
        server.runCommand("gamemode creative @a");
        server.runCommand("forceload add -16 -16 80 16");
        for (String dimension : new String[]{"minecraft:overworld", "minecraft:the_nether"}) {
            int y = dimension.endsWith("overworld") ? 80 : 110;
            server.runOnServer(s -> {
                ServerLevel level = dimension.endsWith("overworld") ? s.overworld() : s.getLevel(Level.NETHER);
                for (int x = -1; x <= 0; x++) for (int z = -1; z <= 1; z++) level.getChunk(x, z);
            });
            server.runCommand("execute in " + dimension + " run fill -8 " + y + " -16 8 " + y + " 16 minecraft:blue_ice");
            server.runCommand("execute in " + dimension + " run fill -8 " + (y + 1) + " -16 8 " + (y + 7) + " 16 minecraft:air");
            server.runOnServer(s -> {
                ServerLevel level = dimension.endsWith("overworld") ? s.overworld() : s.getLevel(Level.NETHER);
                check(level.getBlockState(new net.minecraft.core.BlockPos(0, y, 0)).is(net.minecraft.world.level.block.Blocks.BLUE_ICE)
                    && level.getBlockState(new net.minecraft.core.BlockPos(0, y + 2, 0)).isAir(), "Test room was not prepared");
            });
        }
    }

    private static void waitForView(ClientGameTestContext context, UUID portalId) {
        context.waitFor(mc -> mc.level != null && findEntity(mc.level, portalId) instanceof Portal, 400);
        context.waitFor(mc -> {
            ClientLevel nether = findClientWorld(Level.NETHER);
            return nether != null && nether.getChunkSource().getLoadedChunksCount() > 0
                && PortalViewRenderer.isViewDrawnThisFrame(portalId);
        }, 1200);
    }

    private static void assertPortal(Portal portal, UUID uuid, Vec3 origin, ResourceKey<Level> destinationDimension,
                                     Vec3 destination, double width, double height) {
        check(portal != null && portal.getUUID().equals(uuid), "Portal identity missing after restart");
        check(portal.level().dimension() == (destinationDimension == Level.NETHER ? Level.OVERWORLD : Level.NETHER), "Source dimension changed");
        check(portal.getDestDim() == destinationDimension && TestUtil.near(portal.getOriginPos(), origin)
            && TestUtil.near(portal.getDestPos(), destination), "Portal transform changed after restart");
        check(Math.abs(portal.getWidth() - width) < 1e-6 && Math.abs(portal.getHeight() - height) < 1e-6,
            "Portal dimensions changed");
        check(TestUtil.near(portal.getAxisW(), new Vec3(1, 0, 0)) && TestUtil.near(portal.getAxisH(), new Vec3(0, 1, 0))
            && qouteall.q_misc_util.my_util.DQuaternion.isClose(portal.getRotationD(), qouteall.q_misc_util.my_util.DQuaternion.identity, 1e-6)
            && Math.abs(portal.getScaling() - 1) < 1e-6, "Portal orientation/scale changed");
    }

    private static int portalTicketCount(ServerLevel level) {
        var storage = ((IEServerChunkCache) level.getChunkSource()).ip_getTicketStorage();
        return ((TicketStorageAccessor) storage).ipTest_getTickets().values().stream()
            .mapToInt(tickets -> (int) tickets.stream().filter(t -> t.getType() == ImmPtlChunkTickets.TICKET_TYPE).count()).sum();
    }

    private static void assertDisconnected(ClientGameTestContext context, TestDedicatedServerContext server) {
        server.waitFor(s -> s.getPlayerList().getPlayers().isEmpty(), 200);
        check(context.computeOnClient(mc -> mc.level == null && mc.player == null
            && !ClientWorldLoader.getIsInitialized() && ClientWorldLoader.WORLD_RENDERER_MAP.isEmpty()
            && ClientWorldLoader.EXTRACTOR_MAP.isEmpty()), "Remote worlds/renderers retained after disconnect");
        server.waitFor(s -> {
            for (ServerLevel level : s.getAllLevels()) if (portalTicketCount(level) != 0) return false;
            return true;
        }, 400);
    }

    private static void assertServerStopped(ClientGameTestContext context) {
        check(context.computeOnClient(mc -> !ClientWorldLoader.getIsInitialized()), "Client world state survived server stop");
        check(ImmPtlChunkTickets.BY_DIMENSION.isEmpty(), "Server ticket managers survived stop");
    }

    private static ServerPlayer player(MinecraftServer server) {
        check(server.getPlayerList().getPlayers().size() == 1, "Expected one connected player; inspect server disconnect log");
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static Properties properties(Path runDirectory) {
        Properties properties = new Properties();
        properties.setProperty("level-name", runDirectory.resolve("phase1-world-" + UUID.randomUUID()).toString());
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("view-distance", "5");
        properties.setProperty("simulation-distance", "5");
        properties.setProperty("gamemode", "creative");
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            properties.setProperty("server-port", Integer.toString(socket.getLocalPort()));
        }
        catch (IOException e) { throw new IllegalStateException("Cannot reserve a local test port", e); }
        return properties;
    }

    private static boolean eulaAccepted(Path runDirectory) {
        try { return Files.readAllLines(runDirectory.resolve("eula.txt")).stream().anyMatch(line -> line.trim().equals("eula=true")); }
        catch (IOException e) { return false; }
    }

    private static Entity findEntity(ClientLevel level, UUID uuid) {
        for (Entity entity : level.entitiesForRendering()) if (entity.getUUID().equals(uuid)) return entity;
        return null;
    }

    private static ClientLevel findClientWorld(ResourceKey<Level> dimension) {
        if (ClientWorldLoader.getIsInitialized()) {
            for (ClientLevel world : ClientWorldLoader.getClientWorlds()) if (world.dimension() == dimension) return world;
        }
        return null;
    }
}
