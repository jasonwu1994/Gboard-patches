package dev.jason.gboardpatches.patches.gboard.shared.accesspoint

import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget

internal object GboardAccessPointContributionsTargets {
    val orderFactory = GboardMethodTarget(
        classType = "Lmvg;",
        name = "m",
        parameterTypes = listOf(
            "Landroid/content/Context;",
            "Lniv;",
            "Lnyt;",
            "Lwpa;",
            "Lwpa;",
        ),
        returnType = "Lmvg;",
    )

    val controllerConstructor = GboardMethodTarget(
        classType = "Lmwv;",
        name = "<init>",
        parameterTypes = listOf("Landroid/content/Context;", "Lmyc;"),
        returnType = "V",
    )
}
