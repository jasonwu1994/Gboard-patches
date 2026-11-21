package dev.jason.gboardpatches.patches.gboard.features.rambler

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardStructuralFingerprint
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal const val RAMBLER_AUTO_STOP_CALLBACK = "Lkrm;"
internal const val RAMBLER_AUTO_STOP_MANAGER = "Lkrn;"
internal const val RAMBLER_AUTO_STOP_TIMER = "Lktd;"
private const val DURATION = "Lj\$/time/Duration;"
private const val CONTEXT = "Landroid/content/Context;"
private const val START_HELPER = "morpheRamblerAutoStopStart"
private const val LEVEL_HELPER = "morpheRamblerAutoStopLevel"

// The callback's onStartSuccess and speech level bodies, and the timer's length setter.
internal const val RAMBLER_AUTO_STOP_START_FINGERPRINT =
    "740d4af7ea46eed2716082cef05bfb8fabbad2b1350c758b64623dbe453a0d7f"
internal const val RAMBLER_AUTO_STOP_LEVEL_FINGERPRINT =
    "1ceca524303fd73583877abf98b9218439f8c1dfe73d38eacb6cc009e5e694c3"
internal const val RAMBLER_AUTO_STOP_SETTER_FINGERPRINT =
    "a390acf3c34ffdbc7b3511712d010b9c0f232748a7d00167a4c6133f76b112c8"

/**
 * Stops Rambler voice typing after the chosen stretch without speech. The voice callback hands
 * the session start and every speech level to the runtime before Gboard's own code runs; the
 * runtime decides whether the timeout timer gets a new length or a restart.
 */
internal val gboardRamblerAutoStopPatch = bytecodePatch(
    description = "Rambler 停止說話後依設定秒數自動結束。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        installRamblerAutoStop()
    }
}

internal fun BytecodePatchContext.installRamblerAutoStop() {
    check(GboardVersionBindings.targetVersion == "18.5.4")
    listOf(
        GboardFieldTarget(RAMBLER_AUTO_STOP_MANAGER, "D", "Z"),
        GboardFieldTarget(RAMBLER_AUTO_STOP_MANAGER, "l", "Lktd;"),
        GboardFieldTarget(RAMBLER_AUTO_STOP_MANAGER, "f", CONTEXT),
    ).forEach { target ->
        val field = target.resolve(this)
        check(field.accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.STATIC.value) == 0) {
            "Inaccessible Rambler field: ${target.reference}"
        }
    }
    installRamblerAutoStop(mutableClass(RAMBLER_AUTO_STOP_CALLBACK), mutableClass(RAMBLER_AUTO_STOP_TIMER))
}

internal fun installRamblerAutoStop(callback: MutableClass, timer: MutableClass) {
    check(callback.type == RAMBLER_AUTO_STOP_CALLBACK && timer.type == RAMBLER_AUTO_STOP_TIMER)
    val start = GboardMethodTarget(RAMBLER_AUTO_STOP_CALLBACK, "g", emptyList(), "V").resolve(callback)
    val level = GboardMethodTarget(RAMBLER_AUTO_STOP_CALLBACK, "e", listOf("I"), "V").resolve(callback)
    val setter = GboardMethodTarget(RAMBLER_AUTO_STOP_TIMER, "d", listOf(DURATION), "V").resolve(timer)
    val restart = GboardMethodTarget(RAMBLER_AUTO_STOP_TIMER, "a", emptyList(), "V").resolve(timer)
    val manager = GboardFieldTarget(RAMBLER_AUTO_STOP_CALLBACK, "a", "Lkrn;").resolve(callback)
    val length = GboardFieldTarget(RAMBLER_AUTO_STOP_TIMER, "f", DURATION).resolve(timer)
    check(manager.accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.STATIC.value) == 0) {
        "Inaccessible Rambler manager field"
    }
    check(length.accessFlags and AccessFlags.STATIC.value == 0) { "Unexpected Rambler timer length field" }
    check(callback.fields.count { it.accessFlags and AccessFlags.STATIC.value == 0 } == 1) {
        "Unexpected Rambler callback fields"
    }
    listOf(start, level, setter, restart).forEach { method ->
        check(method.accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.STATIC.value) == 0) {
            "Unexpected Rambler method access: ${method.definingClass}->${method.name}"
        }
    }
    check(start.gboardStructuralFingerprint() == RAMBLER_AUTO_STOP_START_FINGERPRINT) {
        "Unexpected 18.5.4 Rambler start shape: ${start.gboardStructuralFingerprint()}"
    }
    check(level.gboardStructuralFingerprint() == RAMBLER_AUTO_STOP_LEVEL_FINGERPRINT) {
        "Unexpected 18.5.4 Rambler speech level shape: ${level.gboardStructuralFingerprint()}"
    }
    check(setter.gboardStructuralFingerprint() == RAMBLER_AUTO_STOP_SETTER_FINGERPRINT) {
        "Unexpected 18.5.4 Rambler timer setter shape: ${setter.gboardStructuralFingerprint()}"
    }
    check(callback.methods.none { it.name == START_HELPER || it.name == LEVEL_HELPER } &&
        timer.methods.none { it.name == START_HELPER }) { "Rambler auto stop is already installed" }

    timer.addHelperMethodIfMissing(START_HELPER, listOf(CONTEXT, "Z"), "V",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, 5, ramblerAutoStopTimerStartBody())
    callback.addHelperMethodIfMissing(START_HELPER, emptyList(), "V",
        AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, 4, ramblerAutoStopCallbackStartBody())
    callback.addHelperMethodIfMissing(LEVEL_HELPER, listOf("I"), "V",
        AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, 4, ramblerAutoStopCallbackLevelBody())
    // Index 0 is not a branch target in either body; Gboard's code runs unchanged afterwards.
    start.addInstructions(0, "invoke-direct {p0}, $RAMBLER_AUTO_STOP_CALLBACK->$START_HELPER()V")
    level.addInstructions(0, "invoke-direct {p0, p1}, $RAMBLER_AUTO_STOP_CALLBACK->$LEVEL_HELPER(I)V")
}

/** Timer side of the session start: the length field is private, so the timer reads it. */
internal fun ramblerAutoStopTimerStartBody() = """
    :start
    iget-object v0, p0, $RAMBLER_AUTO_STOP_TIMER->f:$DURATION
    ${RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_AUTO_STOP_RUNTIME_ON_SESSION_START, "p1, p2, v0")}
    move-result-object v0
    if-eqz v0, :done
    instance-of v1, v0, $DURATION
    if-eqz v1, :done
    check-cast v0, $DURATION
    invoke-virtual {p0, v0}, $RAMBLER_AUTO_STOP_TIMER->d($DURATION)V
    :done
    return-void
    :end
    .catch Ljava/lang/Throwable; {:start .. :end} :failed
    :failed
    move-exception v0
    return-void
""".trimIndent()

internal fun ramblerAutoStopCallbackStartBody() = """
    :start
    iget-object v0, p0, $RAMBLER_AUTO_STOP_CALLBACK->a:$RAMBLER_AUTO_STOP_MANAGER
    if-eqz v0, :done
    iget-object v1, v0, $RAMBLER_AUTO_STOP_MANAGER->l:$RAMBLER_AUTO_STOP_TIMER
    if-eqz v1, :done
    iget-object v2, v0, $RAMBLER_AUTO_STOP_MANAGER->f:$CONTEXT
    iget-boolean v0, v0, $RAMBLER_AUTO_STOP_MANAGER->D:Z
    invoke-virtual {v1, v2, v0}, $RAMBLER_AUTO_STOP_TIMER->$START_HELPER(${CONTEXT}Z)V
    :done
    return-void
    :end
    .catch Ljava/lang/Throwable; {:start .. :end} :failed
    :failed
    move-exception v0
    return-void
""".trimIndent()

internal fun ramblerAutoStopCallbackLevelBody() = """
    :start
    iget-object v0, p0, $RAMBLER_AUTO_STOP_CALLBACK->a:$RAMBLER_AUTO_STOP_MANAGER
    if-eqz v0, :done
    iget-boolean v1, v0, $RAMBLER_AUTO_STOP_MANAGER->D:Z
    ${RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_AUTO_STOP_RUNTIME_ON_SPEECH_LEVEL, "v1, p1")}
    move-result v1
    if-eqz v1, :done
    iget-object v0, v0, $RAMBLER_AUTO_STOP_MANAGER->l:$RAMBLER_AUTO_STOP_TIMER
    if-eqz v0, :done
    invoke-virtual {v0}, $RAMBLER_AUTO_STOP_TIMER->a()V
    :done
    return-void
    :end
    .catch Ljava/lang/Throwable; {:start .. :end} :failed
    :failed
    move-exception v0
    return-void
""".trimIndent()
