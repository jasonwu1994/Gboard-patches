package dev.jason.gboardpatches.extension.settings;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;



/** Ratchet existing storage access: new runtime code should use readRuntimeSetting(). */
public final class GboardSettingsAccessArchitectureTest {
    private static final Pattern DIRECT_ACCESS = Pattern.compile(
            "\\bgetSharedPreferences\\s*\\(|\\b(?:GboardPatchesSettings\\s*\\.\\s*preferences"
                    + "|GboardRemoteSettings\\s*\\.\\s*remotePreferences)\\s*\\(");

    @Test public void newDirectStorageAccessRequiresReview() throws Exception {
        Properties legacy = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/gboard-settings-access-baseline.properties")) {
            assertNotNull("Missing reviewed legacy storage inventory", input);
            legacy.load(input);
        }
        Path root = Path.of("src/main/java/dev/jason/gboardpatches/extension");
        if (!Files.isDirectory(root)) root = Path.of("extensions/extension/src/main/java/dev/jason/gboardpatches/extension");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.equals("settings/GboardPatchesSettings.java")) continue;
                long actual = DIRECT_ACCESS.matcher(new String(Files.readAllBytes(path),
                        StandardCharsets.UTF_8)).results().count();
                int allowed = Integer.parseInt(legacy.getProperty(relative, "0"));
                if (actual > allowed) violations.add(relative + ": " + actual + " > " + allowed);
            }
        }
        assertTrue("New direct settings access bypasses the runtime contract. Use "
                + "GboardPatchesSettings.readRuntimeSetting(context, reader, stockFallback). "
                + "Do not expand the legacy inventory without a Direct Boot review: " + violations,
                violations.isEmpty());
    }

    @Test public void scannerRecognizesMultilineAccessAndAllowsGuardedReads() {
        assertEquals(1, DIRECT_ACCESS.matcher("context.\ngetSharedPreferences (name, 0)").results().count());
        assertEquals(1, DIRECT_ACCESS.matcher("GboardPatchesSettings \n . preferences (context)").results().count());
        assertEquals(0, DIRECT_ACCESS.matcher("GboardPatchesSettings.readRuntimeSetting(context, reader, false)")
                .results().count());
    }
}
