package dev.jason.gboardpatches.patches.gboard.features.languagemodels

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal data class LanguageModelWrapper(
    val target: GboardMethodTarget,
    val call: RuntimeCallId,
    val arguments: String,
    val before: Boolean = false,
) {
    val helperName get() = "morpheLanguageModelStock" + target.name.replaceFirstChar { it.uppercase() }
}

/** A fresh frame keeps p0 and arguments intact even when the stock method overwrites them. */
internal fun languageModelWrapperBody(spec: LanguageModelWrapper): String {
    val words = spec.target.parameterTypes.size + 1
    val stock = "invoke-direct/range {p0 .. p${words - 1}}, ${spec.target.referenceNamed(spec.helperName)}"
    val callback = emitLanguageModelRuntimeCall(spec.call, spec.arguments)
    val result = when (spec.target.returnType) {
        "V" -> ""
        "Z" -> "move-result v0"
        else -> "move-result-object v0"
    }
    val returns = when (spec.target.returnType) {
        "V" -> "return-void"
        "Z" -> "return v0"
        else -> "return-object v0"
    }
    val commit = if (spec.target.returnType.startsWith("L")) {
        "move-result-object v1\ncheck-cast v1, ${spec.target.returnType}\nmove-object v0, v1"
    } else ""
    val protectedCallback = """
        :language_models_callback_start
        $callback
        $commit
        :language_models_callback_end
        goto :language_models_callback_done
        :language_models_callback_failed
        move-exception v1
        :language_models_callback_done
        .catch Ljava/lang/Throwable; {:language_models_callback_start .. :language_models_callback_end} :language_models_callback_failed
    """.trimIndent()
    return if (spec.before) "$protectedCallback\n$stock\n$result\n$returns"
        else "$stock\n$result\n$protectedCallback\n$returns"
}

// Keep every emitted ABI explicit so the compiled-call inventory can verify ownership.
private fun emitLanguageModelRuntimeCall(call: RuntimeCallId, registers: String): String = when (call) {
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_RESTRICT_NWP_CACHED_PACK -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_RESTRICT_NWP_CACHED_PACK, registers)
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_HMM_ENROLL -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_HMM_ENROLL, registers)
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_LANGUAGE_MODEL_LOAD -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_LANGUAGE_MODEL_LOAD, registers)
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_LANGUAGE_MODEL_UNLOAD -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_LANGUAGE_MODEL_UNLOAD, registers)
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_ENGINE_ADDED -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_ENGINE_ADDED, registers)
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_ENGINE_REMOVED -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_ENGINE_REMOVED, registers)
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_BEFORE_DECODER_RESET -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_BEFORE_DECODER_RESET, registers)
    else -> error("Unexpected language model callback: $call")
}

private fun BytecodePatchContext.wrapLanguageModels(specs: List<LanguageModelWrapper>) {
    // Resolve every member, collision and ABI before changing any member of this group.
    val plans = specs.map { spec ->
        val method = spec.target.resolve(this)
        check(method.implementation != null)
        check(method.accessFlags and (AccessFlags.STATIC.value or AccessFlags.ABSTRACT.value or AccessFlags.NATIVE.value) == 0)
        check(method.parameterTypes.all { it != "J" && it != "D" })
        val owner = mutableClass(method.definingClass)
        check(owner.methods.none { it.name == spec.helperName }) { "Duplicate resource observation wrapper" }
        Triple(spec, method, languageModelWrapperBody(spec))
    }
    plans.forEach { (spec, method, body) ->
        val owner = mutableClass(method.definingClass)
        val helper = ImmutableMethod(method.definingClass, spec.helperName, method.parameters, method.returnType,
            AccessFlags.PRIVATE.value, method.annotations, method.hiddenApiRestrictions, method.implementation).toMutable()
        check(owner.methods.remove(method) && owner.methods.add(helper))
        owner.addHelperMethodIfMissing(method.name, method.parameterTypes.map { it.toString() }, method.returnType,
            method.accessFlags, method.parameterTypes.size + 3, body)
    }
}

internal val gboardLanguageModelCachePatch = bytecodePatch(description = "Restrict NWP cached packs to the selected resource version.") {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        wrapLanguageModels(listOf(LanguageModelWrapper(GboardLanguageModelTargets.languageModelsNwpOpen,
            RuntimeCallId.LANGUAGE_MODELS_RUNTIME_RESTRICT_NWP_CACHED_PACK, "p0, v0")))
    }
}

internal val gboardLanguageModelHmmPatch = bytecodePatch(description = "Observe successful HMM resource enrollment.") {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        wrapLanguageModels(listOf(LanguageModelWrapper(GboardLanguageModelTargets.languageModelsHmmEnroll,
            RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_HMM_ENROLL, "p0, p1, v0")))
    }
}

internal val gboardLanguageModelDecoderPatch = bytecodePatch(description = "Observe decoder resources and clear released observations.") {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        wrapLanguageModels(listOf(
            LanguageModelWrapper(GboardLanguageModelTargets.languageModelsDecoderLoad,
                RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_LANGUAGE_MODEL_LOAD, "p0, p1, v0"),
            LanguageModelWrapper(GboardLanguageModelTargets.languageModelsDecoderUnload,
                RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_LANGUAGE_MODEL_UNLOAD, "p0, p1, v0"),
            LanguageModelWrapper(GboardLanguageModelTargets.languageModelsDecoderAddEngine,
                RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_ENGINE_ADDED, "p0, p1"),
            LanguageModelWrapper(GboardLanguageModelTargets.languageModelsDecoderRemoveEngine,
                RuntimeCallId.LANGUAGE_MODELS_RUNTIME_AFTER_ENGINE_REMOVED, "p0, p1"),
            LanguageModelWrapper(GboardLanguageModelTargets.languageModelsDecoderReset,
                RuntimeCallId.LANGUAGE_MODELS_RUNTIME_BEFORE_DECODER_RESET, "p0", before = true),
        ))
    }
}
