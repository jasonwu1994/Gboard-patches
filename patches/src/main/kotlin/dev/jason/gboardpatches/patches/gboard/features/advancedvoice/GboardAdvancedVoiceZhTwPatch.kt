package dev.jason.gboardpatches.patches.gboard.features.advancedvoice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardAdvancedVoice1803ZhTwPatch = bytecodePatch(
    description = "1853精確的繁中標點、MDD與真實native payload檢查。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        val readiness = GboardMethodTarget("Lssi;", "a", emptyList(), "Z").resolve(this)
        val eligibility = GboardMethodTarget("Lssx;", "<init>", listOf(
            "Landroid/content/Context;", "Ltwj;", "Luym;", "Lsjt;", "Ltwj;", "Ltwj;", "Ltcy;",
            "Ltwj;", "Ltdf;", "Lsyl;", "Llez;", "Lsxs;", "Lsya;", "Lvec;", "Lszj;", "Ltdj;",
            "Ltfh;", "Lsiy;", "Lstm;", "Lssl;", "Ltwj;", "Laclu;", "Ljava/util/Set;"), "V").resolve(this)
        val mdd = GboardMethodTarget("Lsjt;", "<init>",
            listOf("Landroid/content/Context;", "Lsju;", "Lsjo;", "Laclu;"), "V").resolve(this)
        val formatter = GboardMethodTarget("Lsmo;", "<init>", listOf("Ljava/util/Locale;", "Letg;",
            "Lszc;", "I", "Z", "Lsmr;", "Ltey;", "Lvec;", "Ljava/util/concurrent/Executor;"), "V").resolve(this)
        listOf(GboardFieldTarget("Lssi;", "b", "Lwhq;"), GboardFieldTarget("Lsjt;", "d", "Laclu;"),
            GboardFieldTarget("Letg;", "c", "Leta;"), GboardFieldTarget("Leta;", "a", "Leta;"),
            GboardFieldTarget("Leta;", "H", "Z")).forEach { it.resolve(this) }
        listOf(GboardMethodTarget("Lwhq;", "get", emptyList(), "Ljava/lang/Object;"),
            GboardMethodTarget("Lsjs;", "<init>", listOf("Lsjt;", "Ljava/util/Locale;", "Lacfd;"), "V"),
            GboardMethodTarget("Lacbw;", "ak", listOf("Laclu;", "I", "Lachc;", "I"), "Lxqp;")
        ).forEach { it.resolve(this) }
        check("Lachc;" in mutableClass("Lsjs;").interfaces)
        mutableClass("Lssi;").addHelperMethodIfMissing("morpheNativeSplitReady", listOf("Z"), "Z", 0x1a, 3,
            advancedVoice1853NativeBody())
        mutableClass("Lsjt;").addHelperMethodIfMissing("morpheRequestZhTwMdd", emptyList(), "V", 0x12, 6,
            advancedVoice1853MddBody())
        mutableClass("Lsmo;").addHelperMethodIfMissing("morpheZhTwFormatter", listOf("Ljava/util/Locale;", "Letg;", "Z"),
            "Z", 0x1a, 5, advancedVoice1853FormatterBody())
        val normalReadinessReturns = readiness.implementation!!.instructions.indices.filter {
            readiness.implementation!!.instructions[it].isOpcode("RETURN")
        }
        check(normalReadinessReturns.size == 2)
        normalReadinessReturns.asReversed().forEach { i ->
            val r = (readiness.implementation!!.instructions[i] as OneRegisterInstruction).registerA
            readiness.addInstructions(i, """
                invoke-static {v$r}, Lssi;->morpheNativeSplitReady(Z)Z
                move-result v$r
            """)
        }
        eligibility.addInstructions(0, """
            ${RuntimeCallEmitter.invoke(RuntimeCallId.ADVANCED_VOICE_RUNTIME_SEED_APPLICATION_CONTEXT, "p1 .. p1")}
            ${RuntimeCallEmitter.invoke(RuntimeCallId.ADVANCED_VOICE_RUNTIME_INCLUDE_EXACT_ZH_TW_SUPPORTED_LOCALE, "p23 .. p23")}
            move-result-object p23
            check-cast p23, Ljava/util/Set;
        """)
        val mddReturns = mdd.implementation!!.instructions.indices.filter {
            mdd.implementation!!.instructions[it].isOpcode("RETURN_VOID")
        }
        mddReturns.asReversed().forEach { mdd.addInstructions(it, "invoke-direct {p0}, Lsjt;->morpheRequestZhTwMdd()V") }
        mdd.addInstructions(0, RuntimeCallEmitter.invoke(RuntimeCallId.ADVANCED_VOICE_RUNTIME_SEED_APPLICATION_CONTEXT, "p1 .. p1"))
        formatter.addInstructions(0, """
            invoke-static {p1, p2, p5}, Lsmo;->morpheZhTwFormatter(Ljava/util/Locale;Letg;Z)Z
            move-result p5
        """)
    }
}

internal fun advancedVoice1853NativeBody() = """
    :start
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADVANCED_VOICE_RUNTIME_SHOULD_CHECK_NATIVE, "p0")}
    move-result v0
    if-eqz v0, :done
    sget-object v0, Lssi;->b:Lwhq;
    invoke-interface {v0}, Lwhq;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Ljava/lang/Boolean;
    invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v0
    if-eqz v0, :done
    const/4 p0, 0x1
    :done
    return p0
    :end
    .catch Ljava/lang/Throwable; {:start .. :end} :failed
    :failed
    move-exception v0
    return p0
""".trimIndent()

internal fun advancedVoice1853MddBody() = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADVANCED_VOICE_RUNTIME_CLAIM_MDD, "")}
    move-result v0
    if-eqz v0, :done
    :start
    iget-object v0, p0, Lsjt;->d:Laclu;
    if-eqz v0, :reset
    const-string v1, "zh-TW"
    invoke-static {v1}, Ljava/util/Locale;->forLanguageTag(Ljava/lang/String;)Ljava/util/Locale;
    move-result-object v1
    new-instance v2, Lsjs;
    const/4 v3, 0x0
    invoke-direct {v2, p0, v1, v3}, Lsjs;-><init>(Lsjt;Ljava/util/Locale;Lacfd;)V
    const/4 v4, 0x3
    invoke-static {v0, v3, v2, v4}, Lacbw;->ak(Laclu;ILachc;I)Lxqp;
    move-result-object v0
    if-eqz v0, :reset
    :done
    return-void
    :end
    .catch Ljava/lang/Throwable; {:start .. :end} :failed
    :failed
    move-exception v0
    :reset
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADVANCED_VOICE_RUNTIME_RESET_MDD, "")}
    return-void
""".trimIndent()

internal fun advancedVoice1853FormatterBody() = """
    if-eqz p1, :stock
    iget-object v0, p1, Letg;->c:Leta;
    if-nez v0, :config
    sget-object v0, Leta;->a:Leta;
    :config
    if-eqz v0, :stock
    iget-boolean v0, v0, Leta;->H:Z
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADVANCED_VOICE_RUNTIME_FORMATTER_GATE, "p0, v0, p2")}
    move-result p2
    :stock
    return p2
""".trimIndent()
