package dev.jason.gboardpatches.patches.gboard.features.backuprestore

import dev.jason.gboardpatches.patches.gboard.shared.GboardFlagFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardFlagFamilyFeaturePatch

internal val gboardDataBackupFlagValuePatch = gboardFlagFamilyFeaturePatch(
    description = "Register every Gboard backup provider for the personal data backup.",
    feature = GboardFlagFamilyFeature.GBOARD_DATA_BACKUP,
)
