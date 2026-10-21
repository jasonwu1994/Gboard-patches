package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Assume.assumeNotNull
import java.io.File
import java.nio.file.Files
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter

class GboardZhuyinShortcutImportPatchTest {
    private val stock = "Lggy;->av(Ljava/lang/String;)[Ljava/lang/String;"
    private val patched = "Ldev/jason/gboardpatches/extension/zhuyindictionary/" +
        "GboardZhuyinDictionaryRuntime;->tokenizeShortcut(Ljava/lang/String;)[Ljava/lang/String;"

    @Test
    fun `validates converter target in supplied device APK`() {
        val apk = System.getenv("GBOARD_DICTIONARY_VALIDATION_APK")
        assumeNotNull(apk)
        val container = DexFileFactory.loadDexContainer(File(apk!!), Opcodes.getDefault())
        val owner = container.dexEntryNames.asSequence().flatMap { name ->
            container.getEntry(name)!!.dexFile.classes.asSequence()
        }.single { it.type == "Lfyx;" }
        val method = owner.methods.single { it.name == "a" }.toMutable()
        method.applyZhuyinShortcutTokenizer()
        method.applyZhuyinShortcutTokenizer()
        assertEquals(1, method.implementation!!.instructions.count { it.isMethodReference(patched) })
        assertEquals(1, method.implementation!!.instructions.count { it.isMethodReference(stock) })
    }

    @Test
    fun `candidate bridge hooks real APK and survives dex round trip`() {
        val apk = System.getenv("GBOARD_DICTIONARY_VALIDATION_APK")
        assumeNotNull(apk)
        val container = DexFileFactory.loadDexContainer(File(apk!!), Opcodes.getDefault())
        val types = setOf("Lfyx;", "Lhch;", HMM_PROCESSOR, ZHUYIN_PROCESSOR)
        val classes = container.dexEntryNames.asSequence().flatMap { name ->
            container.getEntry(name)!!.dexFile.classes.asSequence()
        }.filter { it.type in types }.associate { it.type to MutableClass(it) }
        assertEquals(4, classes.size)
        repeat(2) {
            classes.getValue("Lfyx;").methods.single { it.name == "a" }.captureShortcutEntries()
            classes.getValue("Lhch;").methods.single { it.name == "g" }
                .hookShortcutImport(RuntimeCallId.ZHUYIN_SHORTCUTS_BEGIN_IMPORT, false)
            classes.getValue("Lhch;").methods.single { it.name == "d" }
                .hookShortcutRow(RuntimeCallId.ZHUYIN_SHORTCUTS_RECORD_ROW)
            classes.getValue("Lhch;").methods.single { it.name == "c" }
                .hookShortcutImport(RuntimeCallId.ZHUYIN_SHORTCUTS_FINISH_IMPORT, true)
            classes.getValue(HMM_PROCESSOR).methods.single { it.name == "B" }.hookShortcutCandidates()
            classes.getValue(ZHUYIN_PROCESSOR).wrapShortcutSelection()
        }
        assertCompletionBranches(classes.getValue("Lhch;").methods.single { it.name == "c" })
        for (call in listOf(RuntimeCallId.ZHUYIN_SHORTCUTS_RECORD_ENTRY,
            RuntimeCallId.ZHUYIN_SHORTCUTS_RECORD_ROW,
            RuntimeCallId.ZHUYIN_SHORTCUTS_BEGIN_IMPORT, RuntimeCallId.ZHUYIN_SHORTCUTS_FINISH_IMPORT,
            RuntimeCallId.ZHUYIN_SHORTCUTS_CANDIDATES, RuntimeCallId.ZHUYIN_SHORTCUTS_COMMIT)) {
            assertEquals(1, classes.values.flatMap { it.methods }.sumOf { method ->
                method.implementation?.instructions?.count {
                    it.isMethodReference(RuntimeAbiCatalog.abi(call).reference)
                } ?: 0
            })
        }
        val output = Files.createTempFile("zhuyin-shortcuts-", ".dex")
        try {
            DexPool.writeTo(output.toString(), ImmutableDexFile(Opcodes.getDefault(), classes.values))
            assertEquals(4, DexFileFactory.loadDexFile(output.toFile(), Opcodes.getDefault()).classes.size)
        } finally { Files.deleteIfExists(output) }
    }

    @Test
    fun `completion hook upgrades old insertion and intercepts every return branch`() {
        val method = ImmutableMethod("Lhch;", "c", emptyList(), "V", 1,
            null, null, MutableMethodImplementation(3)).toMutable().apply {
            addInstructions(0, """
                const/4 v0, 0x0
                if-eqz v0, :done
                goto :done
                :done
                return-void
            """.trimIndent())
        }
        val call = RuntimeCallId.ZHUYIN_SHORTCUTS_FINISH_IMPORT
        // Reproduce the old bug: branches target the original return, bypassing this insertion.
        method.addInstructions(3, RuntimeCallEmitter.invoke(call, "p0"))
        method.hookShortcutImport(call, true)
        method.hookShortcutImport(call, true)
        assertCompletionBranches(method)
    }

    private fun assertCompletionBranches(method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod) {
        val instructions = method.implementation!!.instructions
        val exit = instructions.indices.single { instructions[it].opcode == Opcode.RETURN_VOID }
        val reference = RuntimeAbiCatalog.abi(RuntimeCallId.ZHUYIN_SHORTCUTS_FINISH_IMPORT).reference
        assertEquals(true, instructions[exit - 1].isMethodReference(reference))
        for (branch in instructions.filterIsInstance<BuilderOffsetInstruction>()) {
            assertEquals(false, branch.target.location.index == exit)
        }
    }

    @Test
    fun `changes Chinese tokenizer once and preserves English tokenizer`() {
        val method = importer()
        method.applyZhuyinShortcutTokenizer()
        method.applyZhuyinShortcutTokenizer()
        val calls = method.implementation!!.instructions.filter {
            it.isMethodReference(stock) || it.isMethodReference(patched)
        }
        assertEquals(2, calls.size)
        assertEquals(true, calls[0].isMethodReference(patched))
        assertEquals(true, calls[1].isMethodReference(stock))
    }

    @Test
    fun `rejects unexpected register layout before modifying either branch`() {
        val method = importer(17)
        assertThrows(IllegalStateException::class.java) { method.applyZhuyinShortcutTokenizer() }
        assertEquals(0, method.implementation!!.instructions.count { it.isMethodReference(patched) })
    }

    private fun importer(registers: Int = 16) = ImmutableMethod(
        "Lfyx;", "a",
        listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "I")
            .map { ImmutableMethodParameter(it, null, null) },
        "Lcom/google/android/apps/inputmethod/libs/hmm/MutableDictionaryAccessor\$Entry;",
        1, null, null, MutableMethodImplementation(registers),
    ).toMutable().apply {
        addInstructions(0, """
            invoke-virtual {p2, p0}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;
            move-result-object p0
            invoke-static {p0}, $stock
            move-result-object v4
            invoke-virtual {p2, p0}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;
            move-result-object p0
            invoke-static {p0}, $stock
            move-result-object v4
            const/4 v0, 0x0
            return-object v0
        """.trimIndent())
    }
}
