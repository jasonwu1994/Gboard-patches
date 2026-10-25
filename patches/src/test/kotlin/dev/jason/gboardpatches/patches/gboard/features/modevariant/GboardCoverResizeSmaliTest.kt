/* Cover Resize synthetic Smali ABI tests only; no proprietary Gboard bytecode bundled. */
package dev.jason.gboardpatches.patches.gboard.features.modevariant

import app.morphe.patcher.util.smali.InlineSmaliCompiler
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import org.junit.Assert.assertEquals
import org.junit.Test

class GboardCoverResizeSmaliTest {
    @Test
    fun readModeParameterSupportsHighRegisters() {
        val call = RuntimeCallEmitter.invoke(
            RuntimeCallId.MODE_VARIANT_BEGIN_READ, "p2 .. p2"
        )
        for (count in listOf(5, 20, 64, 256)) {
            val compiled = InlineSmaliCompiler.compile(
                instructions = call,
                parameters = "Landroid/content/Context;Lqhy;ILnmm;Z",
                registers = count,
                forStaticMethod = true
            )
            assertEquals(1, compiled.size)
            val invoke = compiled.single() as BuilderInstruction3rc
            assertEquals(Opcode.INVOKE_STATIC_RANGE, invoke.opcode)
            assertEquals(count - 3, invoke.startRegister)
            assertEquals(1, invoke.registerCount)
        }
    }

    @Test
    fun familyAndReturnRoutingUsesOriginalRegisterWithoutLocals() {
        val family = RuntimeCallEmitter.invoke(
            RuntimeCallId.MODE_VARIANT_FAMILY, "p0 .. p0"
        )
        val start = InlineSmaliCompiler.compile(
            instructions = family,
            parameters = "Lnmm;ZZ",
            registers = 6,
            forStaticMethod = true
        )
        assertEquals(Opcode.INVOKE_STATIC_RANGE, start.single().opcode)

        for (reg in listOf(0, 15, 32, 200)) {
            val code = RuntimeCallEmitter.invoke(
                RuntimeCallId.MODE_VARIANT_ROUTE_RESULT,
                "v" + reg + " .. v" + reg
            ) + "\nmove-result-object v" + reg +
                "\nreturn-object v" + reg
            val compiled = InlineSmaliCompiler.compile(
                instructions = code,
                parameters = "Lnmm;ZZ",
                registers = 256,
                forStaticMethod = true
            )
            assertEquals(
                listOf(Opcode.INVOKE_STATIC_RANGE,
                    Opcode.MOVE_RESULT_OBJECT, Opcode.RETURN_OBJECT),
                compiled.map { it.opcode }
            )
        }
    }

    @Test
    fun saveAndImeBindingCompileWithoutNewLocalRegisters() {
        val save = InlineSmaliCompiler.compile(
            instructions = RuntimeCallEmitter.invoke(
                RuntimeCallId.MODE_VARIANT_BEGIN_SAVE, ""
            ),
            parameters = "",
            registers = 1,
            forStaticMethod = false
        )
        assertEquals(Opcode.INVOKE_STATIC, save.single().opcode)
        for (count in listOf(3, 19, 255)) {
            val bind = InlineSmaliCompiler.compile(
                instructions = RuntimeCallEmitter.invoke(
                    RuntimeCallId.MODE_VARIANT_BIND_IME, "p0 .. p0"
                ),
                parameters = "Landroid/view/inputmethod/EditorInfo;Z",
                registers = count,
                forStaticMethod = false
            )
            assertEquals(Opcode.INVOKE_STATIC_RANGE, bind.single().opcode)
            val call = bind.single() as BuilderInstruction3rc
            assertEquals(count - 3, call.startRegister)
        }
    }

    @Test
    fun originalSaveKeyboardModeReadIsObservedWithoutMutatingIt() {
        val call = RuntimeCallEmitter.invoke(
            RuntimeCallId.MODE_VARIANT_SAVE_KEYBOARD_MODE, "v32 .. v32"
        )
        val code = call + "\nconst/4 v0, 0x1"
        val instructions = InlineSmaliCompiler.compile(
            instructions = code,
            parameters = "",
            registers = 64,
            forStaticMethod = false
        )
        assertEquals(
            listOf(Opcode.INVOKE_STATIC_RANGE, Opcode.CONST_4),
            instructions.map { it.opcode }
        )
    }
}
