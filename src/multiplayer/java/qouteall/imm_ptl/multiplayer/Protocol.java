package qouteall.imm_ptl.multiplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/** Test-local files coordinate independent processes, never Minecraft gameplay packets. */
final class Protocol {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final Path ROOT = Path.of(System.getProperty("imm_ptl.mp.root")).toAbsolutePath().normalize();
    static final String ROLE = System.getProperty("imm_ptl.mp.role");

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void validateProfile() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        check(ROOT.getParent().getFileName().toString().equals("phase2")
            && ROOT.getParent().getParent().getFileName().toString().equals("build"), "Unsafe test root " + ROOT);
        check(cwd.getParent().equals(ROOT), "Test cwd outside isolated root " + cwd);
        Map<String, String> mods = new TreeMap<>();
        FabricLoader.getInstance().getAllMods().forEach(m -> mods.put(m.getMetadata().getId(), m.getMetadata().getVersion().getFriendlyString()));
        write("mods-" + ROLE + "-" + ProcessHandle.current().pid() + ".json", Map.of(
            "pid", ProcessHandle.current().pid(), "role", ROLE, "java", System.getProperty("java.version"), "cwd", cwd.toString(), "mods", mods));
        for (String forbidden : new String[]{"sodium", "iris", "nvidium"}) check(!mods.containsKey(forbidden), "Contaminated baseline: " + forbidden);
        check("0.163.0+26.3".equals(mods.get("fabric-api")), "Wrong Fabric API " + mods.get("fabric-api"));
        check("26.3".equals(mods.get("minecraft")) && "0.19.5".equals(mods.get("fabricloader")), "Wrong Minecraft/Loader");
        check(mods.containsKey("immersive_portals") && mods.containsKey("dimlib"), "Missing core mods");
    }

    static JsonObject read(String name) {
        try {
            Path file = ROOT.resolve(name);
            if (!Files.exists(file)) return null;
            for (int attempt = 0; ; attempt++) {
                try { return JSON.fromJson(Files.readString(file), JsonObject.class); }
                catch (java.nio.file.FileSystemException busy) {
                    if (attempt >= 79) throw busy;
                    try { Thread.sleep(5); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                }
            }
        }
        catch (IOException e) { throw new IllegalStateException(e); }
    }

    static void write(String name, Object data) {
        try {
            Path target = ROOT.resolve(name);
            Path temporary = ROOT.resolve(name + ".tmp");
            Files.writeString(temporary, JSON.toJson(data));
            // Windows briefly prevents replacing a file while the coordinator reads it.
            // Retry the atomic rename; never expose a partially written state file.
            for (int attempt = 0; ; attempt++) {
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    break;
                }
                catch (java.nio.file.AccessDeniedException busy) {
                    if (attempt >= 79) throw busy;
                    try { Thread.sleep(5); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                }
            }
        }
        catch (IOException e) { throw new IllegalStateException("Cannot write test evidence " + name, e); }
    }
}
