package dev.jason.gboardpatches.patches.gboard.features.advancedvoice

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
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

internal const val S3_NETWORK_CHECK_OWNER = "Ltgq;"
private const val NETWORK_INFO = "Lrfn;"
private const val NETWORK_STATE = "Lrfm;"
private const val MODULE_LOCATOR = "Lqlv;"
private const val MODULE = "Lqlr;"
private const val STOCK_HELPER = "morpheS3NetworkStock"
private const val OVERRIDE_HELPER = "morpheS3AirplaneOverride"

internal const val S3_NETWORK_CHECK_FINGERPRINT =
    "75a342b3b13976a6813f60921da95862084f63c616b7ebf8454494138cbce193"

/**
 * Gboard's S3 network check answers "no network" in airplane mode even with Wi-Fi on. The stock
 * check still runs first; only a stock "no" with airplane mode on and a usable network becomes
 * "yes".
 */
internal val gboardS3AirplaneModePatch = bytecodePatch(
    description = "飛航模式但有 Wi-Fi 時讓 S3 語音辨識照常使用網路。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        installS3AirplaneMode()
    }
}

internal fun BytecodePatchContext.installS3AirplaneMode() {
    check(GboardVersionBindings.targetVersion == "18.5.4")
    listOf(GboardFieldTarget(NETWORK_INFO, "b", NETWORK_STATE), GboardFieldTarget(NETWORK_INFO, "c", "Z"))
        .forEach { target ->
            val field = target.resolve(this)
            check(field.accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.STATIC.value) == 0) {
                "Inaccessible network field: ${target.reference}"
            }
        }
    check(mutableClass(NETWORK_STATE).superclass == "Ljava/lang/Enum;") { "Network state is not an enum" }
    check(MODULE in mutableClass(NETWORK_INFO).interfaces) { "Network info is not a Gboard module" }
    val locator = GboardMethodTarget(MODULE_LOCATOR, "b", emptyList(), MODULE_LOCATOR).resolve(this)
    val lookup = GboardMethodTarget(MODULE_LOCATOR, "a", listOf("Ljava/lang/Class;"), MODULE).resolve(this)
    check(locator.accessFlags and AccessFlags.STATIC.value != 0 &&
        locator.accessFlags and AccessFlags.PRIVATE.value == 0 &&
        lookup.accessFlags and AccessFlags.STATIC.value == 0 &&
        lookup.accessFlags and AccessFlags.PRIVATE.value == 0) { "Unexpected module locator shape" }
    installS3AirplaneMode(mutableClass(S3_NETWORK_CHECK_OWNER))
}

internal fun installS3AirplaneMode(owner: MutableClass) {
    check(owner.type == S3_NETWORK_CHECK_OWNER)
    val networkCheck = GboardMethodTarget(S3_NETWORK_CHECK_OWNER, "c", listOf("Z"), "Z").resolve(owner)
    check(networkCheck.accessFlags and AccessFlags.STATIC.value != 0) { "S3 network check is not static" }
    check(networkCheck.gboardStructuralFingerprint() == S3_NETWORK_CHECK_FINGERPRINT) {
        "Unexpected 18.5.4 S3 network check shape: ${networkCheck.gboardStructuralFingerprint()}"
    }
    check(owner.methods.none { it.name == STOCK_HELPER || it.name == OVERRIDE_HELPER }) {
        "S3 airplane mode check is already installed"
    }
    val stock = ImmutableMethod(networkCheck.definingClass, STOCK_HELPER, networkCheck.parameters, networkCheck.returnType,
        AccessFlags.PRIVATE.value or AccessFlags.STATIC.value, networkCheck.annotations,
        networkCheck.hiddenApiRestrictions, networkCheck.implementation).toMutable()
    check(owner.methods.remove(networkCheck) && owner.methods.add(stock))
    owner.addHelperMethodIfMissing(OVERRIDE_HELPER, listOf("Z"), "Z",
        AccessFlags.PRIVATE.value or AccessFlags.STATIC.value, 3, s3AirplaneOverrideBody())
    owner.addHelperMethodIfMissing("c", listOf("Z"), "Z", networkCheck.accessFlags, 2, s3NetworkCheckBody())
}

internal fun s3NetworkCheckBody() = """
    invoke-static {p0}, $S3_NETWORK_CHECK_OWNER->$STOCK_HELPER(Z)Z
    move-result v0
    invoke-static {v0}, $S3_NETWORK_CHECK_OWNER->$OVERRIDE_HELPER(Z)Z
    move-result v0
    return v0
""".trimIndent()

internal fun s3AirplaneOverrideBody() = """
    if-nez p0, :stock
    :start
    invoke-static {}, $MODULE_LOCATOR->b()$MODULE_LOCATOR
    move-result-object v0
    if-eqz v0, :stock
    const-class v1, $NETWORK_INFO
    invoke-virtual {v0, v1}, $MODULE_LOCATOR->a(Ljava/lang/Class;)$MODULE
    move-result-object v0
    instance-of v1, v0, $NETWORK_INFO
    if-eqz v1, :stock
    check-cast v0, $NETWORK_INFO
    iget-object v1, v0, $NETWORK_INFO->b:$NETWORK_STATE
    if-eqz v1, :stock
    invoke-virtual {v1}, Ljava/lang/Enum;->name()Ljava/lang/String;
    move-result-object v1
    iget-boolean v0, v0, $NETWORK_INFO->c:Z
    ${RuntimeCallEmitter.invoke(RuntimeCallId.S3_AIRPLANE_MODE_POLICY_SHOULD_OVERRIDE, "p0, v0, v1")}
    move-result v0
    return v0
    :end
    .catch Ljava/lang/Throwable; {:start .. :end} :failed
    :stock
    return p0
    :failed
    move-exception v0
    return p0
""".trimIndent()
