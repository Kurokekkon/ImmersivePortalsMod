package qouteall.imm_ptl.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test-only JUnit results: Fabric's client runner has no assumption/skip API. */
final class ClientTestReport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTestReport.class);
    private record Result(String status, String detail) {}
    private final String suite;
    private final Map<String, Result> results = new LinkedHashMap<>();

    ClientTestReport(String suite, String... planned) {
        this.suite = suite;
        for (String name : planned) results.put(name, new Result("SKIPPED", "Not reached"));
        write(); // Replace stale results even if this run cannot start.
    }

    void step(String name, Runnable action) {
        try {
            action.run();
            record(name, "PASS", "Assertions completed");
        }
        catch (RuntimeException | Error failure) {
            record(name, "FAIL", failure.toString());
            throw failure;
        }
    }

    void skip(String name, String reason) { record(name, "SKIPPED", reason); }

    void prerequisiteMissing(String reason, boolean required) {
        for (String name : results.keySet().toArray(String[]::new)) skip(name, reason);
        if (required) throw new AssertionError("SKIPPED required " + suite + ": " + reason);
    }

    private void record(String name, String status, String detail) {
        results.put(name, new Result(status, detail));
        LOGGER.info("[ImmPtl lifecycle] {} {} / {}: {}", status, suite, name, detail);
        write();
    }

    private void write() {
        long failed = results.values().stream().filter(r -> r.status.equals("FAIL")).count();
        long skipped = results.values().stream().filter(r -> r.status.equals("SKIPPED")).count();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"").append(suite).append("\" tests=\"").append(results.size())
            .append("\" failures=\"").append(failed).append("\" skipped=\"").append(skipped).append("\">\n");
        results.forEach((name, result) -> {
            xml.append("  <testcase classname=\"").append(suite).append("\" name=\"").append(escape(name)).append("\">");
            if (!result.status.equals("PASS")) {
                xml.append(result.status.equals("FAIL") ? "<failure message=\"" : "<skipped message=\"")
                    .append(escape(result.detail)).append("\"/>");
            }
            xml.append("</testcase>\n");
        });
        xml.append("</testsuite>\n");
        try {
            Path directory = Path.of("test-results");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(suite + ".xml"), xml);
        }
        catch (IOException e) { throw new IllegalStateException("Cannot write test evidence", e); }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
