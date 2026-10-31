package dev.jason.gboardpatches.patches.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission

internal object Constants {
    const val GBOARD_PACKAGE_NAME = "com.google.android.inputmethod.latin"
    const val GBOARD_PATCHED_PACKAGE_NAME = "com.touchtype.swiftkey"
    const val GBOARD_PATCH_AUTHOR = "jasonwu1994"
    const val GBOARD_PATCH_AUTHOR_URL = "https://github.com/jasonwu1994"
    const val GBOARD_PATCH_REPOSITORY_URL = "https://github.com/jasonwu1994/Gboard-patches"
    val GBOARD_PATCH_VERSION = PatchBuildInfo.VERSION

    val COMPATIBILITY_GBOARD = Compatibility(
        name = "Gboard",
        packageName = GboardTargetAdmission.packageName,
        apkFileType = GboardTargetAdmission.apkFileType,
        appIconColor = 0x1A73E8,
        signatures = GboardTargetAdmission.signatures,
        targets = GboardTargetAdmission.versionNames.map { versionName ->
            AppTarget(
                version = versionName,
                isExperimental = false,
            )
        },
    )
}
