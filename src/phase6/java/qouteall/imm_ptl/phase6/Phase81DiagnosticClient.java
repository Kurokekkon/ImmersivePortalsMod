package qouteall.imm_ptl.phase6;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Separately packaged opt-in helper for the next real-client seam report. */
public class Phase81DiagnosticClient implements ClientModInitializer {
    public void onInitializeClient(){ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->{
        dispatcher.register(ClientCommands.literal("ipdiag")
            .then(ClientCommands.literal("start").executes(c->{Phase81Probe.start("real-client-seam",false);c.getSource().sendFeedback(Component.literal("IP diagnostics recording (bounded); move/look near the portal, then /ipdiag stop."));return 1;}))
            .then(ClientCommands.literal("stop").executes(c->{
                var path=FabricLoader.getInstance().getGameDir().resolve("debug/ip-phase81-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+java.util.UUID.randomUUID()+".json");
                Phase81Probe.finish(path);c.getSource().sendFeedback(Component.literal("IP diagnostic report: "+path));return 1;
            })));
    });}
}

