package dev.jason.gboardpatches.extension.settings;

import java.util.ArrayList;
import java.util.List;

final class GboardPatchesSettingsScrollState {
    private int rootScrollY;
    private final List<Integer> featureScrollBackStack = new ArrayList<Integer>();

    void enterFeature(boolean fromRoot, int currentScrollY) {
        int normalizedScrollY = Math.max(0, currentScrollY);
        if (fromRoot) {
            rootScrollY = normalizedScrollY;
            featureScrollBackStack.clear();
        } else {
            featureScrollBackStack.add(Integer.valueOf(normalizedScrollY));
        }
    }

    int leaveFeature(boolean returningToRoot) {
        if (returningToRoot) {
            int restoredScrollY = rootScrollY;
            rootScrollY = 0;
            featureScrollBackStack.clear();
            return restoredScrollY;
        }
        if (featureScrollBackStack.isEmpty()) {
            return 0;
        }
        return featureScrollBackStack.remove(featureScrollBackStack.size() - 1).intValue();
    }

    void resetForDirectPath(int featureAncestorCount) {
        rootScrollY = 0;
        featureScrollBackStack.clear();
        for (int index = 0; index < featureAncestorCount; index++) {
            featureScrollBackStack.add(Integer.valueOf(0));
        }
    }
}
