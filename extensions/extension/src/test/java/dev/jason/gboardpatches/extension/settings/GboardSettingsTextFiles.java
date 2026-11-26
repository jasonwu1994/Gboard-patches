package dev.jason.gboardpatches.extension.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the per-locale settings text files that the build generates resources from. */
public final class GboardSettingsTextFiles {
    public static final String ENGLISH = "values";
    public static final String TRADITIONAL_CHINESE = "values-b+zh+Hant";

    private GboardSettingsTextFiles() {
    }

    public static String read(String qualifier) throws IOException {
        Path path = Path.of("src/main/settings-text", qualifier, "gboard_settings_strings.xml");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    public static String english() throws IOException {
        return read(ENGLISH);
    }

    public static String traditionalChinese() throws IOException {
        return read(TRADITIONAL_CHINESE);
    }

    public static String englishAndTraditionalChinese() throws IOException {
        return english() + "\n" + traditionalChinese();
    }
}
