package dev.jason.gboardpatches.patches.gboard.features.splitkeyboard

import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings

internal object GboardSplitKeyboardTargets {
    const val ORDER_RETURN_TYPE = "Lwns;"

    val splitEligibility get() = GboardVersionBindings.splitEligibility
    val splitDeviceEligibility get() = GboardVersionBindings.splitDeviceEligibility
    val splitWidthEligibility get() = GboardVersionBindings.splitWidthEligibility

    val accessPointName = GboardFieldTarget(
        classType = "Ltfm;",
        name = "d",
        type = "Ljava/lang/Object;",
    )
    val accessPointState = GboardMethodTarget(
        classType = "Ltfm;",
        name = "e",
        parameterTypes = listOf("I"),
        returnType = "V",
    )
    val accessPointOrder = GboardMethodTarget(
        classType = "Lmwg;",
        name = "c",
        parameterTypes = emptyList(),
        returnType = ORDER_RETURN_TYPE,
    )
    val modeAttach = GboardMethodTarget(
        classType = "Lpqw;",
        name = "E",
        parameterTypes = listOf("Lppu;", "Lpev;", "Lpjz;", "Z"),
        returnType = "V",
    )
    val modeEnvironmentReload = GboardMethodTarget(
        classType = "Lpqw;",
        name = "Q",
        parameterTypes = emptyList(),
        returnType = "V",
    )
    val splitAction = GboardMethodTarget(
        classType = "Lpqw;",
        name = "W",
        parameterTypes = emptyList(),
        returnType = "V",
    )
}
