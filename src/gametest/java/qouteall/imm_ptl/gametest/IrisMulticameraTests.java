package qouteall.imm_ptl.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.portal_view.PortalViewRenderer;

/** Focused active-pack regression. Baseline deliberately exercises the existing guard. */
public class IrisMulticameraTests implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("iris") || FabricLoader.getInstance().isModLoaded("nvidium"))
            throw new AssertionError("Phase 5 requires Iris and excludes Nvidium");
        String pack = System.getProperty("imm_ptl.test.selection", "").equals("phase5_secondary")
            ? "ComplementaryUnbound_r5.9.3.zip" : "ComplementaryReimagined_r5.9.3.zip";
        context.runOnClient(mc -> IrisShaderPackTests.IrisControl.setShaderPack(pack));
        if (!System.getProperty("imm_ptl.test.selection", "").equals("phase5_baseline")) {
            try { new SodiumMulticameraTests().runTest(context); }
            finally { context.runOnClient(mc -> IrisShaderPackTests.IrisControl.setShaderPack(null)); }
            return;
        }
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set 6000");
            server.runCommand("forceload add 240 -16 272 16");
            server.runCommand("fill -8 100 -8 8 100 8 minecraft:glowstone");
            server.runCommand("fill -8 101 -8 8 108 8 minecraft:air");
            server.runCommand("fill 248 96 -6 264 112 -6 minecraft:emerald_block");
            server.runCommand("tp @a 0.5 101 5.5 180 0");
            var portal = server.computeOnServer(s -> TestUtil.spawnPortal(s.overworld(),
                new Vec3(0.5,102.5,0.5),Level.OVERWORLD,new Vec3(256.5,102.5,0.5),3,4).getUUID());
            world.getConnection().waitForClientboundPackets();
            context.waitFor(mc -> mc.level.getEntity(portal) != null
                && mc.level.getChunkSource().getChunk(16,0,false)!=null,1200);
            context.waitTicks(40);
            context.runOnClient(mc -> {
                if (!IrisInterface.invoker.isShaders()
                    || !(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline))
                    throw new AssertionError("Shader pack/pipeline not active");
                System.out.println("[Phase 5 baseline] pack="+Iris.getCurrentPackName()
                    +" pipeline="+Iris.getPipelineManager().getPipelineNullable().getClass().getName()
                    +" portal="+portal+" exists=true views="+PortalViewRenderer.getViewCountThisFrame());
            });
            context.takeScreenshot("phase5_baseline_same_dimension");
            if (!context.computeOnClient(mc -> PortalViewRenderer.isViewDrawnThisFrame(portal)))
                throw new AssertionError("ACTIVE_SHADER_LIMITATION: existing same-dimension portal is suppressed");
        }
        finally { context.runOnClient(mc -> IrisShaderPackTests.IrisControl.setShaderPack(null)); }
    }
}
