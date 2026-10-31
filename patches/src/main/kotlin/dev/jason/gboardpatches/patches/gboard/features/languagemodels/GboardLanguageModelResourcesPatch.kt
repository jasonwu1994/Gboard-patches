package dev.jason.gboardpatches.patches.gboard.features.languagemodels

import app.morphe.patcher.patch.resourcePatch
import com.google.gson.Gson
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal fun languageModelRuntimeBindings(): Map<String, String> = mapOf(
    "flag_name_field" to GboardVersionBindings.flagNameField.reference,
    "language_models_baked_getter" to GboardLanguageModelTargets.languageModelsBakedGetter.reference,
    "language_models_hmm_enroll" to GboardLanguageModelTargets.languageModelsHmmEnroll.reference,
    "language_models_hmm_pack" to GboardLanguageModelTargets.languageModelsHmmPack.reference,
    "language_models_hmm_provider" to GboardLanguageModelTargets.languageModelsHmmProvider.reference,
    "language_models_hmm_version" to GboardLanguageModelTargets.languageModelsHmmVersion.reference,
    "language_models_decoder_load" to GboardLanguageModelTargets.languageModelsDecoderLoad.reference,
    "language_models_decoder_unload" to GboardLanguageModelTargets.languageModelsDecoderUnload.reference,
    "language_models_decoder_add_engine" to GboardLanguageModelTargets.languageModelsDecoderAddEngine.reference,
    "language_models_decoder_remove_engine" to GboardLanguageModelTargets.languageModelsDecoderRemoveEngine.reference,
    "language_models_decoder_reset" to GboardLanguageModelTargets.languageModelsDecoderReset.reference,
    "language_models_decoder_dump" to GboardLanguageModelTargets.languageModelsDecoderDump.reference,
    "language_models_descriptor" to GboardLanguageModelTargets.languageModelsDescriptor.reference,
    "language_models_model_type" to GboardLanguageModelTargets.languageModelsModelType.reference,
    "language_models_model_language" to GboardLanguageModelTargets.languageModelsModelLanguage.reference,
    "language_models_model_country" to GboardLanguageModelTargets.languageModelsModelCountry.reference,
    "language_models_model_file" to GboardLanguageModelTargets.languageModelsModelFile.reference,
    "language_models_model_version" to GboardLanguageModelTargets.languageModelsModelVersion.reference,
    "language_models_engine_type" to GboardLanguageModelTargets.languageModelsEngineType.reference,
    "language_models_engine_models" to GboardLanguageModelTargets.languageModelsEngineModels.reference,
    "language_models_engine_model_file" to GboardLanguageModelTargets.languageModelsEngineModelFile.reference,
    "language_models_engine_model_version" to GboardLanguageModelTargets.languageModelsEngineModelVersion.reference,
    "language_models_nwp_open" to GboardLanguageModelTargets.languageModelsNwpOpen.reference,
    "language_models_nwp_manager_factory" to GboardLanguageModelTargets.languageModelsNwpManagerFactory.reference,
    "language_models_pack_metadata" to GboardLanguageModelTargets.languageModelsPackMetadata.reference,
    "language_models_pack_parent" to GboardLanguageModelTargets.languageModelsPackParent.reference,
    "language_models_parent_version" to GboardLanguageModelTargets.languageModelsParentVersion.reference,
)

internal val gboardLanguageModelResourcesPatch = resourcePatch(
    description = "Copy dictionary catalogs and reviewed runtime member identities.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    finalize {
        listOf("delight-versions.json", "hmm-versions.json").forEach { name ->
            val bytes = object {}.javaClass.classLoader.getResourceAsStream("language-models/$name")
                ?.use { it.readBytes() } ?: error("Missing dictionary catalog: $name")
            this["assets/language-models/$name", false].apply {
                parentFile.mkdirs()
                writeBytes(bytes)
            }
        }
        this["assets/language-models/target-bindings.json", false].apply {
            parentFile.mkdirs()
            writeText(Gson().toJson(languageModelRuntimeBindings()), Charsets.UTF_8)
        }
    }
}
