package dev.jason.gboardpatches.extension.languagemodels;

import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog.Family;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelSettings.Snapshot;

public final class GboardLanguageModelPolicy {
    private GboardLanguageModelPolicy() {}

    public static Object apply(String name, Object original, Object baked, Snapshot settings) {
        Family family = GboardLanguageModelCatalog.forFlag(name);
        if (settings == null || family == null || original == null) return original;
        String choice = settings.effectiveChoice(family);
        Object desired = GboardLanguageModelCatalog.APK.equals(choice) ? baked
                : GboardLanguageModelCatalog.values(family, choice).get(name);
        if (desired == null || desired.getClass() != original.getClass()) return original;
        return desired instanceof byte[] bytes ? bytes.clone() : desired;
    }
}
