package dev.jason.gboardpatches.extension.languagemodels;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** Content identities from reviewed Google ZIPs; a filename alone never proves a version. */
public final class GboardKeyCorrectionIdentity {
    private GboardKeyCorrectionIdentity() {}

    public static long versionForHash(String hash) {
        if ("6802978e328616a2d7de1e23347cd7daf896ac0d0e2be5771f4921a86628692c".equals(hash)) return 2020082801L;
        if ("11333d811d060b6dd06de02ced8dd71717696a39228d7b083b697005aedbf248".equals(hash)) return 2024052201L;
        return 0;
    }

    public static String hash(File file) {
        if (!file.getName().endsWith(".tflite") || !file.isFile() || file.length() > 262144) return "";
        try (FileInputStream stream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; int count; int total = 0;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > 262144) return "";
                digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder();
            for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (Exception unavailable) { return ""; }
    }
}
