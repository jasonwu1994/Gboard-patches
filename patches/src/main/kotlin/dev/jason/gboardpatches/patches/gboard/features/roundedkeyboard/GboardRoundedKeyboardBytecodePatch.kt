package dev.jason.gboardpatches.patches.gboard.features.roundedkeyboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.mutableFieldOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.returnInstructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val APPLY_RADII_HELPER = "jasondevApplyRoundedKeyboardRadii"
private const val RADII_REFERENCE_RADIUS_HELPER =
    "jasondevRoundedKeyboardReferenceRadius"
private const val WRAPPER_REFERENCE_RADIUS_HELPER =
    "jasondevRoundedKeyboardReferenceRadius"
private const val WRAPPER_COPY_WITH_RADII_HELPER =
    "jasondevRoundedKeyboardCopyWithRadii"

internal val gboardRoundedKeyboardBytecodePatch = bytecodePatch(
    description = "套用可設定的鍵盤面板圓角。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val admission = GboardVersionBindings.roundedKeyboardAdmission
        val styleResolver = GboardVersionBindings.roundedKeyboardStyleResolver
        check(admission.returnType == "Z" && admission.parameterTypes ==
            listOf("Landroid/content/Context;")) {
            "Rounded keyboard admission binding has an unexpected prototype"
        }
        check(styleResolver.returnType == "Ljava/util/List;" &&
            styleResolver.parameterTypes ==
            listOf("Ljava/lang/String;", "Ljava/util/List;")) {
            "Rounded keyboard style binding has an unexpected prototype"
        }

        mutableFieldOrThrow(GboardVersionBindings.roundedKeyboardRadiiValuesField)
        mutableFieldOrThrow(GboardVersionBindings.roundedKeyboardRadiiWrapperRadiiField)
        mutableFieldOrThrow(GboardVersionBindings.roundedKeyboardRadiiWrapperFirstParameterField)
        mutableFieldOrThrow(GboardVersionBindings.roundedKeyboardRadiiWrapperSecondParameterField)
        GboardVersionBindings.roundedKeyboardRadiiConstructor.resolve(this)
        GboardVersionBindings.roundedKeyboardRadiiWrapperSimpleConstructor.resolve(this)
        GboardVersionBindings.roundedKeyboardRadiiWrapperParameterizedConstructor.resolve(this)

        val radiiType = GboardVersionBindings.roundedKeyboardRadiiType.descriptor
        val wrapperType = GboardVersionBindings.roundedKeyboardRadiiWrapperType.descriptor
        mutableClass(radiiType).addHelperMethodIfMissing(
            name = RADII_REFERENCE_RADIUS_HELPER,
            parameterTypes = emptyList(),
            returnType = "F",
            accessFlags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            registerCount = 7,
            body = roundedKeyboardRadiiReferenceRadiusHelperBody(),
        )
        mutableClass(wrapperType).addHelperMethodIfMissing(
            name = WRAPPER_REFERENCE_RADIUS_HELPER,
            parameterTypes = emptyList(),
            returnType = "F",
            accessFlags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            registerCount = 2,
            body = roundedKeyboardWrapperReferenceRadiusHelperBody(),
        )
        mutableClass(wrapperType).addHelperMethodIfMissing(
            name = WRAPPER_COPY_WITH_RADII_HELPER,
            parameterTypes = listOf(radiiType),
            returnType = wrapperType,
            accessFlags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            registerCount = 6,
            body = roundedKeyboardWrapperCopyWithRadiiHelperBody(),
        )

        findMutableMethodOrThrow(admission).injectRoundedKeyboardAdmission()
        val styleMethod = findMutableMethodOrThrow(styleResolver)
        styleMethod.definingClass.let { owner ->
            check(owner == GboardVersionBindings.roundedKeyboardStyleResolver.ownerDescriptor)
            mutableClass(owner).addHelperMethodIfMissing(
                name = APPLY_RADII_HELPER,
                parameterTypes = listOf("[F", "Ljava/util/List;"),
                returnType = "Ljava/util/List;",
                accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                registerCount = 15,
                body = roundedKeyboardHelperBody(),
            )
        }
        styleMethod.injectRoundedKeyboardStyle()
    }
}

internal fun MutableMethod.injectRoundedKeyboardAdmission() {
    val abi = RuntimeAbiCatalog.abi(
        RuntimeCallId.ROUNDED_KEYBOARD_RUNTIME_APPLY_THEME_ADMISSION,
    )
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 2) { "Duplicate rounded keyboard admission delegates" }
        return
    }
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN") }
    check(returns.size == 2) {
        "Expected two rounded keyboard admission returns, found ${returns.size}"
    }
    returns.sortedDescending().forEach { index ->
        val register = (instructions[index] as OneRegisterInstruction).registerA
        addInstructions(
            index,
            """
                ${RuntimeCallEmitter.invoke(
                    RuntimeCallId.ROUNDED_KEYBOARD_RUNTIME_APPLY_THEME_ADMISSION,
                    "v$register",
                )}

                move-result v$register
            """.trimIndent(),
        )
    }
}

internal fun MutableMethod.injectRoundedKeyboardStyle() {
    val runtimeAbi = RuntimeAbiCatalog.abi(
        RuntimeCallId.ROUNDED_KEYBOARD_RUNTIME_RESOLVE_EFFECTIVE_RADII_DP,
    )
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(runtimeAbi.reference) }
    if (existing > 0) {
        check(existing == 2) { "Duplicate rounded keyboard style delegates" }
        return
    }
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_OBJECT") }
    check(returns.size == 2) {
        "Expected two rounded keyboard style returns, found ${returns.size}"
    }
    returns.sortedDescending().forEach { index ->
        val resultRegister = (instructions[index] as OneRegisterInstruction).registerA
        addInstructions(
            index,
            """
                ${RuntimeCallEmitter.invoke(
                    RuntimeCallId.ROUNDED_KEYBOARD_RUNTIME_RESOLVE_EFFECTIVE_RADII_DP,
                    "p1 .. p1",
                )}

                move-result-object v0

                invoke-static {v0, v$resultRegister}, ${definingClass}->${APPLY_RADII_HELPER}([FLjava/util/List;)Ljava/util/List;

                move-result-object v$resultRegister
            """.trimIndent(),
        )
    }
}

private fun roundedKeyboardHelperBody(): String {
    val radiiType = GboardVersionBindings.roundedKeyboardRadiiType.descriptor
    val radiiConstructor = GboardVersionBindings.roundedKeyboardRadiiConstructor.reference
    val wrapperType = GboardVersionBindings.roundedKeyboardRadiiWrapperType.descriptor
    return """
        if-eqz p0, :return_stock
        if-eqz p1, :return_stock

        array-length v0, p0
        const/4 v1, 0x4
        if-ne v0, v1, :return_stock

        invoke-interface {p1}, Ljava/util/List;->size()I
        move-result v1
        const/4 v0, 0x0
        const/4 v2, -0x1
        const/4 v3, 0x0

        :scan_wrapper
        if-ge v0, v1, :wrapper_scanned
        invoke-interface {p1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;
        move-result-object v4
        instance-of v5, v4, $wrapperType
        if-eqz v5, :scan_next
        if-gez v2, :return_stock
        move v2, v0
        move-object v3, v4

        :scan_next
        add-int/lit8 v0, v0, 0x1
        goto :scan_wrapper

        :wrapper_scanned
        if-ltz v2, :return_stock
        invoke-virtual {v3}, $wrapperType->$WRAPPER_REFERENCE_RADIUS_HELPER()F
        move-result v5
        const/4 v6, 0x0
        cmpl-float v0, v5, v6
        if-lez v0, :return_stock
        const/high16 v6, 0x42000000
        div-float v5, v5, v6

        const/4 v6, 0x0
        aget v9, p0, v6
        mul-float v9, v9, v5
        const/4 v6, 0x1
        aget v10, p0, v6
        mul-float v10, v10, v5
        const/4 v6, 0x2
        aget v11, p0, v6
        mul-float v11, v11, v5
        const/4 v6, 0x3
        aget v12, p0, v6
        mul-float v12, v12, v5
        new-instance v8, $radiiType
        invoke-direct/range {v8 .. v12}, $radiiConstructor

        invoke-virtual {v3, v8}, $wrapperType->$WRAPPER_COPY_WITH_RADII_HELPER($radiiType)$wrapperType
        move-result-object v11
        if-eqz v11, :return_stock

        :copy_list
        new-instance v12, Ljava/util/ArrayList;
        invoke-direct {v12, p1}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
        invoke-virtual {v12, v2, v11}, Ljava/util/ArrayList;->set(ILjava/lang/Object;)Ljava/lang/Object;
        return-object v12

        :return_stock
        return-object p1
    """.trimIndent()
}

private fun roundedKeyboardRadiiReferenceRadiusHelperBody(): String {
    val radiiValues = GboardVersionBindings.roundedKeyboardRadiiValuesField.reference
    return """
        iget-object v0, p0, $radiiValues
        if-eqz v0, :return_zero
        array-length v1, v0
        if-lez v1, :return_zero
        const/4 v2, 0x0
        const/4 v3, 0x0

        :scan_radii
        if-ge v2, v1, :radii_scanned
        aget v4, v0, v2
        invoke-static {v4}, Ljava/lang/Float;->isNaN(F)Z
        move-result v5
        if-nez v5, :return_zero
        invoke-static {v4}, Ljava/lang/Float;->isInfinite(F)Z
        move-result v5
        if-nez v5, :return_zero
        cmpl-float v5, v4, v3
        if-lez v5, :scan_next
        move v3, v4

        :scan_next
        add-int/lit8 v2, v2, 0x1
        goto :scan_radii

        :radii_scanned
        return v3

        :return_zero
        const/4 v0, 0x0
        return v0
    """.trimIndent()
}

private fun roundedKeyboardWrapperReferenceRadiusHelperBody(): String {
    val radiiType = GboardVersionBindings.roundedKeyboardRadiiType.descriptor
    val wrapperRadii = GboardVersionBindings.roundedKeyboardRadiiWrapperRadiiField.reference
    return """
        iget-object v0, p0, $wrapperRadii
        if-eqz v0, :return_zero
        invoke-virtual {v0}, $radiiType->$RADII_REFERENCE_RADIUS_HELPER()F
        move-result v0
        return v0

        :return_zero
        const/4 v0, 0x0
        return v0
    """.trimIndent()
}

private fun roundedKeyboardWrapperCopyWithRadiiHelperBody(): String {
    val wrapperType = GboardVersionBindings.roundedKeyboardRadiiWrapperType.descriptor
    val firstParameter =
        GboardVersionBindings.roundedKeyboardRadiiWrapperFirstParameterField.reference
    val secondParameter =
        GboardVersionBindings.roundedKeyboardRadiiWrapperSecondParameterField.reference
    val simpleConstructor =
        GboardVersionBindings.roundedKeyboardRadiiWrapperSimpleConstructor.reference
    val parameterizedConstructor =
        GboardVersionBindings.roundedKeyboardRadiiWrapperParameterizedConstructor.reference
    return """
        if-eqz p1, :return_null
        iget-object v0, p0, $firstParameter
        iget-object v1, p0, $secondParameter
        if-nez v0, :first_parameter_present
        if-nez v1, :return_null
        new-instance v2, $wrapperType
        invoke-direct {v2, p1}, $simpleConstructor
        return-object v2

        :first_parameter_present
        if-eqz v1, :return_null
        invoke-virtual {v0}, Ljava/lang/Float;->floatValue()F
        move-result v2
        invoke-virtual {v1}, Ljava/lang/Float;->floatValue()F
        move-result v3
        new-instance v0, $wrapperType
        invoke-direct {v0, p1, v2, v3}, $parameterizedConstructor
        return-object v0

        :return_null
        const/4 v0, 0x0
        return-object v0
    """.trimIndent()
}
