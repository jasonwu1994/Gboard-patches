package dev.jason.gboardpatches.patches.gboard.features.ocr

import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags

internal object GboardOcrPatchTargets {
    val taskCompletionListener = GboardMethodTarget("Lmek;", "l", listOf("Lmed;"), "V")

    internal fun requireTaskCompletionRegistration(owner: MutableClass): MutableMethod {
        val method = taskCompletionListener.resolve(owner)
        check(owner.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.ABSTRACT.value))
        check(method.accessFlags == AccessFlags.PUBLIC.value) { "Expected public instance task registration" }
        return method
    }

    fun preflight(context: BytecodePatchContext) {
        val methods = listOf(configLabel, languageHint, loggingLibrary, optionalModuleName, moduleId,
            moduleAvailability, optionalModuleBuilder, moduleManagerRequest, thinRecognizerInit) + listOf(
            GboardMethodTarget("Llfy;", "<init>", listOf("Ljava/lang/String;", "I", "J", "Z"), "V"),
            GboardMethodTarget("Lzlw;", "b", emptyList(), "Lzlw;"),
            GboardMethodTarget("Lzlw;", "a", emptyList(), "Landroid/content/Context;"),
            GboardMethodTarget("Llpv;", "a", listOf("Landroid/content/Context;", "Ljava/lang/String;"), "I"),
            GboardMethodTarget("Llpv;", "d", listOf("Landroid/content/Context;", "Llpu;", "Ljava/lang/String;"), "Llpv;"),
            GboardMethodTarget("Llpv;", "c", listOf("Ljava/lang/String;"), "Landroid/os/IBinder;"),
            GboardMethodTarget("Llpi;", "<init>", listOf("Ljava/lang/Object;"), "V"),
            GboardMethodTarget("Laaic;", "<init>", listOf("Landroid/os/IBinder;"), "V"),
            GboardMethodTarget("Laaib;", "<init>", listOf("Landroid/os/IBinder;"), "V"),
            GboardMethodTarget("Lemt;", "a", emptyList(), "Landroid/os/Parcel;"),
            GboardMethodTarget("Lemt;", "z", listOf("I", "Landroid/os/Parcel;"), "Landroid/os/Parcel;"),
            GboardMethodTarget("Lemt;", "C", listOf("I", "Landroid/os/Parcel;"), "V"),
            GboardMethodTarget("Lemv;", "d", listOf("Landroid/os/Parcel;", "Landroid/os/IInterface;"), "V"),
            GboardMethodTarget("Lemv;", "c", listOf("Landroid/os/Parcel;", "Landroid/os/Parcelable;"), "V"),
            GboardMethodTarget("Laaih;", "<init>", listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Z", "I", "Ljava/lang/String;", "Z"), "V"),
            GboardMethodTarget("Laaim;", "b", emptyList(), "V"),
            GboardMethodTarget("Lzmb;", "<init>", listOf("[Llfy;"), "V"),
            GboardMethodTarget("Llnn;", "<init>", listOf("Landroid/content/Context;"), "V"),
            GboardMethodTarget("Llnn;", "a", listOf("[Llhq;"), "Lmek;"),
            GboardMethodTarget("Llnn;", "b", listOf("Llmw;"), "V"),
            GboardMethodTarget("Llnn;", "c", listOf("Lsrk;"), "Lmek;"),
            GboardMethodTarget("Lsrk;", "<init>", listOf("Ljava/util/List;", "Llmw;"), "V"),
            GboardMethodTarget("Llmw;", "a", listOf("Llnb;"), "V"),
            GboardMethodTarget("Lmed;", "a", listOf("Lmek;"), "V"),
            GboardMethodTarget("Lmek;", "g", emptyList(), "Z"),
            GboardMethodTarget("Lmek;", "e", emptyList(), "Ljava/lang/Object;"),
            GboardMethodTarget("Lmek;", "d", emptyList(), "Ljava/lang/Exception;"),
        )
        methods.forEach { it.resolve(context) }
        requireTaskCompletionRegistration(context.mutableClass("Lmek;"))
        listOf(
            GboardFieldTarget("Laaim;", "a", "Landroid/content/Context;"),
            GboardFieldTarget("Laaim;", "b", "Laahz;"),
            GboardFieldTarget("Laaim;", "f", "Laaib;"),
            GboardFieldTarget("Llpv;", "b", "Llpu;"),
            GboardFieldTarget("Llfy;", "a", "Ljava/lang/String;"),
            GboardFieldTarget("Llnb;", "b", "I"), GboardFieldTarget("Llnb;", "e", "I"),
            GboardFieldTarget("Llna;", "a", "I"), GboardFieldTarget("Llna;", "b", "Z"),
        ).forEach { it.resolve(context) }
        check(context.mutableClass("Laaic;").superclass == "Lemt;")
        check(context.mutableClass("Laaib;").superclass == "Lemt;")
        check("Laahz;" in context.mutableClass("Laaiy;").interfaces)
        check("Llhq;" in context.mutableClass("Lzmb;").interfaces)
    }
    val configLabel = method("b", "Ljava/lang/String;")
    val languageHint = method("c", "Ljava/lang/String;")
    val loggingLibrary = method("d", "Ljava/lang/String;")
    val optionalModuleName = method("e", "Ljava/lang/String;")
    val moduleId = method("f", "Ljava/lang/String;")
    val moduleAvailability = method("g", "Z")

    val optionalModuleBuilder = GboardMethodTarget(
        classType = "Lzlp;",
        name = "a",
        parameterTypes = listOf("Laahz;"),
        returnType = "[Llfy;",
    )
    val moduleManagerRequest = GboardMethodTarget(
        classType = "Laait;",
        name = "a",
        parameterTypes = emptyList(),
        returnType = "[Llfy;",
    )
    val thinRecognizerInit = GboardMethodTarget(
        classType = "Laaim;",
        name = "a",
        parameterTypes = emptyList(),
        returnType = "V",
    )

    private fun method(name: String, returnType: String) = GboardMethodTarget(
        classType = "Laaiy;",
        name = name,
        parameterTypes = emptyList(),
        returnType = returnType,
    )
}
